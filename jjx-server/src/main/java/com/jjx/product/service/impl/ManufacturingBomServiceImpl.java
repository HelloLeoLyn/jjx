package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.domain.dto.ManufacturingBomSaveDTO;
import com.jjx.product.domain.dto.ManufacturingSchemeChoiceDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.vo.EngineeringBomEditVO;
import com.jjx.product.domain.vo.EngineeringBomVO;
import com.jjx.product.domain.vo.ManufacturingBomVO;
import com.jjx.product.domain.vo.ManufacturingSchemeVO;
import com.jjx.product.enums.ManufacturingOutputType;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.product.service.ManufacturingBomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 制造 BOM 方案后端能力（dev-20261111-004）。
 * 复用 engineering_bom/engineering_bom_item 唯一 BOM 主表；不新建方案表；
 * 保存仍走 IEngineeringBomService 的同一事务路径，避免出现第二套保存规则。
 */
@Service
@RequiredArgsConstructor
public class ManufacturingBomServiceImpl implements ManufacturingBomService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final IEngineeringBomService bomService;
    private final EngineeringBomMapper bomMapper;
    private final ProductMapper productMapper;

    @Override
    public List<ManufacturingSchemeVO> options(String outputType, Long outputId, Long productId) {
        ManufacturingOutputType type = normalizeType(outputType);
        if (outputId == null) {
            throw new BusinessException("产出对象不能为空");
        }
        LambdaQueryWrapper<EngineeringBom> wrapper = new LambdaQueryWrapper<EngineeringBom>()
                .eq(EngineeringBom::getOutputType, type.name())
                .eq(EngineeringBom::getOutputId, outputId)
                .orderByDesc(EngineeringBom::getCreateTime)
                .orderByDesc(EngineeringBom::getBomId);
        if (type == ManufacturingOutputType.PRODUCT) {
            // 迁移前/历史行 output 列为空时按 PRODUCT/product_id 兼容。
            wrapper = new LambdaQueryWrapper<EngineeringBom>()
                    .and(w -> w.eq(EngineeringBom::getOutputType, type.name())
                            .eq(EngineeringBom::getOutputId, outputId)
                            .or()
                            .isNull(EngineeringBom::getOutputType)
                            .eq(EngineeringBom::getProductId, outputId))
                    .orderByDesc(EngineeringBom::getCreateTime)
                    .orderByDesc(EngineeringBom::getBomId);
        }
        Map<Long, Boolean> primary = productId == null ? Map.of() : primaryMap(productId);
        List<ManufacturingSchemeVO> out = new ArrayList<>();
        for (EngineeringBom bom : bomMapper.selectList(wrapper)) {
            ManufacturingSchemeVO vo = schemeVO(bom);
            vo.setIsPrimary(productId == null ? null : primary.getOrDefault(bom.getBomId(), false));
            out.add(vo);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ManufacturingBomVO save(ManufacturingBomSaveDTO dto) {
        EngineeringBomDTO payload = toBomDTO(dto);
        Long bomId = bomService.createBomReturnId(payload);
        if (bomId == null) {
            throw new BusinessException("制造BOM保存失败");
        }
        return detail(bomId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ManufacturingBomVO update(Long bomId, ManufacturingBomSaveDTO dto) {
        EngineeringBom existing = bomMapper.selectById(bomId);
        if (existing == null) {
            throw new BusinessException("制造BOM不存在（bomId=" + bomId + "）");
        }
        ManufacturingOutputType type = normalizeType(dto.getOutputType());
        Long existingId = existing.getOutputId() != null ? existing.getOutputId() : existing.getProductId();
        if (type != ManufacturingOutputType.normalize(existing.getOutputType())
                || !Objects.equals(existingId, dto.getOutputId())) {
            throw new BusinessException("产出身份不可修改（bomId=" + bomId + "）");
        }
        EngineeringBomDTO payload = toBomDTO(dto);
        payload.setBomId(bomId);
        EngineeringBomEditVO edit = bomService.updateBomWithDetail(payload);
        if (!edit.isSuccess()) {
            throw new BusinessException("制造BOM保存失败");
        }
        return detail(bomId);
    }

    @Override
    public ManufacturingSchemeVO adopt(Long bomId) {
        EngineeringBom bom = bomMapper.selectById(bomId);
        if (bom == null) {
            throw new BusinessException("制造方案不存在（bomId=" + bomId + "）");
        }
        ManufacturingSchemeVO vo = schemeVO(bom);
        // token 只证明“这个已保存方案被选中”；父 BOM 保存时会重新校验身份/存在/批准资格。
        vo.setSelectionToken("MS-" + bom.getBomId() + "-" + UUID.randomUUID());
        return vo;
    }

    @Override
    public List<ManufacturingSchemeChoiceDTO> getSchemeChoices(Long bomId) {
        EngineeringBom bom = bomMapper.selectById(bomId);
        if (bom == null) {
            throw new BusinessException("BOM不存在（bomId=" + bomId + "）");
        }
        if (ManufacturingOutputType.normalize(bom.getOutputType()) != ManufacturingOutputType.PRODUCT
                || bom.getProductId() == null) {
            return List.of();
        }
        Product product = productMapper.selectById(bom.getProductId());
        return product == null ? List.of() : readChoices(product.getMaterialSchemeJson());
    }

    private EngineeringBomDTO toBomDTO(ManufacturingBomSaveDTO dto) {
        ManufacturingOutputType type = normalizeType(dto.getOutputType());
        if (dto.getOutputId() == null) {
            throw new BusinessException("产出对象不能为空");
        }
        if (type == ManufacturingOutputType.PRODUCT) {
            if (productMapper.selectById(dto.getOutputId()) == null) {
                throw new BusinessException("产出产品不存在（product_id=" + dto.getOutputId() + "）");
            }
        }
        EngineeringBomDTO payload = new EngineeringBomDTO();
        payload.setBomId(dto.getBomId());
        payload.setBomCode(dto.getBomCode());
        payload.setBomName(dto.getBomName());
        payload.setBomVersion(dto.getBomVersion());
        payload.setOutputType(type.name());
        payload.setOutputId(dto.getOutputId());
        payload.setProductId(type == ManufacturingOutputType.PRODUCT ? dto.getOutputId() : null);
        payload.setEffectiveDate(dto.getEffectiveDate());
        payload.setExpiryDate(dto.getExpiryDate());
        payload.setRemark(dto.getRemark());
        payload.setItems(dto.getItems());
        return payload;
    }

    private ManufacturingBomVO detail(Long bomId) {
        EngineeringBom bom = bomMapper.selectById(bomId);
        if (bom == null) {
            throw new BusinessException("制造BOM不存在（bomId=" + bomId + "）");
        }
        ManufacturingBomVO vo = new ManufacturingBomVO();
        vo.setBomId(bom.getBomId());
        vo.setBomCode(bom.getBomCode());
        vo.setBomName(bom.getBomName());
        vo.setBomVersion(bom.getBomVersion());
        vo.setOutputType(ManufacturingOutputType.normalize(bom.getOutputType()).name());
        vo.setOutputId(bom.getOutputId() != null ? bom.getOutputId() : bom.getProductId());
        vo.setProductId(bom.getProductId());
        vo.setApproveStatus(bom.getApproveStatus());
        vo.setIsCurrent(bom.getIsCurrent());
        vo.setEffectiveDate(bom.getEffectiveDate());
        vo.setExpiryDate(bom.getExpiryDate());
        vo.setRemark(bom.getRemark());
        vo.setCreateTime(bom.getCreateTime());
        vo.setUpdateTime(bom.getUpdateTime());
        vo.setItems(bomService.getBomItems(bomId));
        return vo;
    }

    private ManufacturingSchemeVO schemeVO(EngineeringBom bom) {
        ManufacturingSchemeVO vo = new ManufacturingSchemeVO();
        vo.setBomId(bom.getBomId());
        vo.setBomCode(bom.getBomCode());
        vo.setBomName(bom.getBomName());
        vo.setBomVersion(bom.getBomVersion());
        vo.setOutputType(ManufacturingOutputType.normalize(bom.getOutputType()).name());
        vo.setOutputId(bom.getOutputId() != null ? bom.getOutputId() : bom.getProductId());
        vo.setApproveStatus(bom.getApproveStatus());
        vo.setIsCurrent(bom.getIsCurrent());
        vo.setEffectiveDate(bom.getEffectiveDate());
        vo.setExpiryDate(bom.getExpiryDate());
        vo.setRemark(bom.getRemark());
        vo.setItemCount(bomService.getBomItems(bom.getBomId()).size());
        return vo;
    }

    private Map<Long, Boolean> primaryMap(Long productId) {
        Product product = productMapper.selectById(productId);
        Map<Long, Boolean> out = new HashMap<>();
        if (product == null) return out;
        for (ManufacturingSchemeChoiceDTO choice : readChoices(product.getMaterialSchemeJson())) {
            if (Boolean.TRUE.equals(choice.getIsPrimary())) out.put(choice.getBomId(), true);
        }
        return out;
    }

    private List<ManufacturingSchemeChoiceDTO> readChoices(String raw) {
        List<ManufacturingSchemeChoiceDTO> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        try {
            JsonNode root = JSON.readTree(raw);
            if (!(root instanceof ObjectNode)) return out;
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (!entry.getValue().hasNonNull("bomId")) continue;
                ManufacturingSchemeChoiceDTO dto = new ManufacturingSchemeChoiceDTO();
                dto.setMaterialId(Long.parseLong(entry.getKey()));
                dto.setBomId(entry.getValue().path("bomId").asLong());
                dto.setIsPrimary(entry.getValue().path("isPrimary").asBoolean(false));
                EngineeringBom child = bomMapper.selectById(dto.getBomId());
                if (child != null) {
                    dto.setBomCode(child.getBomCode());
                    dto.setBomVersion(child.getBomVersion());
                    dto.setApproveStatus(child.getApproveStatus());
                    if (!Objects.equals(child.getApproveStatus(), 3)) {
                        dto.setWarning("SUB_BOM_NOT_APPROVED：所选方案未批准，父 BOM 提交/批准前需先批准");
                    }
                } else {
                    dto.setWarning("SUB_BOM_NOT_FOUND：所选方案已不存在");
                }
                out.add(dto);
            }
        } catch (Exception ex) {
            throw new BusinessException("产品主方案 JSON 解析失败: " + ex.getMessage());
        }
        return out;
    }

    private ManufacturingOutputType normalizeType(String value) {
        try {
            return ManufacturingOutputType.normalize(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }
}
