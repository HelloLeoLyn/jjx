package com.jjx.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.purchase.converter.PurchaseConverter;
import com.jjx.purchase.converter.SupplierConverter;
import com.jjx.purchase.domain.dto.PurchaseSupplierDTO;
import com.jjx.purchase.domain.dto.SupplierEvaluationDTO;
import com.jjx.purchase.domain.dto.SupplierImportDTO;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.entity.PurchaseSupplier;
import com.jjx.purchase.domain.vo.PurchaseSupplierQueryVO;
import com.jjx.purchase.domain.vo.PurchaseSupplierVO;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.mapper.PurchaseSupplierMapper;
import com.jjx.purchase.service.IPurchaseSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.jjx.system.annotation.Event;
import com.jjx.system.domain.entity.SysTag;
import com.jjx.system.service.ISysTagService;
import com.jjx.system.utils.SecurityUtils;

/**
 * 供应商服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseSupplierServiceImpl extends ServiceImpl<PurchaseSupplierMapper, PurchaseSupplier> implements IPurchaseSupplierService {

    private final PurchaseSupplierMapper supplierMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseConverter purchaseConverter;
    private final SupplierConverter supplierConverter;
    private final ISysTagService tagService;
    private final RedisSequenceService redisSequenceService;

    /** 供应商标签业务类型（系统标签 dev-20260911-007） */
    public static final String TAG_BIZ_TYPE = "purchase_supplier";

    /** 供应商标签分组（字典 sys_tag_group） */
    public static final String TAG_GROUP_SUPPLIER_GOODS = "supplier_goods";
    @Override
    public com.jjx.common.core.page.PageResult<PurchaseSupplierVO> selectSupplierList(PurchaseSupplierQueryVO queryVO) {
        LambdaQueryWrapper<PurchaseSupplier> wrapper = Wrappers.lambdaQuery();

        // 构建查询条件
        if (StringUtils.isNotEmpty(queryVO.getSupplierCode())) {
            wrapper.like(PurchaseSupplier::getSupplierCode, queryVO.getSupplierCode());
        }
        if (StringUtils.isNotEmpty(queryVO.getSupplierName())) {
            wrapper.like(PurchaseSupplier::getSupplierName, queryVO.getSupplierName());
        }
        if (StringUtils.isNotEmpty(queryVO.getSupplierType())) {
            wrapper.eq(PurchaseSupplier::getSupplierType, queryVO.getSupplierType());
        }
        if (queryVO.getStatus() != null) {
            wrapper.eq(PurchaseSupplier::getStatus, queryVO.getStatus());
        }
        if (StringUtils.isNotEmpty(queryVO.getContactPerson())) {
            wrapper.like(PurchaseSupplier::getContactPerson, queryVO.getContactPerson());
        }
        if (StringUtils.isNotEmpty(queryVO.getPhone())) {
            wrapper.like(PurchaseSupplier::getPhone, queryVO.getPhone());
        }

        // 按标签筛选（dev-20260911-007 单标签；dev-20260912-004 支持多标签 + 与/或）
        List<Long> queryTagIds = queryVO.getTagIds();
        if ((queryTagIds == null || queryTagIds.isEmpty()) && queryVO.getTagId() != null) {
            queryTagIds = List.of(queryVO.getTagId());
        }
        if (queryTagIds != null && !queryTagIds.isEmpty()) {
            boolean matchAll = !"OR".equalsIgnoreCase(queryVO.getTagMatchMode());
            List<Long> bizIds = tagService.getBizIdsByTagIds(TAG_BIZ_TYPE, queryTagIds, matchAll);
            if (bizIds.isEmpty()) {
                return com.jjx.common.core.page.PageResult.build(new java.util.ArrayList<>(), 0L);
            }
            wrapper.in(PurchaseSupplier::getSupplierId, bizIds);
        }

        // 排序
        wrapper.orderByDesc(PurchaseSupplier::getCreateTime).orderByDesc(PurchaseSupplier::getSupplierId);

        // DEV-696：分页（pageNum/pageSize 为空时退化为全量，兼容下拉框等数组调用方）
        int pageNum = queryVO.getPageNum() != null ? queryVO.getPageNum() : 1;
        int pageSize = queryVO.getPageSize() != null ? queryVO.getPageSize() : Integer.MAX_VALUE;
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PurchaseSupplier> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.core.metadata.IPage<PurchaseSupplier> pageResult = supplierMapper.selectPage(page, wrapper);

        List<PurchaseSupplierVO> voList = supplierConverter.toVOList(pageResult.getRecords());
        fillTags(voList);
        return com.jjx.common.core.page.PageResult.of(pageResult, voList);
    }

    /** 填充标签（dev-20260911-007） */
    private void fillTags(List<PurchaseSupplierVO> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        for (PurchaseSupplierVO vo : voList) {
            if (vo.getSupplierId() == null) {
                continue;
            }
            List<SysTag> tags = tagService.getBizTags(TAG_BIZ_TYPE, vo.getSupplierId());
            vo.setTagIds(tags.stream().map(SysTag::getTagId).collect(java.util.stream.Collectors.toList()));
            vo.setTagNames(tags.stream().map(SysTag::getTagName).collect(java.util.stream.Collectors.toList()));
        }
    }

    @Override
    public PurchaseSupplierVO selectSupplierById(Long supplierId) {
        PurchaseSupplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new BusinessException("供应商不存在");
        }
        PurchaseSupplierVO vo = supplierConverter.toVO(supplier);
        fillTags(java.util.Collections.singletonList(vo));
        return vo;
    }

    @Override
    public PurchaseSupplierVO selectSupplierByName(String supplierName) {
        LambdaQueryWrapper<PurchaseSupplier> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(PurchaseSupplier::getSupplierName,supplierName);
        PurchaseSupplier purchaseSupplier = supplierMapper.selectOne(wrapper);
        return supplierConverter.toVO(purchaseSupplier);
    }

    /** 供应商编码前缀（dev-20260911-005） */
    private static final String SUPPLIER_CODE_PREFIX = "SUP";

    /** 供应商编码流水位数（dev-20260911-005） */
    private static final int SUPPLIER_CODE_DIGITS = 5;

    @Override
    public String generateSupplierCode() {
        return redisSequenceService.generateBusinessNumberByType(
                "supplier", SUPPLIER_CODE_PREFIX, "", SUPPLIER_CODE_DIGITS);
    }

    @Event(value = "purchase.supplier.created", bizId = "#supplierDTO", bizType = "'purchase'")
    @Transactional(rollbackFor = Exception.class)
    public int insertSupplier(PurchaseSupplierDTO supplierDTO) {
        // 供应商编码留空时由系统生成（dev-20260911-005）
        if (StringUtils.isBlank(supplierDTO.getSupplierCode())) {
            supplierDTO.setSupplierCode(generateSupplierCode());
            log.info("供应商编码留空，自动生成：{}", supplierDTO.getSupplierCode());
        }

        // 检查供应商编码是否唯一
        if (checkSupplierCodeUnique(supplierDTO.getSupplierCode())) {
            throw new BusinessException("供应商编码已存在");
        }

        // 检查供应商名称是否唯一
        if (checkSupplierNameUnique(supplierDTO.getSupplierName())) {
            throw new BusinessException("供应商名称已存在");
        }

        // 验证必填字段
        if (StringUtils.isEmpty(supplierDTO.getSupplierName())) {
            throw new BusinessException("供应商名称不能为空");
        }
        if (StringUtils.isBlank(supplierDTO.getSupplierType())) {
            throw new BusinessException("供应商类型不能为空");
        }
        if (!com.jjx.purchase.domain.enums.SupplierTypeEnum.isValid(supplierDTO.getSupplierType())) {
            throw new BusinessException("供应商类型不合法");
        }

        // 转换实体
        PurchaseSupplier supplier = supplierConverter.toEntity(supplierDTO);

        // 设置默认值
        if (supplier.getStatus() == null) {
            supplier.setStatus(StatusEnum.NORMAL.getCode()); // 默认正常状态
        }
        if (supplier.getEvaluationScore() == null) {
            supplier.setEvaluationScore(BigDecimal.ZERO);
        }
        if (supplier.getQualityScore() == null) {
            supplier.setQualityScore(BigDecimal.ZERO);
        }
        if (supplier.getDeliveryScore() == null) {
            supplier.setDeliveryScore(BigDecimal.ZERO);
        }
        if (supplier.getPriceScore() == null) {
            supplier.setPriceScore(BigDecimal.ZERO);
        }

        // 保存供应商（编码并发冲突时重新取号重试，dev-20260911-005）
        int result = 0;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                result = supplierMapper.insert(supplier);
                break;
            } catch (DuplicateKeyException e) {
                if (attempt == 4) {
                    throw new BusinessException("供应商编码生成冲突，请重试");
                }
                supplier.setSupplierCode(generateSupplierCode());
                log.warn("供应商编码冲突，重新取号：{}", supplier.getSupplierCode());
            }
        }
        if (result <= 0) {
            throw new BusinessException("保存供应商失败");
        }

        // 标签（dev-20260911-007）
        if (supplierDTO.getTagIds() != null) {
            tagService.setBizTags(TAG_BIZ_TYPE, supplier.getSupplierId(), supplierDTO.getTagIds(),
                    SecurityUtils.getUsername());
        }

        return result;
    }

    @Override
    @Event(value = "purchase.supplier.updated", bizId = "#supplierDTO", bizType = "'purchase'")
    @Transactional(rollbackFor = Exception.class)
    public int updateSupplier(PurchaseSupplierDTO supplierDTO) {
        if (supplierDTO.getSupplierId() == null) {
            throw new BusinessException("供应商ID不能为空");
        }

        // 检查供应商是否存在
        PurchaseSupplier existingSupplier = supplierMapper.selectById(supplierDTO.getSupplierId());
        if (existingSupplier == null) {
            throw new BusinessException("供应商不存在");
        }

        // 检查供应商编码是否唯一（排除自身）
        if (StringUtils.isNotEmpty(supplierDTO.getSupplierCode()) &&
            !existingSupplier.getSupplierCode().equals(supplierDTO.getSupplierCode())) {
            if (checkSupplierCodeUnique(supplierDTO.getSupplierCode())) {
                throw new BusinessException("供应商编码已存在");
            }
        }

        // 检查供应商名称是否唯一（排除自身）
        if (StringUtils.isNotEmpty(supplierDTO.getSupplierName()) &&
            !existingSupplier.getSupplierName().equals(supplierDTO.getSupplierName())) {
            if (checkSupplierNameUnique(supplierDTO.getSupplierName())) {
                throw new BusinessException("供应商名称已存在");
            }
        }

        // 转换实体
        PurchaseSupplier supplier = supplierConverter.toEntity(supplierDTO);

        // 更新供应商
        int result = supplierMapper.updateById(supplier);
        if (result <= 0) {
            throw new BusinessException("更新供应商失败");
        }

        // 标签（dev-20260911-007：全量替换；null=不改）
        if (supplierDTO.getTagIds() != null) {
            tagService.setBizTags(TAG_BIZ_TYPE, supplierDTO.getSupplierId(), supplierDTO.getTagIds(),
                    SecurityUtils.getUsername());
        }

        return result;
    }

    @Override
    @Event(value = "purchase.supplier.deleted", bizId = "#supplierId", bizType = "'purchase'")
    @Transactional(rollbackFor = Exception.class)
    public int deleteSupplierById(Long supplierId) {
        // 检查供应商是否存在
        PurchaseSupplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new BusinessException("供应商不存在");
        }

        // 检查是否有关联的采购订单
        LambdaQueryWrapper<PurchaseOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PurchaseOrder::getSupplierId,supplierId);
        long orderCount = purchaseOrderMapper.selectCount(queryWrapper);
        if (orderCount>0) {
            throw new BusinessException("该供应商存在关联的采购订单，不能删除");
        }

        // 删除供应商（逻辑删除）
        return supplierMapper.deleteById(supplierId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSupplierByIds(Long[] supplierIds) {
        int count = 0;
        for (Long supplierId : supplierIds) {
            count += deleteSupplierById(supplierId);
        }
        return count;
    }

    @Override
    public boolean checkSupplierCodeUnique(String supplierCode) {
        return supplierMapper.checkSupplierCodeUnique(supplierCode) > 0;
    }

    @Override
    public boolean checkSupplierNameUnique(String supplierName) {
        return supplierMapper.checkSupplierNameUnique(supplierName) > 0;
    }

    @Override
    @Event(value = "purchase.supplier.status_updated", bizId = "#supplierId", bizType = "'purchase'")
    public int updateSupplierStatus(Long supplierId, Integer status) {
        // 检查供应商是否存在
        PurchaseSupplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new BusinessException("供应商不存在");
        }

        // 验证状态值
        if (!StatusEnum.isValid(status)) {
            throw new BusinessException("状态值不正确，必须是" + StatusEnum.NORMAL.getCode() + "（正常）或" + StatusEnum.DISABLE.getCode() + "（停用）");
        }

        return supplierMapper.updateSupplierStatus(supplierId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateSupplierEvaluation(SupplierEvaluationDTO supplierEvaluationDTO) {
        // 检查供应商是否存在
        PurchaseSupplier supplier = supplierMapper.selectById(supplierEvaluationDTO.getSupplierId());
        if (supplier == null) {
            throw new BusinessException("供应商不存在");
        }
        PurchaseSupplier convert = supplierConverter.toEntity(supplierEvaluationDTO);
        // 更新最后评估日期
        return supplierMapper.updateById(convert);
    }

    @Override
    public List<PurchaseSupplierVO> selectSuppliersByType(String supplierType) {
        LambdaQueryWrapper<PurchaseSupplier> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(PurchaseSupplier::getSupplierType, supplierType);
        wrapper.eq(PurchaseSupplier::getStatus, StatusEnum.NORMAL.getCode()); // 只查询正常状态的供应商
        wrapper.orderByDesc(PurchaseSupplier::getEvaluationScore);

        List<PurchaseSupplier> suppliers = supplierMapper.selectList(wrapper);
        return supplierConverter.toVOList(suppliers);
    }

    @Override
    public List<PurchaseSupplierVO> selectActiveSuppliers() {
        LambdaQueryWrapper<PurchaseSupplier> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(PurchaseSupplier::getStatus, StatusEnum.NORMAL.getCode()); // 正常状态
        wrapper.orderByDesc(PurchaseSupplier::getEvaluationScore);

        List<PurchaseSupplier> suppliers = supplierMapper.selectList(wrapper);
        return supplierConverter.toVOList(suppliers);
    }

    @Override
    public List<PurchaseSupplierVO> selectHighQualitySuppliers(Double minScore) {
        LambdaQueryWrapper<PurchaseSupplier> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(PurchaseSupplier::getStatus, StatusEnum.NORMAL.getCode()); // 正常状态

        if (minScore != null) {
            wrapper.ge(PurchaseSupplier::getEvaluationScore, BigDecimal.valueOf(minScore));
        }

        wrapper.orderByDesc(PurchaseSupplier::getEvaluationScore);

        List<PurchaseSupplier> suppliers = supplierMapper.selectList(wrapper);
        return supplierConverter.toVOList(suppliers);
    }

    @Override
    public String exportSupplierList(PurchaseSupplierQueryVO queryVO) {
        // TODO: 实现导出功能
        throw new BusinessException("导出功能暂未实现");
    }

    @Override
    public Object getSupplierStatistics() {
        Map<String, Object> stats = new HashMap<>();
        List<PurchaseSupplier> all = supplierMapper.selectList(Wrappers.emptyWrapper());
        stats.put("totalCount", (long) all.size());
        long disabledCount = all.stream().filter(s -> s.getStatus() != null && s.getStatus() == 1).count();
        stats.put("normalCount", all.size() - disabledCount);
        stats.put("disabledCount", disabledCount);
        // dev-20260912-003：类型改为 R/A/I/F/E/O；materialsCount/equipmentCount/otherCount 保留键名兼容
        stats.put("materialsCount", all.stream().filter(s -> "R".equals(s.getSupplierType())).count());
        stats.put("auxiliaryCount", all.stream().filter(s -> "A".equals(s.getSupplierType())).count());
        stats.put("inkCount", all.stream().filter(s -> "I".equals(s.getSupplierType())).count());
        stats.put("finishedCount", all.stream().filter(s -> "F".equals(s.getSupplierType())).count());
        stats.put("equipmentCount", all.stream().filter(s -> "E".equals(s.getSupplierType())).count());
        stats.put("otherCount", all.stream().filter(s -> !"R".equals(s.getSupplierType()) && !"E".equals(s.getSupplierType())).count());
        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importSuppliers(List<SupplierImportDTO> importList, String operName) {
        if (importList == null || importList.isEmpty()) {
            throw new BusinessException("导入数据为空");
        }

        int successCount = 0;
        int failCount = 0;
        int updateCount = 0;
        StringBuilder errorMsg = new StringBuilder();

        for (int i = 0; i < importList.size(); i++) {
            SupplierImportDTO importDTO = importList.get(i);
            try {
                // 编码优先匹配；未填编码时按名称匹配（dev-20260911-005）
                String code = StringUtils.trimToNull(importDTO.getSupplierCode());
                PurchaseSupplier existingSupplier;
                if (code != null) {
                    existingSupplier = supplierMapper.selectBySupplierCode(code);
                } else if (StringUtils.isNotBlank(importDTO.getSupplierName())) {
                    existingSupplier = supplierMapper.selectBySupplierName(importDTO.getSupplierName().trim());
                } else {
                    existingSupplier = null;
                }
                // 供应商类型（dev-20260912-003）：能对应字典编码的直接用；否则按供货品类大类归类
                //   - 大类命中关键词 → 对应类型；未命中 → 其他(O)
                //   - 原值（大类*明细）仍整体进标签，保留两级明细
                //   （保留 dev-20260911-007 的标签兜底：历史表把供货品类写在类型列）
                String rawType = StringUtils.trimToNull(importDTO.getSupplierType());
                String supplierType = null;
                StringBuilder extraTag = new StringBuilder();
                if (rawType != null) {
                    if (com.jjx.purchase.domain.enums.SupplierTypeEnum.isValid(rawType)) {
                        supplierType = rawType.trim().toUpperCase();
                    } else {
                        supplierType = com.jjx.purchase.domain.enums.SupplierTypeEnum.classifyByGoodsCategory(rawType);
                        extraTag.append(rawType);
                    }
                }
                // 解析标签（专用列 + 类型列误填的文本）
                List<Long> tagIds = resolveImportTags(importDTO.getSupplierTags(), extraTag.toString());

                if (existingSupplier != null) {
                    // 更新已有供应商
                    existingSupplier.setSupplierName(importDTO.getSupplierName());
                    if (supplierType != null) {
                        existingSupplier.setSupplierType(supplierType);
                    }
                    existingSupplier.setContactPerson(importDTO.getContactPerson());
                    existingSupplier.setPhone(importDTO.getPhone());
                    existingSupplier.setEmail(importDTO.getEmail());
                    existingSupplier.setAddress(importDTO.getAddress());
                    existingSupplier.setTaxNumber(importDTO.getTaxNo());
                    existingSupplier.setBankAccount(importDTO.getBankAccount());
                    existingSupplier.setRemark(importDTO.getRemark());
                    supplierMapper.updateById(existingSupplier);
                    if (!tagIds.isEmpty()) {
                        tagService.setBizTags(TAG_BIZ_TYPE, existingSupplier.getSupplierId(),
                                mergeTagIds(existingSupplier.getSupplierId(), tagIds), operName);
                    }
                    updateCount++;
                } else {
                    // 新增供应商
                    PurchaseSupplier supplier = new PurchaseSupplier();
                    // 编码留空时系统生成（dev-20260911-005：SUP + 5 位流水）
                    supplier.setSupplierCode(code != null ? code : generateSupplierCode());
                    supplier.setSupplierName(importDTO.getSupplierName());
                    supplier.setSupplierType(supplierType != null ? supplierType
                            : com.jjx.purchase.domain.enums.SupplierTypeEnum.OTHER.getCode());
                    supplier.setContactPerson(importDTO.getContactPerson());
                    supplier.setPhone(importDTO.getPhone());
                    supplier.setEmail(importDTO.getEmail());
                    supplier.setAddress(importDTO.getAddress());
                    supplier.setTaxNumber(importDTO.getTaxNo());
                    supplier.setBankAccount(importDTO.getBankAccount());
                    supplier.setRemark(importDTO.getRemark());
                    supplier.setStatus(StatusEnum.NORMAL.getCode()); // 默认正常状态
                    supplier.setEvaluationScore(BigDecimal.ZERO);
                    supplier.setQualityScore(BigDecimal.ZERO);
                    supplier.setDeliveryScore(BigDecimal.ZERO);
                    supplier.setPriceScore(BigDecimal.ZERO);
                    supplierMapper.insert(supplier);
                    if (!tagIds.isEmpty()) {
                        tagService.setBizTags(TAG_BIZ_TYPE, supplier.getSupplierId(), tagIds, operName);
                    }
                    successCount++;
                }
            } catch (Exception e) {
                log.error("导入第{}行失败: {}", i + 1, e.getMessage());
                failCount++;
                errorMsg.append(String.format("第%d行(%s): %s\n", i + 1, importDTO.getSupplierCode(), e.getMessage()));
            }
        }

        // 构建结果消息
        StringBuilder resultMsg = new StringBuilder();
        resultMsg.append("导入完成。");
        if (successCount > 0) {
            resultMsg.append(String.format("新增%d条，", successCount));
        }
        if (updateCount > 0) {
            resultMsg.append(String.format("更新%d条，", updateCount));
        }
        if (failCount > 0) {
            resultMsg.append(String.format("失败%d条。\n%s", failCount, errorMsg.toString()));
        } else {
            resultMsg.append("全部成功。");
        }

        log.info(resultMsg.toString());
        return resultMsg.toString();
    }

    /**
     * 解析导入的标签文本为标签ID列表（dev-20260911-007）
     * <p>多个标签用 / ， , ; ； | 分隔；单项支持「大类*明细」两级，标签不存在时自动建档。</p>
     */
    private List<Long> resolveImportTags(String... tagTexts) {
        List<Long> ids = new java.util.ArrayList<>();
        for (String tagText : tagTexts) {
            if (StringUtils.isBlank(tagText)) {
                continue;
            }
            for (String raw : tagText.split("[/,，;；|、]", -1)) {
                String item = raw == null ? "" : raw.trim();
                if (item.isEmpty()) {
                    continue;
                }
                Long parentId = null;
                String name = item;
                int star = item.indexOf('*');
                if (star > 0 && star < item.length() - 1) {
                    String parentName = item.substring(0, star).trim();
                    name = item.substring(star + 1).trim();
                    if (!parentName.isEmpty()) {
                        parentId = tagService.ensureTag(TAG_GROUP_SUPPLIER_GOODS, parentName, null,
                                SecurityUtils.getUsername());
                    }
                }
                Long tagId = tagService.ensureTag(TAG_GROUP_SUPPLIER_GOODS, name, parentId,
                        SecurityUtils.getUsername());
                if (tagId != null && !ids.contains(tagId)) {
                    ids.add(tagId);
                }
            }
        }
        return ids;
    }

    /** 合并已有标签与新标签（导入时不覆盖原有标签） */
    private List<Long> mergeTagIds(Long supplierId, List<Long> newTagIds) {
        List<Long> merged = new java.util.ArrayList<>();
        for (SysTag tag : tagService.getBizTags(TAG_BIZ_TYPE, supplierId)) {
            merged.add(tag.getTagId());
        }
        for (Long id : newTagIds) {
            if (!merged.contains(id)) {
                merged.add(id);
            }
        }
        return merged;
    }
}
