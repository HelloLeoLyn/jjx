package com.jjx.inventory.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.enums.StatusEnum;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.converter.MaterialConverter;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.dto.imports.MaterialImportDTO;
import com.jjx.inventory.dto.query.MaterialCheckDTO;
import com.jjx.inventory.dto.query.MaterialQueryDTO;
import com.jjx.inventory.dto.vo.MaterialVO;
import com.jjx.inventory.enums.ProcessGroup;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.service.InventoryMaterialService;
import com.jjx.purchase.domain.vo.PurchaseSupplierVO;
import com.jjx.purchase.service.IPurchaseSupplierService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 物料主数据服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryMaterialServiceImpl extends ServiceImpl<InventoryMaterialMapper, InventoryMaterial>
        implements InventoryMaterialService {

    private final InventoryMaterialMapper materialMapper;
    private final InventoryStockMapper stockMapper;
    private final RedisSequenceService redisSequenceService;
    private final MaterialConverter materialConverter;
    private final IPurchaseSupplierService purchaseSupplierService;

    @Override
    public PageResult<MaterialVO> pageQuery(MaterialQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryMaterial> wrapper = buildQueryWrapper(queryDTO);
        Page<InventoryMaterial> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        materialMapper.selectPage(page, wrapper);
        List<InventoryMaterial> records = page.getRecords();
        List<MaterialVO> voList = materialConverter.toVOList(records);
        return PageResult.build(voList, page.getTotal());
    }

    private static @NonNull LambdaQueryWrapper<InventoryMaterial> buildQueryWrapper(MaterialQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getMaterialCode() != null && !queryDTO.getMaterialCode().isEmpty()) {
            wrapper.like(InventoryMaterial::getMaterialCode, queryDTO.getMaterialCode());
        }
        if (queryDTO.getMaterialName() != null && !queryDTO.getMaterialName().isEmpty()) {
            wrapper.like(InventoryMaterial::getMaterialName, queryDTO.getMaterialName());
        }
        if (queryDTO.getSpecification() != null && !queryDTO.getSpecification().isEmpty()) {
            wrapper.eq(InventoryMaterial::getSpecification, queryDTO.getSpecification());
        }
        if (queryDTO.getSupplierId() != null) {
            wrapper.eq(InventoryMaterial::getSupplierId, queryDTO.getSupplierId());
        }
        if (queryDTO.getCategoryId() != null) {
            wrapper.eq(InventoryMaterial::getCategoryId, queryDTO.getCategoryId());
        }
        if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
            wrapper.eq(InventoryMaterial::getStatus, queryDTO.getStatus());
        }
        wrapper.orderByDesc(InventoryMaterial::getCreateTime);
        return wrapper;
    }

    @Override
    public MaterialVO getDetailById(Long id) {
        InventoryMaterial material = materialMapper.selectById(id);
        return materialConverter.toVO(material);
    }

    @Override
    public InventoryMaterial getByCode(String materialCode) {
        return materialMapper.selectByCode(materialCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean create(InventoryMaterial material) {
        // 检查编码是否已存在
        if (existsByCode(material.getMaterialCode())) {
            log.error("物料编码已存在: {}", material.getMaterialCode());
            return false;
        }

        return materialMapper.insert(material) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(InventoryMaterial material) {
        InventoryMaterial existing = materialMapper.selectById(material.getMaterialId());
        if (existing == null) {
            log.error("物料不存在: {}", material.getMaterialId());
            return false;
        }

        // 检查编码是否重复（如果修改了编码）
        if (!existing.getMaterialCode().equals(material.getMaterialCode())) {
            if (existsByCode(material.getMaterialCode())) {
                log.error("物料编码已存在: {}", material.getMaterialCode());
                return false;
            }
        }

        return materialMapper.updateById(material) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWithCheck(Long id) {
        InventoryMaterial material = materialMapper.selectById(id);
        if (material == null) {
            log.error("物料不存在: {}", id);
            return false;
        }

        // 检查库存汇总
        InventoryStock stock = new InventoryStock();
        stock.setMaterialId(id);
        Long stockCount = stockMapper
                .selectCount(new LambdaQueryWrapper<InventoryStock>().eq(InventoryStock::getMaterialId, id));
        if (stockCount != null && stockCount > 0) {
            log.error("物料已被库存引用，无法删除: materialId={}", id);
            throw new RuntimeException("物料已被库存引用，无法删除");
        }

        // TODO: 检查是否被采购订单、生产订单、销售订单引用
        // 后续可扩展检查 purchase_order_item、production_order_material 等

        return materialMapper.deleteById(id) > 0;
    }

    @Override
    public List<Map<String, Object>> getOptions(String keyword) {
        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<InventoryMaterial>()
                .eq(InventoryMaterial::getStatus, 0);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(InventoryMaterial::getMaterialCode, keyword)
                    .or().like(InventoryMaterial::getMaterialName, keyword));
        }
        List<InventoryMaterial> materials = materialMapper.selectList(wrapper
                .orderByAsc(InventoryMaterial::getMaterialCode)
                .last("LIMIT 100"));

        List<Map<String, Object>> options = new ArrayList<>();
        for (InventoryMaterial material : materials) {
            Map<String, Object> option = new HashMap<>();
            option.put("value", material.getMaterialId());
            option.put("label", material.getMaterialCode() + " - " + material.getMaterialName());
            option.put("materialCode", material.getMaterialCode());
            option.put("materialName", material.getMaterialName());
            option.put("unit", material.getUnit());
            option.put("specification", material.getSpecification());
            options.add(option);
        }

        return options;
    }

    @Override
    public boolean existsByCode(String materialCode) {
        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryMaterial::getMaterialCode, materialCode);
        Long count = materialMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchUpdateStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        LambdaUpdateWrapper<InventoryMaterial> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(InventoryMaterial::getStatus, status)
                .in(InventoryMaterial::getMaterialId, ids);
        return materialMapper.update(updateWrapper) > 0;
    }

    @Override
    public PageResult<MaterialVO> search(MaterialQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(InventoryMaterial::getMaterialCode, queryDTO.getMaterialCode());
        wrapper.or().like(StringUtils.isNotBlank(queryDTO.getMaterialCode()), InventoryMaterial::getMaterialName,
                queryDTO.getMaterialName());
        IPage<InventoryMaterial> page = new Page<InventoryMaterial>().setSize(queryDTO.getPageSize())
                .setCurrent(queryDTO.getPageNum());
        materialMapper.selectPage(page, wrapper);
        List<MaterialVO> voList = materialConverter.toVOList(page.getRecords());
        return PageResult.build(voList, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importMaterial(List<MaterialImportDTO> importList, String operName) {
        if (importList == null || importList.isEmpty()) {
            return "导入数据为空";
        }

        int successCount = 0;
        int skipCount = 0;
        List<String> errors = new ArrayList<>();
        String dateKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        for (int i = 0; i < importList.size(); i++) {
            MaterialImportDTO dto = importList.get(i);
            try {
                PurchaseSupplierVO purchaseSupplierVO = purchaseSupplierService
                        .selectSupplierByName(dto.getSupplierName());
                if (purchaseSupplierVO == null) {
                    errors.add("第" + (i + 1) + "条数据导入失败: " + dto.getMaterialName() + " - 找不到对应供应商");
                    continue;
                }
                // 检查是否已存在（按物料名称+规格+供应商去重）
                LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(InventoryMaterial::getMaterialName, dto.getMaterialName());
                if (dto.getSpecification() != null && !dto.getSpecification().isEmpty()) {
                    wrapper.eq(InventoryMaterial::getSpecification, dto.getSpecification());
                }
                if (dto.getSupplierName() != null && !dto.getSupplierName().isEmpty()) {
                    wrapper.eq(InventoryMaterial::getSupplierName, dto.getSupplierName());
                }

                Long existCount = materialMapper.selectCount(wrapper);
                if (existCount != null && existCount > 0) {
                    skipCount++;
                    continue;
                }

                // 生成物料编码
                String materialCode = generateMaterialCode(dateKey);

                // 创建物料实体
                InventoryMaterial material = new InventoryMaterial();
                material.setMaterialCode(materialCode);
                material.setMaterialName(dto.getMaterialName());
                material.setMaterialNameEn(dto.getMaterialNameEn());
                material.setMaterialType(dto.getMaterialType());
                material.setSpecification(dto.getSpecification());
                material.setUnit(dto.getUnit());
                material.setSupplierId(purchaseSupplierVO.getSupplierId());
                material.setSupplierName(dto.getSupplierName());
                material.setRemark(dto.getRemark());
                material.setStatus(StatusEnum.NORMAL.getCode());
                material.setSafeStock(BigDecimal.ZERO);
                material.setMaxStock(BigDecimal.ZERO);
                material.setReorderPoint(BigDecimal.ZERO);
                material.setBatchControl(false);
                material.setExpiryAlertDays(30);
                material.setProcessGroup(getProcessGroup(dto));
                materialMapper.insert(material);
                successCount++;

            } catch (Exception e) {
                log.error("导入第{}条数据失败: {}", i + 1, dto.getMaterialName(), e);
                errors.add("第" + (i + 1) + "条数据导入失败: " + dto.getMaterialName() + " - " + e.getMessage());
            }
        }

        StringBuilder result = new StringBuilder();
        result.append("导入完成：成功 ").append(successCount).append(" 条");
        if (skipCount > 0) {
            result.append("，跳过重复 ").append(skipCount).append(" 条");
        }
        if (!errors.isEmpty()) {
            result.append("，失败 ").append(errors.size()).append(" 条");
            log.warn("导入失败详情: {}", String.join("; ", errors));
        }

        return result.toString();
    }

    @Override
    public List<MaterialVO> selectList(MaterialQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryMaterial> queryWrapper = buildQueryWrapper(queryDTO);
        List<InventoryMaterial> inventoryMaterials = materialMapper.selectList(queryWrapper);
        return materialConverter.toVOList(inventoryMaterials);
    }

    @Override
    public String generateCode() {
        String dateKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return generateMaterialCode(dateKey);
    }

    @Override
    public MaterialVO checkMaterial(MaterialCheckDTO checkDTO) {
        if (checkDTO.getMaterialName() == null || checkDTO.getMaterialName().isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryMaterial::getMaterialName, checkDTO.getMaterialName());

        if (checkDTO.getSpecification() != null && !checkDTO.getSpecification().isEmpty()) {
            wrapper.eq(InventoryMaterial::getSpecification, checkDTO.getSpecification());
        }
        if (checkDTO.getSupplierName() != null && !checkDTO.getSupplierName().isEmpty()) {
            wrapper.eq(InventoryMaterial::getSupplierName, checkDTO.getSupplierName());
        }

        InventoryMaterial material = materialMapper.selectOne(wrapper, false);
        if (material == null) {
            return null;
        }
        return materialConverter.toVO(material);
    }

    private static String getProcessGroup(MaterialImportDTO dto) {
        ProcessGroup processGroup = ProcessGroup.fromCode(dto.getProcessGroup());
        if (processGroup == null) {
            processGroup = ProcessGroup.fromName(dto.getProcessGroup());
            return processGroup.getCode();
        }
        return null;
    }

    /**
     * 生成物料编码
     * 格式：MTR + 日期(yyyyMMdd) + 序列号(4位)
     * 例如：MTR202604280001
     */
    private String generateMaterialCode(String dateKey) {
        Long sequence = redisSequenceService.getNextSequence("material:" + dateKey);
        return "MTR" + dateKey + String.format("%04d", sequence);
    }
}
