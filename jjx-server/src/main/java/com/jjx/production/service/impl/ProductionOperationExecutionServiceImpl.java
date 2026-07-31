package com.jjx.production.service.impl;

import com.jjx.production.enums.ExecutionStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.ProductionOperationExecutionCreateDTO;
import com.jjx.production.domain.dto.ProductionOperationExecutionQueryDTO;
import com.jjx.production.domain.dto.ProductionOperationExecutionUpdateDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.vo.ProductionOperationExecutionVO;
import com.jjx.production.domain.vo.ProductionOrderVO;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.ProductionOperationExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 生产工序执行服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionOperationExecutionServiceImpl extends ServiceImpl<ProductionOperationExecutionMapper, ProductionOperationExecution>
        implements ProductionOperationExecutionService {

    private final ProductionOperationExecutionMapper productionOperationExecutionMapper;
    private final ProductionOrderMapper productionOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createExecution(ProductionOperationExecutionCreateDTO createDTO) {
        log.info("创建工序执行记录: {}", createDTO);

        // 验证数据
        validateExecutionData(createDTO);

        // 转换为实体
        ProductionOperationExecution execution = convertCreateDTOToEntity(createDTO);
        execution.setExecutionStatus(ExecutionStatusEnum.PENDING.getCode()); // 默认状态为待执行

        // 保存到数据库
        boolean success = save(execution);
        if (!success) {
            throw new BusinessException("创建工序执行记录失败");
        }

        log.info("工序执行记录创建成功, ID: {}", execution.getExecutionId());
        return execution.getExecutionId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateExecution(ProductionOperationExecutionUpdateDTO updateDTO) {
        log.info("更新工序执行记录: {}", updateDTO);

        // 检查记录是否存在
        ProductionOperationExecution execution = getById(updateDTO.getExecutionId());
        if (execution == null) {
            throw new BusinessException("工序执行记录不存在: " + updateDTO.getExecutionId());
        }

        // 更新实体
        updateEntityFromUpdateDTO(execution, updateDTO);

        // 更新到数据库
        boolean success = updateById(execution);
        if (!success) {
            throw new BusinessException("更新工序执行记录失败");
        }

        log.info("工序执行记录更新成功, ID: {}", execution.getExecutionId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteExecution(Long executionId) {
        log.info("删除工序执行记录: {}", executionId);

        // 检查记录是否存在
        ProductionOperationExecution execution = getById(executionId);
        if (execution == null) {
            throw new BusinessException("工序执行记录不存在: " + executionId);
        }

        // 检查记录状态，只有特定状态可以删除
        if (!canDeleteExecution(execution)) {
            throw new BusinessException("记录状态不允许删除");
        }

        // 删除记录
        boolean success = removeById(executionId);
        if (!success) {
            throw new BusinessException("删除工序执行记录失败");
        }

        log.info("工序执行记录删除成功, ID: {}", executionId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeleteExecution(List<Long> executionIds) {
        log.info("批量删除工序执行记录: {}", executionIds);

        if (executionIds == null || executionIds.isEmpty()) {
            throw new BusinessException("执行记录ID列表不能为空");
        }

        // 检查所有记录是否存在且状态允许删除
        for (Long executionId : executionIds) {
            ProductionOperationExecution execution = getById(executionId);
            if (execution == null) {
                throw new BusinessException("工序执行记录不存在: " + executionId);
            }
            if (!canDeleteExecution(execution)) {
                throw new BusinessException("记录状态不允许删除: " + executionId);
            }
        }

        // 批量删除
        boolean success = removeByIds(executionIds);
        if (!success) {
            throw new BusinessException("批量删除工序执行记录失败");
        }

        log.info("批量删除工序执行记录成功, 数量: {}", executionIds.size());
        return true;
    }

    @Override
    public ProductionOperationExecutionVO getExecutionById(Long executionId) {
        log.debug("根据ID获取工序执行详情: {}", executionId);

        ProductionOperationExecution execution = getById(executionId);
        if (execution == null) {
            throw new BusinessException("工序执行记录不存在: " + executionId);
        }

        return convertToVO(execution);
    }

    @Override
    public List<ProductionOperationExecutionVO> queryExecutionList(ProductionOperationExecutionQueryDTO queryDTO) {
        log.debug("查询工序执行列表: {}", queryDTO);

        LambdaQueryWrapper<ProductionOperationExecution> wrapper = buildQueryWrapper(queryDTO);
        wrapper.orderByDesc(ProductionOperationExecution::getCreateTime);

        List<ProductionOperationExecution> executions = list(wrapper);
        return executions.stream()
                .map(ProductionOperationExecutionServiceImpl::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ProductionOperationExecutionVO> queryExecutionPage(ProductionOperationExecutionQueryDTO queryDTO) {
        log.debug("分页查询工序执行: {}", queryDTO);

        // 构建查询条件
        LambdaQueryWrapper<ProductionOperationExecution> wrapper = buildQueryWrapper(queryDTO);

        // 设置排序
        wrapper.orderByDesc(ProductionOperationExecution::getCreateTime);

        // 分页查询
        Page<ProductionOperationExecution> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<ProductionOperationExecution> executionPage = page(page, wrapper);

        // 转换为VO分页
        Page<ProductionOperationExecutionVO> voPage = new Page<>(executionPage.getCurrent(), executionPage.getSize(), executionPage.getTotal());
        List<ProductionOperationExecutionVO> voList = executionPage.getRecords().stream()
                .map(ProductionOperationExecutionServiceImpl::convertToVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startExecution(Long executionId) {
        log.info("开始工序执行: {}", executionId);

        ProductionOperationExecution execution = getById(executionId);
        if (execution == null) {
            throw new BusinessException("工序执行记录不存在: " + executionId);
        }

        // 检查记录状态是否可以开始
        if (!canStartExecution(execution)) {
            throw new BusinessException("记录状态不允许开始");
        }

        // 更新状态为进行中
        execution.setExecutionStatus(ExecutionStatusEnum.EXECUTING.getCode());
        execution.setActualStartTime(LocalDateTime.now());

        boolean success = updateById(execution);
        if (!success) {
            throw new BusinessException("开始工序执行失败");
        }

        log.info("工序执行开始成功, ID: {}", executionId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean pauseExecution(Long executionId) {
        log.info("暂停工序执行: {}", executionId);

        ProductionOperationExecution execution = getById(executionId);
        if (execution == null) {
            throw new BusinessException("工序执行记录不存在: " + executionId);
        }

        // 检查记录状态是否可以暂停
        if (!canPauseExecution(execution)) {
            throw new BusinessException("记录状态不允许暂停");
        }

        // 更新状态为暂停
        execution.setExecutionStatus(ExecutionStatusEnum.PAUSED.getCode());

        boolean success = updateById(execution);
        if (!success) {
            throw new BusinessException("暂停工序执行失败");
        }

        log.info("工序执行暂停成功, ID: {}", executionId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeExecution(Long executionId) {
        log.info("完成工序执行: {}", executionId);

        ProductionOperationExecution execution = getById(executionId);
        if (execution == null) {
            throw new BusinessException("工序执行记录不存在: " + executionId);
        }

        // 检查记录状态是否可以完成
        if (!canCompleteExecution(execution)) {
            throw new BusinessException("记录状态不允许完成");
        }

        // 更新状态为已完成
        execution.setExecutionStatus(ExecutionStatusEnum.COMPLETED.getCode());
        execution.setActualEndTime(LocalDateTime.now());

        // 如果没有设置产出数量，默认使用投入数量
        if (execution.getOutputQuantity() == null || execution.getOutputQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            execution.setOutputQuantity(execution.getInputQuantity());
        }
        // 如果没有设置合格数量，默认使用产出数量
        if (execution.getQualifiedQuantity() == null) {
            execution.setQualifiedQuantity(execution.getOutputQuantity());
        }
        // 如果没有设置不良数量，默认为0
        if (execution.getDefectiveQuantity() == null) {
            execution.setDefectiveQuantity(BigDecimal.ZERO);
        }

        boolean success = updateById(execution);
        if (!success) {
            throw new BusinessException("完成工序执行失败");
        }

        // 更新生产工单的完成数量
        updateOrderCompletedQuantity(execution.getOrderId());

        log.info("工序执行完成成功, ID: {}", executionId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelExecution(Long executionId) {
        log.info("取消工序执行: {}", executionId);

        ProductionOperationExecution execution = getById(executionId);
        if (execution == null) {
            throw new BusinessException("工序执行记录不存在: " + executionId);
        }

        // 检查记录状态是否可以取消
        if (!canCancelExecution(execution)) {
            throw new BusinessException("记录状态不允许取消");
        }

        // 更新状态为已取消
        execution.setExecutionStatus(ExecutionStatusEnum.CANCELLED.getCode());

        boolean success = updateById(execution);
        if (!success) {
            throw new BusinessException("取消工序执行失败");
        }

        log.info("工序执行取消成功, ID: {}", executionId);
        return true;
    }

    @Override
    public List<ProductionOperationExecutionVO> getExecutionsByOrderId(Long orderId) {
        log.debug("根据生产工单ID查询工序执行: {}", orderId);

        // 使用 Mapper XML 中的关联查询
        List<ProductionOperationExecution> executions = productionOperationExecutionMapper.selectByOrderId(orderId);
        return executions.stream()
                .map(ProductionOperationExecutionServiceImpl::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductionOperationExecutionVO> getExecutionsByProcessId(Long processId) {
        log.debug("根据工序ID查询工序执行: {}", processId);

        LambdaQueryWrapper<ProductionOperationExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductionOperationExecution::getProcessId, processId)
                .orderByDesc(ProductionOperationExecution::getCreateTime);

        List<ProductionOperationExecution> executions = list(wrapper);
        return executions.stream()
                .map(ProductionOperationExecutionServiceImpl::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result importExecutionData(List<ProductionOperationExecutionCreateDTO> importData) {
        log.info("导入工序执行数据, 数量: {}", importData.size());

        if (importData == null || importData.isEmpty()) {
            return Result.error("导入数据不能为空");
        }

        int successCount = 0;
        int failCount = 0;
        List<String> failMessages = new java.util.ArrayList<>();

        for (int i = 0; i < importData.size(); i++) {
            ProductionOperationExecutionCreateDTO dto = importData.get(i);
            try {
                // 验证数据
                validateExecutionData(dto);

                // 转换为实体并保存
                ProductionOperationExecution execution = convertCreateDTOToEntity(dto);
                execution.setExecutionStatus(ExecutionStatusEnum.PENDING.getCode());
                save(execution);

                successCount++;
            } catch (Exception e) {
                failCount++;
                String message = String.format("第%d行导入失败: %s", i + 1, e.getMessage());
                failMessages.add(message);
                log.error(message, e);
            }
        }

        String resultMessage = String.format("导入完成: 成功%d条, 失败%d条", successCount, failCount);
        log.info(resultMessage);

        if (failCount > 0) {
            java.util.Map<String, Object> resultData = new java.util.HashMap<>();
            resultData.put("successCount", successCount);
            resultData.put("failCount", failCount);
            resultData.put("failMessages", failMessages);
            Result<Object> result = Result.error(resultMessage);
            result.setData(resultData);
            return result;
        } else {
            return Result.success(resultMessage);
        }
    }

    @Override
    public List<ProductionOperationExecutionVO> exportExecutionData(ProductionOperationExecutionQueryDTO queryDTO) {
        log.debug("导出工序执行数据: {}", queryDTO);

        LambdaQueryWrapper<ProductionOperationExecution> wrapper = buildQueryWrapper(queryDTO);
        wrapper.orderByDesc(ProductionOperationExecution::getCreateTime);

        List<ProductionOperationExecution> executions = list(wrapper);
        return executions.stream()
                .map(ProductionOperationExecutionServiceImpl::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public Result getExecutionStatistics(ProductionOperationExecutionQueryDTO queryDTO) {
        log.debug("获取工序执行统计信息: {}", queryDTO);

        // 构建查询条件
        LambdaQueryWrapper<ProductionOperationExecution> wrapper = buildQueryWrapper(queryDTO);

        // 获取统计数据
        long totalCount = count(wrapper);

        // 按状态统计
        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.PENDING.getCode());
        long pendingCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.EXECUTING.getCode());
        long inProgressCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.COMPLETED.getCode());
        long completedCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.CANCELLED.getCode());
        long cancelledCount = count(wrapper);

        // 构建统计结果
        java.util.Map<String, Object> statistics = new java.util.HashMap<>();
        statistics.put("totalCount", totalCount);
        statistics.put("pendingCount", pendingCount);
        statistics.put("inProgressCount", inProgressCount);
        statistics.put("completedCount", completedCount);
        statistics.put("cancelledCount", cancelledCount);

        return Result.success(statistics);
    }

    // ============ 私有方法 ============

    /**
     * 更新生产工单的完成数量
     * 汇总所有已完成工序的合格数量，更新到工单的 completedQuantity
     */
    private void updateOrderCompletedQuantity(Long orderId) {
        // 查询该工单下所有已完成工序的合格数量总和
        LambdaQueryWrapper<ProductionOperationExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductionOperationExecution::getOrderId, orderId)
                .eq(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.COMPLETED.getCode());

        List<ProductionOperationExecution> completedExecutions = list(wrapper);
        BigDecimal totalQualified = completedExecutions.stream()
                .map(e -> e.getQualifiedQuantity() != null ? e.getQualifiedQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 更新工单的完成数量
        ProductionOrder order = productionOrderMapper.selectById(orderId);
        if (order != null) {
            order.setCompletedQuantity(totalQualified);
            if (order.getPlannedQuantity() != null) {
                order.setRemainingQuantity(order.getPlannedQuantity().subtract(totalQualified));
            }
            productionOrderMapper.updateById(order);
            log.info("更新工单 {} 的完成数量为: {}", orderId, totalQualified);
        }
    }

    /**
     * 构建查询条件
     */
    private static LambdaQueryWrapper<ProductionOperationExecution> buildQueryWrapper(ProductionOperationExecutionQueryDTO queryDTO) {
        LambdaQueryWrapper<ProductionOperationExecution> wrapper = new LambdaQueryWrapper<>();

        if (queryDTO.getOrderId() != null) {
            wrapper.eq(ProductionOperationExecution::getOrderId, queryDTO.getOrderId());
        }
        if (queryDTO.getProcessId() != null) {
            wrapper.eq(ProductionOperationExecution::getProcessId, queryDTO.getProcessId());
        }
        if (queryDTO.getExecutionStatus() != null) {
            wrapper.eq(ProductionOperationExecution::getExecutionStatus, queryDTO.getExecutionStatus());
        }
        if (queryDTO.getEquipmentId() != null) {
            wrapper.eq(ProductionOperationExecution::getEquipmentId, queryDTO.getEquipmentId());
        }
        if (queryDTO.getEquipmentCode() != null) {
            wrapper.eq(ProductionOperationExecution::getEquipmentCode, queryDTO.getEquipmentCode());
        }
        if (queryDTO.getOperatorId() != null) {
            wrapper.eq(ProductionOperationExecution::getOperatorId, queryDTO.getOperatorId());
        }
        if (queryDTO.getOperatorName() != null) {
            wrapper.like(ProductionOperationExecution::getOperatorName, queryDTO.getOperatorName());
        }
        if (queryDTO.getPlanStartTimeFrom() != null) {
            wrapper.ge(ProductionOperationExecution::getPlannedStartTime, queryDTO.getPlanStartTimeFrom().atStartOfDay());
        }
        if (queryDTO.getPlanStartTimeTo() != null) {
            wrapper.le(ProductionOperationExecution::getPlannedStartTime, queryDTO.getPlanStartTimeTo().atTime(23, 59, 59));
        }
        if (queryDTO.getPlanEndTimeFrom() != null) {
            wrapper.ge(ProductionOperationExecution::getPlannedEndTime, queryDTO.getPlanEndTimeFrom().atStartOfDay());
        }
        if (queryDTO.getPlanEndTimeTo() != null) {
            wrapper.le(ProductionOperationExecution::getPlannedEndTime, queryDTO.getPlanEndTimeTo().atTime(23, 59, 59));
        }

        return wrapper;
    }

    /**
     * 验证工序执行数据
     */
    private static void validateExecutionData(ProductionOperationExecutionCreateDTO createDTO) {
        if (createDTO.getOrderId() == null) {
            throw new BusinessException("生产工单ID不能为空");
        }
        if (createDTO.getProcessId() == null) {
            throw new BusinessException("工序ID不能为空");
        }
        if (createDTO.getProcessOrder() == null) {
            throw new BusinessException("工序顺序不能为空");
        }
    }

    /**
     * 检查记录是否可以删除
     */
    private static boolean canDeleteExecution(ProductionOperationExecution execution) {
        // 只有待执行和已取消状态的记录可以删除
        Integer status = execution.getExecutionStatus();
        return ExecutionStatusEnum.PENDING.getCode().equals(status) || ExecutionStatusEnum.CANCELLED.getCode().equals(status);
    }

    /**
     * 检查记录是否可以开始
     */
    private static boolean canStartExecution(ProductionOperationExecution execution) {
        // 只有待执行状态的记录可以开始
        return ExecutionStatusEnum.PENDING.getCode().equals(execution.getExecutionStatus());
    }

    /**
     * 检查记录是否可以暂停
     */
    private static boolean canPauseExecution(ProductionOperationExecution execution) {
        // 只有进行中状态的记录可以暂停
        return ExecutionStatusEnum.EXECUTING.getCode().equals(execution.getExecutionStatus());
    }

    /**
     * 检查记录是否可以完成
     */
    private static boolean canCompleteExecution(ProductionOperationExecution execution) {
        // 只有进行中状态的记录可以完成
        return ExecutionStatusEnum.EXECUTING.getCode().equals(execution.getExecutionStatus());
    }

    /**
     * 检查记录是否可以取消
     */
    private static boolean canCancelExecution(ProductionOperationExecution execution) {
        // 只有待执行和进行中状态的记录可以取消
        Integer status = execution.getExecutionStatus();
        return ExecutionStatusEnum.PENDING.getCode().equals(status) || ExecutionStatusEnum.EXECUTING.getCode().equals(status);
    }

    /**
     * 转换为VO
     */
    private static ProductionOperationExecutionVO convertToVO(ProductionOperationExecution execution) {
        ProductionOperationExecutionVO vo = new ProductionOperationExecutionVO();

        // 复制基本字段
        vo.setExecutionId(execution.getExecutionId());
        vo.setOrderId(execution.getOrderId());
        vo.setProcessId(execution.getProcessId());
        vo.setProcessOrder(execution.getProcessOrder());
        vo.setExecutionStatus(execution.getExecutionStatus());
        vo.setPlannedStartTime(execution.getPlannedStartTime());
        vo.setPlannedEndTime(execution.getPlannedEndTime());
        vo.setActualStartTime(execution.getActualStartTime());
        vo.setActualEndTime(execution.getActualEndTime());
        vo.setOperatorId(execution.getOperatorId());
        vo.setOperatorName(execution.getOperatorName());
        vo.setEquipmentId(execution.getEquipmentId());
        vo.setEquipmentCode(execution.getEquipmentCode());
        vo.setEquipmentName(execution.getEquipmentName());
        vo.setInputQuantity(execution.getInputQuantity());
        vo.setOutputQuantity(execution.getOutputQuantity());
        vo.setQualifiedQuantity(execution.getQualifiedQuantity());
        vo.setDefectiveQuantity(execution.getDefectiveQuantity());
        vo.setDefectiveReason(execution.getDefectiveReason());
        vo.setActualProcessParams(execution.getActualProcessParams());
        vo.setQualityCheckResult(execution.getQualityCheckResult());
        vo.setActualLaborHours(execution.getActualLaborHours());
        vo.setActualMachineHours(execution.getActualMachineHours());
        vo.setCreateTime(execution.getCreateTime());
        vo.setUpdateTime(execution.getUpdateTime());

        // 设置状态描述
        vo.setExecutionStatusDesc(getStatusDesc(execution.getExecutionStatus()));

        // 设置计算字段
        vo.setHasStarted(execution.hasStarted());
        vo.setHasEnded(execution.hasEnded());
        vo.setIsOverdue(execution.isOverdue());
        vo.setIsPending(execution.isPending());
        vo.setIsProcessing(execution.isProcessing());
        vo.setIsCompleted(execution.isCompleted());
        vo.setIsSkipped(execution.isSkipped());
        vo.setPlannedHours(execution.getPlannedHours());
        vo.setActualHours(execution.getActualHours());
        vo.setQualifiedRate(execution.getQualifiedRate());
        vo.setDefectiveRate(execution.getDefectiveRate());
        vo.setCanStart(execution.canStart());
        vo.setCanComplete(execution.canComplete());
        vo.setTotalActualHours(execution.getTotalActualHours());

        // 设置关联的工单信息
        if (execution.getProductionOrder() != null) {
            ProductionOrder order = execution.getProductionOrder();
            ProductionOrderVO orderVO = new ProductionOrderVO();
            orderVO.setOrderId(order.getOrderId());
            orderVO.setOrderNo(order.getOrderNo());
            orderVO.setProductId(order.getProductId());
            orderVO.setProductCode(order.getProductCode());
            orderVO.setProductName(order.getProductName());
            orderVO.setProductSpec(order.getProductSpec());
            orderVO.setProductUnit(order.getProductUnit());
            orderVO.setPlannedQuantity(order.getPlannedQuantity());
            orderVO.setCompletedQuantity(order.getCompletedQuantity());
            orderVO.setOrderStatus(order.getOrderStatus());
            vo.setProductionOrder(orderVO);
            vo.setOrderNo(order.getOrderNo());
        }

        return vo;
    }

    /**
     * 将CreateDTO转换为实体
     */
    private static ProductionOperationExecution convertCreateDTOToEntity(ProductionOperationExecutionCreateDTO createDTO) {
        ProductionOperationExecution execution = new ProductionOperationExecution();

        execution.setOrderId(createDTO.getOrderId());
        execution.setProcessId(createDTO.getProcessId());
        execution.setProcessOrder(createDTO.getProcessOrder());
        execution.setPlannedStartTime(createDTO.getPlanStartTime());
        execution.setPlannedEndTime(createDTO.getPlanEndTime());
        execution.setOperatorId(createDTO.getOperatorId());
        execution.setOperatorName(createDTO.getOperatorName());
        execution.setEquipmentId(createDTO.getEquipmentId());
        execution.setEquipmentCode(createDTO.getEquipmentCode());
        execution.setEquipmentName(createDTO.getEquipmentName());
        execution.setInputQuantity(createDTO.getPlannedQuantity());

        return execution;
    }

    /**
     * 从UpdateDTO更新实体
     */
    private static void updateEntityFromUpdateDTO(ProductionOperationExecution execution, ProductionOperationExecutionUpdateDTO updateDTO) {
        if (updateDTO.getActualStartTime() != null) {
            execution.setActualStartTime(updateDTO.getActualStartTime());
        }
        if (updateDTO.getActualEndTime() != null) {
            execution.setActualEndTime(updateDTO.getActualEndTime());
        }
        if (updateDTO.getActualLaborHours() != null) {
            execution.setActualLaborHours(updateDTO.getActualLaborHours());
        }
        if (updateDTO.getActualMachineHours() != null) {
            execution.setActualMachineHours(updateDTO.getActualMachineHours());
        }
        if (updateDTO.getActualCompletedQuantity() != null) {
            execution.setOutputQuantity(updateDTO.getActualCompletedQuantity());
        }
        if (updateDTO.getActualQualifiedQuantity() != null) {
            execution.setQualifiedQuantity(updateDTO.getActualQualifiedQuantity());
        }
        if (updateDTO.getActualDefectiveQuantity() != null) {
            execution.setDefectiveQuantity(updateDTO.getActualDefectiveQuantity());
        }
        if (updateDTO.getOperatorId() != null) {
            execution.setOperatorId(updateDTO.getOperatorId());
        }
        if (updateDTO.getOperatorName() != null) {
            execution.setOperatorName(updateDTO.getOperatorName());
        }
        if (updateDTO.getEquipmentId() != null) {
            execution.setEquipmentId(updateDTO.getEquipmentId());
        }
        if (updateDTO.getEquipmentCode() != null) {
            execution.setEquipmentCode(updateDTO.getEquipmentCode());
        }
        if (updateDTO.getEquipmentName() != null) {
            execution.setEquipmentName(updateDTO.getEquipmentName());
        }
        if (updateDTO.getExecutionStatus() != null) {
            execution.setExecutionStatus(updateDTO.getExecutionStatus());
        }
        if (updateDTO.getRemark() != null) {
            execution.setDefectiveReason(updateDTO.getRemark());
        }

        execution.setUpdateTime(LocalDateTime.now());
    }

    /**
     * 获取状态描述
     */
    private static String getStatusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 0: return "待执行";
            case 2: return "进行中";
            case 3: return "已暂停";
            case 4: return "已完成";
            case 6: return "已取消";
            default: return "未知";
        }
    }
}
