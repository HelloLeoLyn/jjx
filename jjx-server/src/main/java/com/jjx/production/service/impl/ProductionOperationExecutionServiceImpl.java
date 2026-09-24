package com.jjx.production.service.impl;

import com.jjx.production.enums.ExecutionStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.ProductionOperationExecutionCreateDTO;
import com.jjx.production.domain.dto.ProductionOperationExecutionQueryDTO;
import com.jjx.production.domain.dto.ProductionOperationExecutionUpdateDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionEquipment;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.vo.ProductionOperationExecutionVO;
import com.jjx.production.domain.vo.ProductionOrderVO;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionEquipmentMapper;
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
    private final ProductionEquipmentMapper productionEquipmentMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final com.jjx.production.service.WorkReportProjectionService workReportProjectionService;
    /** P1：工序产生时同步创建 First ProductionTask（统一任务责任树） */
    private final com.jjx.production.service.ProductionTaskService productionTaskService;
    /** 开工设备首次绑定/换机履历。 */
    private final com.jjx.production.service.ProductionOperationRecordService productionOperationRecordService;
    /** 完工口径/阶段（新质检模型）—— dev-20260918-015 */
    private final com.jjx.quality.service.QualityLotService qualityLotService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createExecution(ProductionOperationExecutionCreateDTO createDTO) {
        log.info("创建工序执行记录: {}", createDTO);

        // 验证数据
        validateExecutionData(createDTO);

        // 转换为实体
        ProductionOperationExecution execution = convertCreateDTOToEntity(createDTO);
        execution.setExecutionStatus(ExecutionStatusEnum.PENDING.getValue()); // 默认状态为待执行

        // 保存到数据库
        boolean success = save(execution);
        if (!success) {
            throw new BusinessException("创建工序执行记录失败");
        }

        // P1：同一事务内创建 First ProductionTask（真实第一层任务，非 System Root）
        productionTaskService.createFirstTask(execution.getExecutionId(), execution.getInputQuantity());

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

        ProductionOperationExecutionVO vo = convertToVO(execution);
        // 补全工单号/工序名（P1：不再包含任何 Root/TaskNode 派工投影）
        enrichExecutionVOs(java.util.List.of(vo));
        return vo;
    }

    @Override
    public List<ProductionOperationExecutionVO> queryExecutionList(ProductionOperationExecutionQueryDTO queryDTO) {
        log.debug("查询工序执行列表: {}", queryDTO);

        LambdaQueryWrapper<ProductionOperationExecution> wrapper = buildQueryWrapper(queryDTO);
        wrapper.orderByDesc(ProductionOperationExecution::getCreateTime);

        List<ProductionOperationExecution> executions = list(wrapper);
        List<ProductionOperationExecutionVO> vos = executions.stream()
                .map(ProductionOperationExecutionServiceImpl::convertToVO)
                .collect(Collectors.toList());

        // 2026-08-11 修复：补全工单号/工序编码/工序名（convertToVO 只拷自身字段）
        enrichExecutionVOs(vos);
        // V1 Fix Pack FIX-2：排除 CANCELLED 工单的工序（历史保留，不进入生产操作任务）
        vos.removeIf(vo -> Boolean.TRUE.equals(isOrderCancelled(vo.getOrderId())));
        return vos;
    }

    /** 批量补全工单号、工序编码/名称 */
    /**
     * V1 Fix Pack FIX-2：判断订单是否 CANCELLED（批量查询缓存，避免 N+1）
     * 历史 CANCELLED 工单的 Execution 保留数据库记录，但默认不进入生产操作任务范围
     */
    private java.util.Map<Long, Boolean> orderCancelledCache = new java.util.concurrent.ConcurrentHashMap<>();

    private Boolean isOrderCancelled(Long orderId) {
        if (orderId == null) return false;
        return orderCancelledCache.computeIfAbsent(orderId, id -> {
            try {
                Integer cnt = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM production_order WHERE order_id = ? AND order_status = "
                                + com.jjx.production.enums.ProductionOrderStatusEnum.CANCELLED.getValue(),
                        Integer.class, id);
                return cnt != null && cnt > 0;
            } catch (Exception e) {
                log.warn("查询工单取消状态失败 orderId={}: {}", id, e.getMessage());
                return false;
            }
        });
    }

    private void enrichExecutionVOs(List<ProductionOperationExecutionVO> vos) {
        if (vos == null || vos.isEmpty()) return;
        try {
            java.util.Set<Long> executionIds = vos.stream()
                    .map(ProductionOperationExecutionVO::getExecutionId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            java.util.Map<Long, BigDecimal> pendingApprovalMap = new java.util.HashMap<>();
            if (!executionIds.isEmpty()) {
                String executionIdStr = executionIds.stream().map(String::valueOf)
                        .collect(Collectors.joining(","));
                jdbcTemplate.query("SELECT execution_id,"
                                + " COALESCE(SUM(qualified_quantity + defective_quantity),0) pending_quantity"
                                + " FROM production_work_report WHERE execution_id IN (" + executionIdStr + ")"
                                + " AND report_status=? GROUP BY execution_id",
                        (org.springframework.jdbc.core.RowCallbackHandler) rs -> pendingApprovalMap.put(
                                rs.getLong("execution_id"), rs.getBigDecimal("pending_quantity")),
                        com.jjx.production.enums.WorkReportStatusEnum.PENDING.getCode());
            }
            vos.forEach(vo -> vo.setPendingApprovalQuantity(
                    pendingApprovalMap.getOrDefault(vo.getExecutionId(), BigDecimal.ZERO)));

            // 工单号
            java.util.Set<Long> orderIds = vos.stream()
                    .map(ProductionOperationExecutionVO::getOrderId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!orderIds.isEmpty()) {
                String orderIdStr = orderIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                java.util.Map<Long, String> orderNoMap = new java.util.HashMap<>();
                try {
                    jdbcTemplate.query("SELECT order_id, order_no FROM production_order WHERE order_id IN (" + orderIdStr + ")",
                            rs -> {
                                orderNoMap.put(rs.getLong("order_id"), rs.getString("order_no"));
                            });
                } catch (Exception e) {
                    log.warn("查询工单号失败: {}", e.getMessage());
                }
                for (ProductionOperationExecutionVO vo : vos) {
                    if (vo.getOrderId() != null) vo.setOrderNo(orderNoMap.get(vo.getOrderId()));
                }
            }
            // 工序编码/名称
            java.util.Set<Long> processIds = vos.stream()
                    .map(ProductionOperationExecutionVO::getProcessId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!processIds.isEmpty()) {
                String pidStr = processIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                java.util.Map<Long, String[]> processMap = new java.util.HashMap<>();
                try {
                    jdbcTemplate.query("SELECT process_id, process_code, process_name, icon, has_index FROM engineering_standard_process WHERE process_id IN (" + pidStr + ")",
                            rs -> {
                                processMap.put(rs.getLong("process_id"),
                                        new String[]{rs.getString("process_code"), rs.getString("process_name"),
                                                rs.getString("icon"), String.valueOf(rs.getInt("has_index"))});
                            });
                } catch (Exception e) {
                    log.warn("查询工序信息失败: {}", e.getMessage());
                }
                for (ProductionOperationExecutionVO vo : vos) {
                    if (vo.getProcessId() != null) {
                        String[] info = processMap.get(vo.getProcessId());
                        if (info != null) {
                            vo.setProcessCode(info[0]);
                            vo.setProcessName(info[1]);
                            vo.setIcon(info[2]);
                            try {
                                vo.setHasIndex(Integer.valueOf(info[3]));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            // 2026-09-09 完工按钮权限（Leo 定）：仅 EXECUTING 且当前用户=该工序根任务负责人（一级负责人）或超管可完工
            if (!executionIds.isEmpty()) {
                String execIdStr = executionIds.stream().map(String::valueOf)
                        .collect(Collectors.joining(","));
                java.util.Map<Long, Long> rootAssigneeMap = new java.util.HashMap<>();
                try {
                    jdbcTemplate.query("SELECT execution_id, assignee_id FROM production_task "
                                    + "WHERE execution_id IN (" + execIdStr + ") AND parent_task_id IS NULL",
                            (org.springframework.jdbc.core.RowCallbackHandler) rs -> rootAssigneeMap.put(rs.getLong("execution_id"),
                                    rs.getObject("assignee_id") == null ? null : rs.getLong("assignee_id")));
                } catch (Exception e) {
                    log.warn("查询根任务负责人失败: {}", e.getMessage());
                }
                Long loginUserId = com.jjx.system.utils.SecurityUtils.getUserId();
                boolean isSuperAdmin = com.jjx.system.utils.SecurityUtils.hasRole("admin");
                Integer executingValue = ExecutionStatusEnum.EXECUTING.getValue();
                for (ProductionOperationExecutionVO vo : vos) {
                    Long owner = rootAssigneeMap.get(vo.getExecutionId());
                    vo.setCanComplete(executingValue.equals(vo.getExecutionStatus())
                            && ((owner != null && owner.equals(loginUserId)) || isSuperAdmin));
                }
            }
        } catch (Exception e) {


            log.warn("补全工序执行展示信息失败: {}", e.getMessage());
        }
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
        // V1 Fix Pack FIX-4：分页列表同样补全工序名/工单号（原缺失导致 processName 显示"-"）
        enrichExecutionVOs(voList);
        // V1 Fix Pack FIX-2：排除 CANCELLED 工单的工序（历史保留，不进入生产操作任务）
        voList.removeIf(vo -> Boolean.TRUE.equals(isOrderCancelled(vo.getOrderId())));
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startExecution(Long executionId) {
        return startExecution(executionId, null, null, false);
    }

    /**
     * 开始或恢复工序执行（扫码C：支持可选设备码软校验）
     *
     * @param executionId   工序执行ID
     * @param scannedDeviceCode 扫码设备码（可空；空=跳过校验，兼容旧调用）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startExecution(Long executionId, String scannedDeviceCode) {
        return startExecution(executionId, null, scannedDeviceCode, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startExecution(Long executionId, Long equipmentId, String scannedDeviceCode,
                                  boolean confirmEquipmentChange) {
        log.info("开始工序执行: {}, scannedDeviceCode={}", executionId, scannedDeviceCode);

        ProductionOperationExecution execution = getById(executionId);
        if (execution == null) {
            throw new BusinessException("工序执行记录不存在: " + executionId);
        }

        // 检查记录状态是否可以开始或继续
        if (!canStartExecution(execution)) {
            throw new BusinessException("仅待开始或已暂停的工序可开始/继续");
        }

        ProductionOrder order = productionOrderMapper.selectById(execution.getOrderId());
        if (order == null) {
            throw new BusinessException("所属生产工单不存在: " + execution.getOrderId());
        }
        if (!com.jjx.production.enums.ProductionOrderStatusEnum.IN_PROGRESS.getValue().equals(order.getOrderStatus())) {
            com.jjx.production.enums.ProductionOrderStatusEnum current =
                    com.jjx.production.enums.ProductionOrderStatusEnum.getByValue(order.getOrderStatus());
            throw new BusinessException("请先启动生产工单，再开始工序（当前工单状态："
                    + (current == null ? String.valueOf(order.getOrderStatus()) : current.getLabel()) + "）");
        }

        bindStartEquipment(execution, equipmentId, scannedDeviceCode, confirmEquipmentChange);

        // 恢复时保留首次实际开始时间。当前字段模型没有暂停时长分段，
        // 完工后仍沿用 actualStartTime 到 actualEndTime 的现有工时口径。
        execution.setExecutionStatus(ExecutionStatusEnum.EXECUTING.getValue());
        if (execution.getActualStartTime() == null) {
            execution.setActualStartTime(LocalDateTime.now());
        }

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
        execution.setExecutionStatus(ExecutionStatusEnum.PAUSED.getValue());

        boolean success = updateById(execution);
        if (!success) {
            throw new BusinessException("暂停工序执行失败");
        }

        log.info("工序执行暂停成功, ID: {}", executionId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean qualityCheck(Long executionId, String checkType, String checkResult, String checkItems, String remark) {
        log.info("工序{}: executionId={}", "首检".equals(checkType) ? "首检" : "巡检", executionId);

        ProductionOperationExecution execution = getById(executionId);
        if (execution == null) {
            throw new BusinessException("工序执行记录不存在: " + executionId);
        }
        // 只有执行中可质检
        if (execution.getExecutionStatus() == null
                || execution.getExecutionStatus() != ExecutionStatusEnum.EXECUTING.getValue()) {
            throw new BusinessException("只有执行中的工序可以进行首检/巡检");
        }
        if (!"FIRST".equalsIgnoreCase(checkType) && !"PATROL".equalsIgnoreCase(checkType)) {
            throw new BusinessException("质检类型不合法(FIRST首检/PATROL巡检)");
        }
        if (checkResult == null || (!"PASS".equalsIgnoreCase(checkResult) && !"FAIL".equalsIgnoreCase(checkResult))) {
            throw new BusinessException("质检结论不合法(PASS/FAIL)");
        }

        // 记录质检结果到 quality_check_result(JSON数组追加)
        String checkNo = "EXEC" + executionId + "-" + ("FIRST".equalsIgnoreCase(checkType) ? "F" : "P")
                + System.currentTimeMillis() % 100000;
        java.util.Map<String, Object> record = new java.util.LinkedHashMap<>();
        record.put("checkNo", checkNo);
        record.put("checkType", checkType);
        record.put("checkResult", checkResult);
        record.put("checkItems", checkItems);
        record.put("remark", remark);
        record.put("checker", com.jjx.system.utils.SecurityUtils.getUsername());
        record.put("checkTime", java.time.LocalDateTime.now().toString());

        String existing = execution.getQualityCheckResult();
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        if (existing != null && !existing.isEmpty()) {
            try {
                list = new com.fasterxml.jackson.databind.ObjectMapper().readValue(existing,
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
            } catch (Exception e) {
                log.warn("解析历史质检结果失败: {}", e.getMessage());
                list = new java.util.ArrayList<>();
            }
        }
        list.add(record);
        try {
            execution.setQualityCheckResult(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(list));
        } catch (Exception e) {
            throw new BusinessException("质检结果序列化失败");
        }
        updateById(execution);

        // 不合格 → 自动暂停工序
        if ("FAIL".equalsIgnoreCase(checkResult)) {
            execution.setExecutionStatus(ExecutionStatusEnum.PAUSED.getValue());
            updateById(execution);
            log.warn("工序[{}] {}不合格，已自动暂停", executionId, checkType);
        }

        log.info("工序[{}] {}完成: {} ({})", executionId, checkType, checkResult, checkNo);
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
        if (!ExecutionStatusEnum.EXECUTING.getValue().equals(execution.getExecutionStatus())) {
            throw new BusinessException("只有执行中的工序可以完成");
        }

        // 2026-09-09 完工权限（Leo 定）：仅该工序一级负责人（根任务负责人，production:all 下具体某人）或超级管理员可完工
        Long userId = com.jjx.system.utils.SecurityUtils.getUserId();
        Long rootAssigneeId = productionTaskService.getRootAssigneeId(executionId);
        boolean isSuperAdmin = com.jjx.system.utils.SecurityUtils.hasRole("admin");
        boolean isRootOwner = rootAssigneeId != null && rootAssigneeId.equals(userId);
        if (!isRootOwner && !isSuperAdmin) {
            throw new BusinessException(rootAssigneeId == null
                    ? "该工序暂无一级负责人，仅超级管理员可完成工序"
                    : "仅该工序一级负责人（或超级管理员）可完成工序，请由负责人在界面点击完工");
        }

        // 2026-09-09 完成链简化：整棵子树就绪（无待审/达标/无剩余/无未完成责任）后，由完工动作一并收口根任务（幂等）
        productionTaskService.assertExecutionCompletable(executionId);
        productionTaskService.completeRootForExecution(executionId);

        LocalDateTime completedAt = LocalDateTime.now();
        boolean success = update(Wrappers.<ProductionOperationExecution>lambdaUpdate()
                .eq(ProductionOperationExecution::getExecutionId, executionId)
                .eq(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.EXECUTING.getValue())
                .set(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.COMPLETED.getValue())
                .set(ProductionOperationExecution::getActualEndTime, completedAt));
        if (!success) {
            throw new BusinessException("工序状态已变更，请刷新后重试");
        }

        long unfinishedOtherExecutions = count(Wrappers.<ProductionOperationExecution>lambdaQuery()
                .eq(ProductionOperationExecution::getOrderId, execution.getOrderId())
                .ne(ProductionOperationExecution::getExecutionId, executionId)
                .notIn(ProductionOperationExecution::getExecutionStatus,
                        ExecutionStatusEnum.COMPLETED.getValue(),
                        ExecutionStatusEnum.SKIPPED.getValue(),
                        ExecutionStatusEnum.CANCELLED.getValue()));
        // 旧 FQC 建单已下线（dev-20260918-017/018）：成品检验走 quality_lot（末道报工审批即建批）

        // 2026-09-09 口径Y（Leo 定）：工单成品数量（completed/finished/remaining）不再在工序完工时写入，
        // 统一由 FQC 检验批判定 PASS 写入（成品=质检通过数）。
        // 此前 updateOrderCompletedQuantity 把“Σ 各工序合格数”当完成量，多工序工单被重复累加（如 5×100=500），
        // 且 remaining=计划-Σ 出现负数——该写点已删除。

        log.info("工序执行完成成功, ID: {}, 是否最后有效工序: {}", executionId, unfinishedOtherExecutions == 0);
        return true;
    }

    /** 未终态工序（非 已完成/已跳过/已取消） */
    private List<ProductionOperationExecution> listOpenExecutions(Long orderId) {
        return list(Wrappers.<ProductionOperationExecution>lambdaQuery()
                .eq(ProductionOperationExecution::getOrderId, orderId)
                .notIn(ProductionOperationExecution::getExecutionStatus,
                        ExecutionStatusEnum.COMPLETED.getValue(),
                        ExecutionStatusEnum.SKIPPED.getValue(),
                        ExecutionStatusEnum.CANCELLED.getValue())
                .orderByAsc(ProductionOperationExecution::getProcessOrder));
    }

    private static String processLabel(ProductionOperationExecution exec) {
        String name = exec.getProcessName();
        return (name == null || name.isBlank()) ? ("工序" + exec.getProcessOrder()) : name;
    }

    private static String statusText(Integer status) {
        ExecutionStatusEnum e = ExecutionStatusEnum.getByValue(status);
        return e == null ? String.valueOf(status) : e.getLabel();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeOrderExecutions(Long orderId) {
        log.info("工单统一收口: orderId={}", orderId);
        if (orderId == null) {
            throw new BusinessException("工单ID不能为空");
        }
        ProductionOrder order = productionOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("工单不存在: " + orderId);
        }
        List<ProductionOperationExecution> open = listOpenExecutions(orderId);
        if (open.isEmpty()) {
            throw new BusinessException("该工单没有待完成的工序，无需重复收口");
        }

        Long userId = com.jjx.system.utils.SecurityUtils.getUserId();
        boolean isSuperAdmin = com.jjx.system.utils.SecurityUtils.hasRole("admin");

        // ① 权限（该工单全部待完工工序的根任务负责人本人） + ② 逐工序前置，一次聚合全部阻断项
        List<String> blockers = new java.util.ArrayList<>();
        for (ProductionOperationExecution exec : open) {
            String label = processLabel(exec);
            Long rootAssigneeId = productionTaskService.getRootAssigneeId(exec.getExecutionId());
            boolean isRootOwner = rootAssigneeId != null && rootAssigneeId.equals(userId);
            if (!isRootOwner && !isSuperAdmin) {
                throw new BusinessException(rootAssigneeId == null
                        ? "工序[" + label + "]暂无一级负责人，仅超级管理员可完成"
                        : "仅该工单一级负责人（或超级管理员）可收口，请由负责人操作");
            }
            if (!ExecutionStatusEnum.EXECUTING.getValue().equals(exec.getExecutionStatus())) {
                blockers.add(label + "：当前状态为" + statusText(exec.getExecutionStatus()) + "，不能完成");
                continue;
            }
            for (String b : productionTaskService.executionCompletionBlockers(exec.getExecutionId())) {
                blockers.add(label + "：" + b);
            }
        }
        if (!blockers.isEmpty()) {
            throw new BusinessException("工单暂不能完成：\n✗ " + String.join("\n✗ ", blockers));
        }

        // 逐条收口（先根任务，再工序状态；任一失败事务回滚=整体拒绝）
        LocalDateTime completedAt = LocalDateTime.now();
        for (ProductionOperationExecution exec : open) {
            productionTaskService.completeRootForExecution(exec.getExecutionId());
            boolean success = update(Wrappers.<ProductionOperationExecution>lambdaUpdate()
                    .eq(ProductionOperationExecution::getExecutionId, exec.getExecutionId())
                    .eq(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.EXECUTING.getValue())
                    .set(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.COMPLETED.getValue())
                    .set(ProductionOperationExecution::getActualEndTime, completedAt));
            if (!success) {
                throw new BusinessException("工序[" + processLabel(exec) + "]状态已变更，请刷新后重试");
            }
        }
        log.info("工单统一收口完成: orderId={}, 工序数={}（成品检验走 quality_lot：末道报工审批即建批）", orderId, open.size());
        return true;
    }

    @Override
    public List<com.jjx.production.domain.vo.OrderCompletionStatusVO> getOrderCompletionStatus(List<Long> orderIds) {
        List<com.jjx.production.domain.vo.OrderCompletionStatusVO> result = new java.util.ArrayList<>();
        if (orderIds == null || orderIds.isEmpty()) {
            return result;
        }
        List<Long> ids = orderIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return result;
        }
        Long userId = com.jjx.system.utils.SecurityUtils.getUserId();
        boolean isSuperAdmin = com.jjx.system.utils.SecurityUtils.hasRole("admin");
        String orderIdStr = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        java.util.Map<Long, ComStatusRow> rows = new java.util.HashMap<>();
        try {
            jdbcTemplate.query("SELECT e.order_id AS order_id, COUNT(*) AS open_cnt, "
                            + "SUM(CASE WHEN t.task_id IS NOT NULL AND t.assignee_id = " + (userId == null ? -1L : userId)
                            + " THEN 1 ELSE 0 END) AS mine_cnt, "
                            + "SUM(CASE WHEN t.task_id IS NOT NULL THEN 1 ELSE 0 END) AS root_cnt "
                            + "FROM production_operation_execution e "
                            + "LEFT JOIN production_task t ON t.execution_id = e.execution_id AND t.parent_task_id IS NULL "
                            + "WHERE e.order_id IN (" + orderIdStr + ") "
                            + "AND e.execution_status NOT IN (" + ExecutionStatusEnum.COMPLETED.getValue() + ","
                            + ExecutionStatusEnum.SKIPPED.getValue() + "," + ExecutionStatusEnum.CANCELLED.getValue() + ") "
                            + "GROUP BY e.order_id",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> rows.put(rs.getLong("order_id"),
                            new ComStatusRow(rs.getInt("open_cnt"), rs.getInt("mine_cnt"), rs.getInt("root_cnt"))));
        } catch (Exception e) {
            log.warn("查询工单收口状态失败: {}", e.getMessage());
        }
        // 工序进度（总数/已完成，不含已取消）—— dev-20260918-015
        java.util.Map<Long, int[]> progress = new java.util.HashMap<>();
        try {
            jdbcTemplate.query("SELECT e.order_id AS order_id, COUNT(*) AS total_cnt, "
                            + "SUM(CASE WHEN e.execution_status IN (" + ExecutionStatusEnum.COMPLETED.getValue() + ","
                            + ExecutionStatusEnum.SKIPPED.getValue() + ") THEN 1 ELSE 0 END) AS done_cnt "
                            + "FROM production_operation_execution e "
                            + "WHERE e.order_id IN (" + orderIdStr + ") "
                            + "AND e.execution_status <> " + ExecutionStatusEnum.CANCELLED.getValue() + " "
                            + "GROUP BY e.order_id",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> progress.put(rs.getLong("order_id"),
                            new int[]{rs.getInt("total_cnt"), rs.getInt("done_cnt")}));
        } catch (Exception e) {
            log.warn("查询工单工序进度失败: {}", e.getMessage());
        }
        for (Long id : ids) {
            ComStatusRow row = rows.get(id);
            com.jjx.production.domain.vo.OrderCompletionStatusVO vo = new com.jjx.production.domain.vo.OrderCompletionStatusVO();
            vo.setOrderId(id);
            ProductionOrder order = productionOrderMapper.selectById(id);
            vo.setOrderNo(order == null ? null : order.getOrderNo());
            int openCnt = row == null ? 0 : row.openCnt;
            int rootCnt = row == null ? 0 : row.rootCnt;
            int mineCnt = row == null ? 0 : row.mineCnt;
            vo.setPendingExecutionCount(openCnt);
            vo.setAuthorized(isSuperAdmin || (rootCnt > 0 && mineCnt == rootCnt));
            vo.setCanComplete(vo.isAuthorized() && openCnt > 0);
            int[] prog = progress.get(id);
            fillStage(vo, order, prog == null ? 0 : prog[0], prog == null ? 0 : prog[1]);
            result.add(vo);
        }
        return result;
    }

    /** jdbcTemplate 结果行载体 */
    private record ComStatusRow(int openCnt, int mineCnt, int rootCnt) {
    }

    /**
     * 派生「完工阶段」—— dev-20260918-015（四个状态机投影，不落库）。
     * 工序完成 ≠ 工单完成：工序全完成后阶段应翻成「待完工检验」而不是仍显示「进行中」。
     * dev-20260923-028：判定逻辑抽到 {@link OrderCompletionStageResolver}（可单测），并新增「待补产」：
     * 报废已处置、良品累计未达计划时，明确告诉责任人还缺几件、去哪补产（原来误报成"继续检验/补检"）。
     */
    private void fillStage(com.jjx.production.domain.vo.OrderCompletionStatusVO vo, ProductionOrder order,
                           int total, int done) {
        java.math.BigDecimal zero = java.math.BigDecimal.ZERO;
        vo.setQualifiedQuantity(zero);
        vo.setPlannedQuantity(zero);
        vo.setUndisposedFailQuantity(zero);
        vo.setScrappedQuantity(zero);
        vo.setShortfallQuantity(zero);
        if (order == null) {
            setStage(vo, OrderCompletionStageResolver.UNKNOWN, "未知", "工单不存在");
            return;
        }
        java.math.BigDecimal planned = order.getPlannedQuantity() == null ? zero : order.getPlannedQuantity();
        vo.setPlannedQuantity(planned);
        boolean inboundPending = order.getInboundPendingFlag() != null && order.getInboundPendingFlag() == 1;
        Integer status = order.getOrderStatus();
        boolean reachedFqcStage = status != null
                && com.jjx.production.enums.ProductionOrderStatusEnum.IN_PROGRESS.getValue().equals(status)
                && total > 0 && done >= total;

        com.jjx.quality.dto.FqcCompletionSummary fqc = null;
        if (reachedFqcStage) {
            fqc = qualityLotService.summarizeEffectiveFqc(order.getOrderId());
            vo.setFqcPendingCount(fqc.getPendingCount());
            vo.setQualifiedQuantity(fqc.getQualifiedTotal());
            vo.setUndisposedFailQuantity(fqc.getUndisposedFailQuantity());
            vo.setScrappedQuantity(fqc.getScrappedTotal());
        }

        OrderCompletionStageResolver.Result result = OrderCompletionStageResolver.resolve(
                new OrderCompletionStageResolver.Input(
                        status, inboundPending, total, done,
                        fqc != null && fqc.isHasLot(),
                        fqc == null ? 0 : fqc.getPendingCount(),
                        fqc == null ? zero : fqc.getQualifiedTotal(),
                        fqc == null ? zero : fqc.getUndisposedFailQuantity(),
                        fqc == null ? zero : fqc.getScrappedTotal(),
                        planned));
        vo.setShortfallQuantity(result.shortfallQuantity());
        fillReconciliation(vo, order.getOrderId(), planned);
        setStage(vo, result.stage(), result.label(), result.nextAction());
    }

    /**
     * 数量对账栏（dev-20260923-024）：计划 / 已报工(投入) / 良品 / 报废 / 返工在制 / 让步 / 在制 / 差数。
     *
     * <p>口径（045 §1 术语表）：工单「完成」= 良品累计；差数 = max(0, 计划 − 良品)，必须由
     * 补产 / 返工回收 / 让步 三者之一填平，否则不允许关闭工单。全部实时汇总，不落冗余列。</p>
     *
     * <p>说明：良品/报废 与 VO 既有字段 qualifiedQuantity/scrappedQuantity **同定义**
     * （有效 FQC 批 pass 合计 / SCRAP DONE 合计），此处独立汇总是为了让对账栏在任何阶段都可读
     * （既有字段受「已到 FQC 阶段」门控，仅用于阶段判定）。</p>
     */
    private void fillReconciliation(com.jjx.production.domain.vo.OrderCompletionStatusVO vo, Long orderId,
                                    java.math.BigDecimal planned) {
        java.math.BigDecimal zero = java.math.BigDecimal.ZERO;
        if (orderId == null) {
            return;
        }
        java.math.BigDecimal reported = zero;
        java.math.BigDecimal good = zero;
        java.math.BigDecimal scrap = zero;
        java.math.BigDecimal reworkWip = zero;
        java.math.BigDecimal concession = zero;
        java.math.BigDecimal inspected = zero;
        try {
            reported = nz(jdbcTemplate.queryForObject(
                    "SELECT IFNULL(SUM(IFNULL(qualified_quantity,0) + IFNULL(defective_quantity,0)),0)"
                            + " FROM production_work_report WHERE order_id = ? AND report_status = 'APPROVED'",
                    java.math.BigDecimal.class, orderId));
            good = nz(jdbcTemplate.queryForObject(
                    "SELECT IFNULL(SUM(l.pass_quantity),0) FROM quality_lot l"
                            + " WHERE l.order_id = ? AND l.lot_type = 'FQC' AND l.del_flag = 0"
                            + " AND NOT EXISTS (SELECT 1 FROM quality_lot c WHERE c.parent_lot_id = l.lot_id AND c.del_flag = 0)",
                    java.math.BigDecimal.class, orderId));
            inspected = nz(jdbcTemplate.queryForObject(
                    "SELECT IFNULL(SUM(IFNULL(l.inspected_quantity,0)),0) FROM quality_lot l"
                            + " WHERE l.order_id = ? AND l.lot_type = 'FQC' AND l.del_flag = 0",
                    java.math.BigDecimal.class, orderId));
            scrap = nz(jdbcTemplate.queryForObject(
                    "SELECT IFNULL(SUM(a.quantity),0) FROM quality_ncr_action a JOIN quality_ncr n ON n.ncr_id = a.ncr_id"
                            + " WHERE n.order_id = ? AND n.del_flag = 0 AND a.del_flag = 0"
                            + " AND a.action_type = 'SCRAP' AND a.status = 'DONE'",
                    java.math.BigDecimal.class, orderId));
            reworkWip = nz(jdbcTemplate.queryForObject(
                    "SELECT IFNULL(SUM(a.quantity),0) FROM quality_ncr_action a JOIN quality_ncr n ON n.ncr_id = a.ncr_id"
                            + " WHERE n.order_id = ? AND n.del_flag = 0 AND a.del_flag = 0"
                            + " AND a.action_type = 'REWORK' AND a.status IN ('PENDING','PROCESSING')",
                    java.math.BigDecimal.class, orderId));
            concession = nz(jdbcTemplate.queryForObject(
                    "SELECT IFNULL(SUM(a.quantity),0) FROM quality_ncr_action a JOIN quality_ncr n ON n.ncr_id = a.ncr_id"
                            + " WHERE n.order_id = ? AND n.del_flag = 0 AND a.del_flag = 0"
                            + " AND a.action_type = 'CONCESSION' AND a.status = 'DONE'",
                    java.math.BigDecimal.class, orderId));
        } catch (Exception e) {
            log.warn("数量对账栏汇总失败（降级为 0，不影响阶段判定）: orderId={} err={}", orderId, e.getMessage());
        }
        vo.setReportedQuantity(reported);
        vo.setGoodQuantity(good);
        vo.setScrapQuantity(scrap);
        vo.setReworkWipQuantity(reworkWip);
        vo.setConcessionQuantity(concession);
        vo.setWipQuantity(reported.subtract(inspected).max(zero));
        vo.setDiffQuantity((planned == null ? zero : planned).subtract(good).max(zero));
        // dev-20260923-026 / -027：物料侧（BOM应领 / 已领 / 补料 / 退料 / 超领率）
        fillMaterialReconciliation(vo, orderId, planned);
    }

    /**
     * 物料侧对账（dev-20260923-026 / -027）：BOM 应领 / 已领 / 补料 / 退料 / 超领率。
     *
     * <p>口径：BOM 定额（单耗 ×(1+损耗率) × 计划量）是**基准不是天花板** —— 正常领料 ≤ 剩余定额，
     * 超出部分只能走补料通道（原因 + 授权 + 留痕）；超领率用来考核。</p>
     */
    private void fillMaterialReconciliation(com.jjx.production.domain.vo.OrderCompletionStatusVO vo, Long orderId,
                                            java.math.BigDecimal planned) {
        java.math.BigDecimal zero = java.math.BigDecimal.ZERO;
        java.math.BigDecimal required = zero;
        java.math.BigDecimal issued = zero;
        java.math.BigDecimal supplement = zero;
        java.math.BigDecimal returned = zero;
        try {
            required = nz(jdbcTemplate.queryForObject(
                    // 损耗率口径：loss_rate 存百分数（5 = 5%）→ 单耗 ×(1 + loss_rate/100)，与
                    // EngineeringBomServiceImpl:680 / OrderMaterialReserveServiceImpl:266 / InventoryOutboundServiceImpl:1150 一致
                    "SELECT IFNULL(SUM(i.quantity * (1 + IFNULL(i.loss_rate, 0) / 100)), 0) * IFNULL(o.planned_quantity, 0)"
                            + " FROM production_order o LEFT JOIN engineering_bom_item i ON i.bom_id = o.bom_id"
                            + " WHERE o.order_id = ?",
                    java.math.BigDecimal.class, orderId));
            issued = nz(jdbcTemplate.queryForObject(
                    "SELECT IFNULL(SUM(ii.quantity), 0) FROM inventory_outbound_item ii"
                            + " JOIN inventory_outbound_order o ON o.outbound_id = ii.outbound_id"
                            + " WHERE o.order_status <> 9 AND o.source_type = 'work_order' AND o.source_id = ?"
                            + "   AND o.supplement_reason_type IS NULL",
                    java.math.BigDecimal.class, orderId));
            supplement = nz(jdbcTemplate.queryForObject(
                    "SELECT IFNULL(SUM(ii.quantity), 0) FROM inventory_outbound_item ii"
                            + " JOIN inventory_outbound_order o ON o.outbound_id = ii.outbound_id"
                            + " WHERE o.order_status <> 9 AND o.source_type = 'work_order' AND o.source_id = ?"
                            + "   AND o.supplement_reason_type IS NOT NULL",
                    java.math.BigDecimal.class, orderId));
            returned = nz(jdbcTemplate.queryForObject(
                    "SELECT IFNULL(SUM(ii.quantity), 0) FROM inventory_inbound_item ii"
                            + " JOIN inventory_inbound_order o ON o.inbound_id = ii.inbound_id"
                            + " WHERE o.order_status <> 9 AND o.inbound_type = 'PRODUCTION_RETURN'"
                            + "   AND o.source_id IN (SELECT ncr_id FROM quality_ncr WHERE order_id = ?)",
                    java.math.BigDecimal.class, orderId));
        } catch (Exception e) {
            log.warn("物料侧对账汇总失败（降级为 0）: orderId={} err={}", orderId, e.getMessage());
        }
        vo.setMaterialRequired(required);
        vo.setMaterialIssued(issued);
        vo.setMaterialSupplement(supplement);
        vo.setMaterialReturned(returned);
        vo.setOverPickRate(required.signum() > 0
                ? issued.add(supplement).subtract(required).max(zero)
                        .multiply(new java.math.BigDecimal("100"))
                        .divide(required, 2, java.math.RoundingMode.HALF_UP)
                : zero);
    }

    private static java.math.BigDecimal nz(java.math.BigDecimal value) {
        return value == null ? java.math.BigDecimal.ZERO : value;
    }

    private void setStage(com.jjx.production.domain.vo.OrderCompletionStatusVO vo,
                          String stage, String label, String nextAction) {
        vo.setStage(stage);
        vo.setStageLabel(label);
        vo.setNextAction(nextAction);
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
        execution.setExecutionStatus(ExecutionStatusEnum.CANCELLED.getValue());

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
                execution.setExecutionStatus(ExecutionStatusEnum.PENDING.getValue());
                save(execution);

                // P1：同一事务内创建 First ProductionTask（导入路径与单条创建保持一致）
                productionTaskService.createFirstTask(execution.getExecutionId(), execution.getInputQuantity());

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
        wrapper.eq(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.PENDING.getValue());
        long pendingCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.EXECUTING.getValue());
        long inProgressCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.COMPLETED.getValue());
        long completedCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOperationExecution::getExecutionStatus, ExecutionStatusEnum.CANCELLED.getValue());
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
     * 2026-09-09 已删除：updateOrderCompletedQuantity（052 口径）——
     * 原逻辑把“Σ 各已完成工序合格数”写入 completedQuantity，多工序工单被重复累加（5×100=500），
     * remainingQuantity=计划-Σ 出现负数（100-500=-400），与工卡“计划/完成/剩余”展示严重背离。
     * 成品口径已收敛（口径Y，Leo 定）：工单 completed/finished/remaining 统一由
     * FQC 检验批判定 PASS 写入（成品=质检通过数 passQty）。
     */

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
        if (queryDTO.getOperatorName() != null && !queryDTO.getOperatorName().isEmpty()) {
            if ("当前用户".equals(queryDTO.getOperatorName())) {
                // 2026-08-11 修复：前端"我的任务"传"当前用户"魔数，解析为当前登录用户名
                try {
                    String currentUser = com.jjx.system.utils.SecurityUtils.getUsername();
                    if (currentUser != null) {
                        wrapper.eq(ProductionOperationExecution::getOperatorName, currentUser);
                    }
                } catch (Exception e) {
                    log.warn("解析当前用户失败(不按操作员过滤): {}", e.getMessage());
                }
            } else {
                wrapper.like(ProductionOperationExecution::getOperatorName, queryDTO.getOperatorName());
            }
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
        return ExecutionStatusEnum.PENDING.getValue().equals(status) || ExecutionStatusEnum.CANCELLED.getValue().equals(status);
    }

    /**
     * 检查记录是否可以开始或继续
     */
    private static boolean canStartExecution(ProductionOperationExecution execution) {
        Integer status = execution.getExecutionStatus();
        return ExecutionStatusEnum.PENDING.getValue().equals(status)
                || ExecutionStatusEnum.PAUSED.getValue().equals(status);
    }

    private void bindStartEquipment(ProductionOperationExecution execution, Long equipmentId,
                                    String scannedDeviceCode, boolean confirmChange) {
        ProductionEquipment selected = resolveEquipment(equipmentId, scannedDeviceCode);
        if (selected == null && execution.getEquipmentId() != null) {
            selected = resolveEquipment(execution.getEquipmentId(), null); // 恢复沿用原设备并复核状态。
        }
        if (selected == null) return; // 未绑定设备的手工工序允许直接开工。

        com.jjx.production.enums.EquipmentStatusEnum status =
                com.jjx.production.enums.EquipmentStatusEnum.getByValue(selected.getStatus());
        if (status == null || !status.isAvailable()) {
            throw new BusinessException("设备不可用于开工：" + selected.getEquipmentName()
                    + "（" + (status == null ? "未知状态" : status.getLabel()) + "）");
        }

        Long oldId = execution.getEquipmentId();
        boolean changing = oldId != null && !oldId.equals(selected.getEquipmentId());
        if (changing && !confirmChange) {
            throw new BusinessException("工序已绑定设备 " + execution.getEquipmentName()
                    + "，更换为 " + selected.getEquipmentName() + " 需要确认");
        }
        if (equipmentId != null && scannedDeviceCode != null && !scannedDeviceCode.isBlank()
                && !selected.getEquipmentNo().equals(scannedDeviceCode.trim())) {
            throw new BusinessException("所选设备与扫码设备不一致");
        }

        String oldDisplay = execution.getEquipmentName() == null ? "未绑定"
                : execution.getEquipmentName() + "（" + execution.getEquipmentCode() + "）";
        execution.setEquipmentId(selected.getEquipmentId());
        execution.setEquipmentCode(selected.getEquipmentNo());
        execution.setEquipmentName(selected.getEquipmentName());

        if (oldId == null || changing) {
            com.jjx.production.domain.dto.ProductionOperationRecordCreateDTO record =
                    new com.jjx.production.domain.dto.ProductionOperationRecordCreateDTO();
            record.setExecutionId(execution.getExecutionId());
            record.setRecordType(com.jjx.production.enums.RecordTypeEnum.EQUIPMENT.getCode());
            record.setRecordTime(LocalDateTime.now());
            try {
                record.setOperatorId(com.jjx.system.utils.SecurityUtils.getUserId());
                record.setOperatorName(com.jjx.system.utils.SecurityUtils.getUsername());
            } catch (Exception ignored) {
                record.setOperatorName("system");
            }
            record.setRemark((changing ? "开工换机：" + oldDisplay + " → " : "首次开工绑定设备：")
                    + selected.getEquipmentName() + "（" + selected.getEquipmentNo() + "）");
            productionOperationRecordService.createRecord(record);
        }
    }

    private ProductionEquipment resolveEquipment(Long equipmentId, String scannedDeviceCode) {
        if (equipmentId == null && (scannedDeviceCode == null || scannedDeviceCode.isBlank())) return null;
        ProductionEquipment equipment;
        if (equipmentId != null) {
            equipment = productionEquipmentMapper.selectById(equipmentId);
        } else {
            equipment = productionEquipmentMapper.selectOne(Wrappers.<ProductionEquipment>lambdaQuery()
                    .eq(ProductionEquipment::getEquipmentNo, scannedDeviceCode.trim())
                    .last("LIMIT 1"));
        }
        if (equipment == null) {
            throw new BusinessException("设备不存在或已删除");
        }
        return equipment;
    }

    /**
     * 检查记录是否可以暂停
     */
    private static boolean canPauseExecution(ProductionOperationExecution execution) {
        // 只有进行中状态的记录可以暂停
        return ExecutionStatusEnum.EXECUTING.getValue().equals(execution.getExecutionStatus());
    }

    /**
     * 检查记录是否可以完成
     */
    private static boolean canCompleteExecution(ProductionOperationExecution execution) {
        // 只有进行中状态的记录可以完成
        return ExecutionStatusEnum.EXECUTING.getValue().equals(execution.getExecutionStatus());
    }

    /**
     * 检查记录是否可以取消
     */
    private static boolean canCancelExecution(ProductionOperationExecution execution) {
        // 只有待执行和进行中状态的记录可以取消
        Integer status = execution.getExecutionStatus();
        return ExecutionStatusEnum.PENDING.getValue().equals(status) || ExecutionStatusEnum.EXECUTING.getValue().equals(status);
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
        // 2026-08-12：印刷等自定义工序透传（名称/大类/计划参数）
        vo.setMajorCategory(execution.getMajorCategory());
        vo.setProcessName(execution.getProcessName());
        vo.setCustomProcessParams(execution.getCustomProcessParams());
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

        // 待完成 = 任务数量 - 已完成（WorkReport 有效产出），下限 0
        BigDecimal inQty = execution.getInputQuantity();
        BigDecimal outQty = execution.getOutputQuantity();
        vo.setRemainingQuantity(inQty == null ? BigDecimal.ZERO
                : inQty.subtract(outQty == null ? BigDecimal.ZERO : outQty).max(BigDecimal.ZERO));

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
        // P1 Final Cleanup：透传 DTO 工序名称（此前丢失导致 Execution/任务树 processName 为空）
        execution.setProcessName(createDTO.getProcessName());
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
        if (updateDTO.getActualLaborHours() != null
                || updateDTO.getActualMachineHours() != null
                || updateDTO.getActualCompletedQuantity() != null
                || updateDTO.getActualQualifiedQuantity() != null
                || updateDTO.getActualDefectiveQuantity() != null) {
            // P2-C：生产数量与工时已切换为 WorkReport 事实，普通 Execution Update 不再直接维护（防双重累计）
            throw new BusinessException("生产数量/工时已切换为报工记录，请使用报工功能维护");
        }
        if (updateDTO.getActualStartTime() != null) {
            execution.setActualStartTime(updateDTO.getActualStartTime());
        }
        if (updateDTO.getActualEndTime() != null) {
            execution.setActualEndTime(updateDTO.getActualEndTime());
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
        if (updateDTO.getDefectiveReason() != null) {
            execution.setDefectiveReason(updateDTO.getDefectiveReason());
        }
        // P0-03：DTO.remark 不再写入 defective_reason（原错误映射）；execution 实体无 remark 字段，remark 不持久化

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
