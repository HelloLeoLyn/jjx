package com.jjx.product.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.product.domain.converter.ProductStandardProcessConverter;
import com.jjx.product.domain.dto.ProductStandardProcessQueryDTO;
import com.jjx.product.domain.entity.ProductStandardProcess;
import com.jjx.product.domain.vo.ProductStandardProcessVO;
import com.jjx.product.mapper.EngineeringRoutingItemMapper;
import com.jjx.product.mapper.ProductStandardProcessMapper;
import com.jjx.product.service.IProductStandardProcessService;
import com.jjx.system.service.SysDictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.jjx.system.annotation.Event;

/**
 * 产品标准工序服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductStandardProcessServiceImpl extends ServiceImpl<ProductStandardProcessMapper, ProductStandardProcess>
        implements IProductStandardProcessService {

    private final ProductStandardProcessMapper processMapper;
    private final EngineeringRoutingItemMapper routingItemMapper;
    private final ProductStandardProcessConverter productStandardProcessConverter;
    private final SysDictService dictService;

    // ==================== 基础 CRUD ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductStandardProcessVO createProcess(ProductStandardProcess process) {
        // 检查编码是否唯一
        if (!checkProcessCodeUnique(process.getProcessCode(), null)) {
            throw new BusinessException(BusinessExceptionEnum.BOM_CODE_DUPLICATE);
        }

        // 设置默认值
        if (process.getIsEnabled() == null) {
            process.setIsEnabled(1);
        }
        if (process.getDisplayOrder() == null) {
            process.setDisplayOrder(0);
        }
        if (process.getStandardLaborHours() == null) {
            process.setStandardLaborHours(BigDecimal.ZERO);
        }
        if (process.getStandardMachineHours() == null) {
            process.setStandardMachineHours(BigDecimal.ZERO);
        }

        // JSON字段处理：空字符串转为null，避免MySQL JSON类型报错
        if (StringUtils.isBlank(process.getProcessParamTemplate())) {
            process.setProcessParamTemplate(null);
        }

        process.setCreateTime(LocalDateTime.now());
        process.setUpdateTime(LocalDateTime.now());

        boolean success = save(process);
        if (!success) {
            throw new BusinessException(BusinessExceptionEnum.DB_INSERT_FAILED);
        }

        log.info("创建标准工序成功: {} - {}", process.getProcessCode(), process.getProcessName());
        return productStandardProcessConverter.toVO(process);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductStandardProcessVO updateProcess(ProductStandardProcess process) {
        ProductStandardProcess existing = getById(process.getProcessId());
        if (existing == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        // 检查编码是否唯一（排除自身）
        if (!existing.getProcessCode().equals(process.getProcessCode())) {
            if (!checkProcessCodeUnique(process.getProcessCode(), process.getProcessId())) {
                throw new BusinessException(BusinessExceptionEnum.BOM_CODE_DUPLICATE);
            }
        }

        // 更新字段
        existing.setProcessCode(process.getProcessCode());
        existing.setProcessName(process.getProcessName());
        existing.setProcessType(process.getProcessType());
        existing.setProcessCategory(process.getProcessCategory());
        existing.setStandardLaborHours(process.getStandardLaborHours());
        existing.setStandardMachineHours(process.getStandardMachineHours());
        existing.setProcessParamTemplate(process.getProcessParamTemplate());
        existing.setSkillRequirement(process.getSkillRequirement());
        existing.setEquipmentType(process.getEquipmentType());
        existing.setQualityStandard(process.getQualityStandard());
        existing.setDescription(process.getDescription());
        existing.setDisplayOrder(process.getDisplayOrder());
        existing.setUpdateTime(LocalDateTime.now());

        // JSON字段处理：空字符串转为null，避免MySQL JSON类型报错
        if (StringUtils.isBlank(existing.getProcessParamTemplate())) {
            existing.setProcessParamTemplate(null);
        }

        boolean success = updateById(existing);
        if (!success) {
            throw new BusinessException(BusinessExceptionEnum.DB_UPDATE_FAILED);
        }

        log.info("更新标准工序成功: {} - {}", existing.getProcessCode(), existing.getProcessName());
        return productStandardProcessConverter.toVO(existing);
    }

    @Override
    @Event(value = "product.standard_process.deleted", bizId = "#processId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void deleteProcess(Long processId) {
        ProductStandardProcess process = getById(processId);
        if (process == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        // 检查是否可删除
        if (!canDelete(processId)) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_PROCESS_ALREADY_EXISTS,
                    String.format("工序 %s 已被工艺路线引用，无法删除", process.getProcessCode()));
        }

        boolean success = removeById(processId);
        if (!success) {
            throw new BusinessException(BusinessExceptionEnum.DB_DELETE_FAILED);
        }

        log.info("删除标准工序成功: {} - {}", process.getProcessCode(), process.getProcessName());
    }

    @Override
    public IPage<ProductStandardProcessVO> pageQuery(ProductStandardProcessQueryDTO queryDTO) {
        LambdaQueryWrapper<ProductStandardProcess> wrapper = buildQueryWrapper(queryDTO);

        // 排序
        if (StringUtils.isNotBlank(queryDTO.getOrderByColumn())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getIsAsc());
            switch (queryDTO.getOrderByColumn()) {
                case "processId":
                    wrapper.orderBy(true, isAsc, ProductStandardProcess::getProcessId);
                    break;
                case "processCode":
                    wrapper.orderBy(true, isAsc, ProductStandardProcess::getProcessCode);
                    break;
                case "processName":
                    wrapper.orderBy(true, isAsc, ProductStandardProcess::getProcessName);
                    break;
                case "displayOrder":
                    wrapper.orderBy(true, isAsc, ProductStandardProcess::getDisplayOrder);
                    break;
                case "createTime":
                    wrapper.orderBy(true, isAsc, ProductStandardProcess::getCreateTime);
                    break;
                default:
                    wrapper.orderByDesc(ProductStandardProcess::getCreateTime);
            }
        } else {
            wrapper.orderByAsc(ProductStandardProcess::getDisplayOrder)
                    .orderByDesc(ProductStandardProcess::getCreateTime);
        }

        Page<ProductStandardProcess> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<ProductStandardProcess> resultPage = page(page, wrapper);

        // 转换为VO
        Page<ProductStandardProcessVO> voPage = new Page<>();
        BeanUtil.copyProperties(resultPage, voPage, "records");
        List<ProductStandardProcess> list = resultPage.getRecords();
        List<ProductStandardProcessVO> voList = productStandardProcessConverter.toVOList(list);
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public ProductStandardProcessVO getProcessById(Long processId) {
        ProductStandardProcess process = getById(processId);
        if (process == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }
        return productStandardProcessConverter.toVO(process);
    }

    // ==================== 状态管理 ====================

    @Override
    @Event(value = "product.standard_process.status_updated", bizId = "#processId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(Long processId, Boolean enabled) {
        ProductStandardProcess process = getById(processId);
        if (process == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        process.setIsEnabled(enabled ? 1 : 0);
        process.setUpdateTime(LocalDateTime.now());

        boolean success = updateById(process);
        if (!success) {
            throw new BusinessException(BusinessExceptionEnum.DB_UPDATE_FAILED);
        }

        log.info("设置工序状态成功: {} -> {}", process.getProcessCode(), enabled ? "启用" : "禁用");
    }

    // ==================== 查询接口 ====================

    @Override
    public List<ProductStandardProcessVO> getEnabledProcesses() {
        LambdaQueryWrapper<ProductStandardProcess> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductStandardProcess::getIsEnabled, 1)
                .orderByAsc(ProductStandardProcess::getDisplayOrder);

        List<ProductStandardProcess> processes = list(wrapper);
        return productStandardProcessConverter.toVOList(processes);
    }

    @Override
    public List<ProductStandardProcessVO> getByProcessType(String processType) {
        LambdaQueryWrapper<ProductStandardProcess> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductStandardProcess::getProcessType, processType)
                .eq(ProductStandardProcess::getIsEnabled, 1)
                .orderByAsc(ProductStandardProcess::getDisplayOrder);

        List<ProductStandardProcess> processes = list(wrapper);
        return productStandardProcessConverter.toVOList(processes);
    }

    @Override
    public List<ProductStandardProcessVO> getByProcessCategory(String processCategory) {
        LambdaQueryWrapper<ProductStandardProcess> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductStandardProcess::getProcessCategory, processCategory)
                .eq(ProductStandardProcess::getIsEnabled, 1)
                .orderByAsc(ProductStandardProcess::getDisplayOrder);

        List<ProductStandardProcess> processes = list(wrapper);
        return productStandardProcessConverter.toVOList(processes);
    }

    @Override
    public List<ProductStandardProcessVO> getByProcessTypeAndCategory(String processType, String processCategory) {
        LambdaQueryWrapper<ProductStandardProcess> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductStandardProcess::getProcessType, processType)
                .eq(ProductStandardProcess::getProcessCategory, processCategory)
                .eq(ProductStandardProcess::getIsEnabled, 1)
                .orderByAsc(ProductStandardProcess::getDisplayOrder);

        List<ProductStandardProcess> processes = list(wrapper);
        return productStandardProcessConverter.toVOList(processes);
    }

    @Override
    public List<ProductStandardProcessVO> getProcessesByIds(List<Long> processIds) {
        if (processIds == null || processIds.isEmpty()) {
            return List.of();
        }

        List<ProductStandardProcess> processes = listByIds(processIds);
        return productStandardProcessConverter.toVOList(processes);
    }

    // ==================== 批量操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateDisplayOrder(List<Long> processIds) {
        if (processIds == null || processIds.isEmpty()) {
            return;
        }

        for (int i = 0; i < processIds.size(); i++) {
            ProductStandardProcess process = new ProductStandardProcess();
            process.setProcessId(processIds.get(i));
            process.setDisplayOrder(i + 1);
            process.setUpdateTime(LocalDateTime.now());
            updateById(process);
        }

        log.info("批量更新工序显示顺序成功，数量: {}", processIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchEnable(List<Long> processIds) {
        if (processIds == null || processIds.isEmpty()) {
            return;
        }

        for (Long processId : processIds) {
            setEnabled(processId, true);
        }

        log.info("批量启用工序成功，数量: {}", processIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDisable(List<Long> processIds) {
        if (processIds == null || processIds.isEmpty()) {
            return;
        }

        for (Long processId : processIds) {
            setEnabled(processId, false);
        }

        log.info("批量禁用工序成功，数量: {}", processIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> processIds) {
        if (processIds == null || processIds.isEmpty()) {
            return;
        }

        // 检查是否可删除
        for (Long processId : processIds) {
            if (!canDelete(processId)) {
                ProductStandardProcess process = getById(processId);
                throw new BusinessException(BusinessExceptionEnum.ROUTING_PROCESS_ALREADY_EXISTS,
                        String.format("工序 %s 已被引用，无法删除", process != null ? process.getProcessCode() : processId));
            }
        }

        boolean success = removeByIds(processIds);
        if (!success) {
            throw new BusinessException(BusinessExceptionEnum.DB_DELETE_FAILED);
        }

        log.info("批量删除工序成功，数量: {}", processIds.size());
    }

    // ==================== 验证接口 ====================

    @Override
    public boolean checkProcessCodeUnique(String processCode, Long excludeId) {
        LambdaQueryWrapper<ProductStandardProcess> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductStandardProcess::getProcessCode, processCode);
        if (excludeId != null) {
            wrapper.ne(ProductStandardProcess::getProcessId, excludeId);
        }
        return count(wrapper) == 0;
    }

    @Override
    public boolean canDelete(Long processId) {
        // 检查是否被工艺路线引用
        // TODO: 实现引用检查，需要 EngineeringRoutingItemMapper 中的方法
        // return routingItemMapper.countByProcessId(processId) == 0;
        return true;
    }

    // ==================== 编码生成 ====================

    @Override
    public String generateNextProcessCode(String processType, String processCategory) {
        if (StringUtils.isBlank(processType) || StringUtils.isBlank(processCategory)) {
            throw new BusinessException("工序类型和工序类别不能为空");
        }

        // 2. 查询已存在的最大序号
        String prefix = "T" + processType + "C" + processCategory;
        LambdaQueryWrapper<ProductStandardProcess> wrapper = Wrappers.lambdaQuery();
        wrapper.likeRight(ProductStandardProcess::getProcessCode, prefix);
        wrapper.orderByDesc(ProductStandardProcess::getProcessCode);
        wrapper.last("LIMIT 1");
        List<ProductStandardProcess> existing = processMapper.selectList(wrapper);

        int nextSeq = 1;
        if (CollUtil.isNotEmpty(existing)) {
            String lastCode = existing.get(0).getProcessCode();
            String seqStr = lastCode.substring(prefix.length());
            nextSeq = Integer.parseInt(seqStr) + 1;
        }

        // 3. 生成新编码
        return prefix + String.format("%03d", nextSeq);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建查询条件
     */
    private static LambdaQueryWrapper<ProductStandardProcess> buildQueryWrapper(ProductStandardProcessQueryDTO queryDTO) {
        LambdaQueryWrapper<ProductStandardProcess> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(queryDTO.getProcessCode())) {
            wrapper.like(ProductStandardProcess::getProcessCode, queryDTO.getProcessCode());
        }
        if (StringUtils.isNotBlank(queryDTO.getProcessName())) {
            wrapper.like(ProductStandardProcess::getProcessName, queryDTO.getProcessName());
        }
        if (StringUtils.isNotBlank(queryDTO.getProcessType())) {
            wrapper.eq(ProductStandardProcess::getProcessType, queryDTO.getProcessType());
        }
        if (StringUtils.isNotBlank(queryDTO.getProcessCategory())) {
            wrapper.eq(ProductStandardProcess::getProcessCategory, queryDTO.getProcessCategory());
        }
        if (queryDTO.getIsEnabled() != null) {
            wrapper.eq(ProductStandardProcess::getIsEnabled, queryDTO.getIsEnabled());
        }

        return wrapper;
    }

    // ==================== 导入（2026-08-08） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public com.jjx.inventory.dto.vo.MaterialImportResultVO importStandardProcesses(
            java.util.List<com.jjx.product.dto.imports.StandardProcessImportDTO> importList) {
        com.jjx.inventory.dto.vo.MaterialImportResultVO result = new com.jjx.inventory.dto.vo.MaterialImportResultVO();
        if (importList == null || importList.isEmpty()) {
            return result;
        }

        // 文件内重复检测（工序编码）
        java.util.Map<String, Integer> dupCountMap = new java.util.HashMap<>();
        for (com.jjx.product.dto.imports.StandardProcessImportDTO dto : importList) {
            String code = dto.getProcessCode() == null ? "" : dto.getProcessCode().trim();
            dupCountMap.merge(code, 1, Integer::sum);
        }

        for (int i = 0; i < importList.size(); i++) {
            com.jjx.product.dto.imports.StandardProcessImportDTO dto = importList.get(i);
            int excelRow = i + 2; // 第1行表头，数据从第2行开始
            String code = dto.getProcessCode() == null ? "" : dto.getProcessCode().trim();
            try {
                // 必填校验
                if (code.isEmpty()) {
                    result.addFail(excelRow, dto.getProcessName(), "工序编码不能为空");
                    continue;
                }
                if (dto.getProcessName() == null || dto.getProcessName().trim().isEmpty()) {
                    result.addFail(excelRow, code, "工序名称不能为空");
                    continue;
                }
                // 文件内重复
                if (dupCountMap.getOrDefault(code, 0) > 1) {
                    result.addFail(excelRow, code, "文件内重复行（工序编码出现 " + dupCountMap.get(code) + " 次），请删除重复行或合并");
                    continue;
                }
                // 类型/类别枚举校验
                String type = dto.getProcessType() == null ? "" : dto.getProcessType().trim();
                if (!type.isEmpty() && !com.jjx.product.enums.ProcessTypeEnum.isValidCode(type)) {
                    result.addFail(excelRow, code, "工序类型不合法: " + type + "（MAIN_PAD/UP_LINE/DOWN_LINE/PRINTING/CUTTING/LAMINATING/TESTING/PACKAGING）");
                    continue;
                }
                String category = dto.getProcessCategory() == null ? "" : dto.getProcessCategory().trim();
                if (!category.isEmpty() && !com.jjx.product.enums.ProcessCategoryEnum.isValidCode(category)) {
                    result.addFail(excelRow, code, "工序类别不合法: " + category + "（PREPARATION/MAIN/FINISHING/QUALITY）");
                    continue;
                }
                // 工时/机时/排序数字解析
                java.math.BigDecimal laborHours = parseDecimal(dto.getStandardLaborHours());
                java.math.BigDecimal machineHours = parseDecimal(dto.getStandardMachineHours());
                Integer displayOrder = parseInteger(dto.getDisplayOrder());
                Integer isEnabled = parseInteger(dto.getIsEnabled());
                if (isEnabled == null) isEnabled = 1;
                if (isEnabled != 0 && isEnabled != 1) {
                    result.addFail(excelRow, code, "启用列只能填 1 或 0");
                    continue;
                }

                // 库内判重（按工序编码）
                Long existCount = processMapper.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.product.domain.entity.ProductStandardProcess>()
                                .eq(com.jjx.product.domain.entity.ProductStandardProcess::getProcessCode, code));
                if (existCount != null && existCount > 0) {
                    result.setSkipCount(result.getSkipCount() + 1);
                    continue;
                }

                com.jjx.product.domain.entity.ProductStandardProcess p = new com.jjx.product.domain.entity.ProductStandardProcess();
                p.setProcessCode(code);
                p.setProcessName(dto.getProcessName().trim());
                p.setProcessType(type.isEmpty() ? null : type);
                p.setProcessCategory(category.isEmpty() ? null : category);
                p.setStandardLaborHours(laborHours);
                p.setStandardMachineHours(machineHours);
                p.setProcessParamTemplate(dto.getProcessParamTemplate());
                p.setSkillRequirement(dto.getSkillRequirement());
                p.setEquipmentType(dto.getEquipmentType());
                p.setQualityStandard(dto.getQualityStandard());
                p.setDescription(dto.getDescription());
                p.setDisplayOrder(displayOrder == null ? 0 : displayOrder);
                p.setIsEnabled(isEnabled);
                processMapper.insert(p);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                result.addFail(excelRow, code, "导入失败: " + e.getMessage());
            }
        }
        return result;
    }

    private java.math.BigDecimal parseDecimal(String v) {
        if (v == null || v.trim().isEmpty()) return null;
        try {
            return new java.math.BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(String v) {
        if (v == null || v.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
