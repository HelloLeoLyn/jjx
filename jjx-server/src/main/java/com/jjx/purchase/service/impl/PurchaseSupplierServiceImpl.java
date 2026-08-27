package com.jjx.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.jjx.system.annotation.Event;

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

        // 排序
        wrapper.orderByDesc(PurchaseSupplier::getCreateTime).orderByDesc(PurchaseSupplier::getSupplierId);

        // DEV-696：分页（pageNum/pageSize 为空时退化为全量，兼容下拉框等数组调用方）
        int pageNum = queryVO.getPageNum() != null ? queryVO.getPageNum() : 1;
        int pageSize = queryVO.getPageSize() != null ? queryVO.getPageSize() : Integer.MAX_VALUE;
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PurchaseSupplier> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.core.metadata.IPage<PurchaseSupplier> pageResult = supplierMapper.selectPage(page, wrapper);

        List<PurchaseSupplierVO> voList = supplierConverter.toVOList(pageResult.getRecords());
        return com.jjx.common.core.page.PageResult.of(pageResult, voList);
    }

    @Override
    public PurchaseSupplierVO selectSupplierById(Long supplierId) {
        PurchaseSupplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new BusinessException("供应商不存在");
        }
        return supplierConverter.toVO(supplier);
    }

    @Override
    public PurchaseSupplierVO selectSupplierByName(String supplierName) {
        LambdaQueryWrapper<PurchaseSupplier> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(PurchaseSupplier::getSupplierName,supplierName);
        PurchaseSupplier purchaseSupplier = supplierMapper.selectOne(wrapper);
        return supplierConverter.toVO(purchaseSupplier);
    }

    @Override
    @Event(value = "purchase.supplier.created", bizId = "#supplierDTO", bizType = "'purchase'")
    @Transactional(rollbackFor = Exception.class)
    public int insertSupplier(PurchaseSupplierDTO supplierDTO) {
        // 检查供应商编码是否唯一
        if (checkSupplierCodeUnique(supplierDTO.getSupplierCode())) {
            throw new BusinessException("供应商编码已存在");
        }

        // 检查供应商名称是否唯一
        if (checkSupplierNameUnique(supplierDTO.getSupplierName())) {
            throw new BusinessException("供应商名称已存在");
        }

        // 验证必填字段
        if (StringUtils.isEmpty(supplierDTO.getSupplierCode())) {
            throw new BusinessException("供应商编码不能为空");
        }
        if (StringUtils.isEmpty(supplierDTO.getSupplierName())) {
            throw new BusinessException("供应商名称不能为空");
        }
        if (supplierDTO.getSupplierType() == null) {
            throw new BusinessException("供应商类型不能为空");
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

        // 保存供应商
        int result = supplierMapper.insert(supplier);
        if (result <= 0) {
            throw new BusinessException("保存供应商失败");
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
        stats.put("materialsCount", all.stream().filter(s -> "M".equals(s.getSupplierType())).count());
        stats.put("equipmentCount", all.stream().filter(s -> "E".equals(s.getSupplierType())).count());
        stats.put("otherCount", all.stream().filter(s -> !"M".equals(s.getSupplierType()) && !"E".equals(s.getSupplierType())).count());
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
                // 检查供应商编码是否已存在
                PurchaseSupplier existingSupplier = supplierMapper.selectBySupplierCode(importDTO.getSupplierCode());
                if (existingSupplier != null) {
                    // 更新已有供应商
                    existingSupplier.setSupplierName(importDTO.getSupplierName());
                    existingSupplier.setSupplierType(importDTO.getSupplierType());
                    existingSupplier.setContactPerson(importDTO.getContactPerson());
                    existingSupplier.setPhone(importDTO.getPhone());
                    existingSupplier.setEmail(importDTO.getEmail());
                    existingSupplier.setAddress(importDTO.getAddress());
                    existingSupplier.setTaxNumber(importDTO.getTaxNo());
                    existingSupplier.setBankAccount(importDTO.getBankAccount());
                    existingSupplier.setRemark(importDTO.getRemark());
                    supplierMapper.updateById(existingSupplier);
                    updateCount++;
                } else {
                    // 新增供应商
                    PurchaseSupplier supplier = new PurchaseSupplier();
                    supplier.setSupplierCode(importDTO.getSupplierCode());
                    supplier.setSupplierName(importDTO.getSupplierName());
                    supplier.setSupplierType(importDTO.getSupplierType());
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
}
