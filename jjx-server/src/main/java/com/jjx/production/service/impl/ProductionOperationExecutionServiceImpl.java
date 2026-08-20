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
import com.jjx.production.domain.entity.ProductionDispatch;
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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final com.jjx.production.service.DispatchService dispatchService;
    private final com.jjx.production.service.WorkReportProjectionService workReportProjectionService;
    private final com.jjx.production.service.DispatchNodeReadService dispatchNodeReadService;
    private final com.jjx.production.mapper.ProductionDispatchMapper dispatchMapper;
    /** P3-C：FQC 自动创建 / 质检联动 */
    private final com.jjx.production.service.QualityActionService qualityActionService;
    /** WP-D：Assignment 视角（我的份额/hasAssignment） */
    private final com.jjx.production.mapper.ProductionExecutionAssignmentMapper assignmentMapper;
    private final com.jjx.production.mapper.ProductionWorkReportMapper workReportMapper;

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

        // WP-D：scope=mine → 我的当前任务（责任人 = ACTIVE Node assignee；执行人 = ACTIVE Assignment 且剩余>0）
        //      scope=done → 我已完成（执行人：我的 Assignment 已完成/已释放；责任人：参与过且 Execution 已完成）
        Long mineUserId = null;
        boolean doneScope = false;
        if (queryDTO != null) {
            if ("mine".equalsIgnoreCase(queryDTO.getScope())) {
                mineUserId = com.jjx.system.utils.SecurityUtils.getUserId();
            } else if ("done".equalsIgnoreCase(queryDTO.getScope())) {
                mineUserId = com.jjx.system.utils.SecurityUtils.getUserId();
                doneScope = true;
            }
        }

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
        // P2-D/WP-D：填充 currentAssignee projection + Assignment 视角（hasAssignment/myXXX/canReport）
        fillCurrentAssigneeProjection(vos);
        if (mineUserId != null) {
            final Long meId = mineUserId;
            if (doneScope) {
                // 我已完成：执行人视角（我的 Assignment 剩余==0 或已释放）+ 责任人视角（曾参与且 Execution 已完成）
                java.util.Set<Long> myExecIds = myInvolvedExecutionIds(meId);
                vos.removeIf(vo -> {
                    if (!myExecIds.contains(vo.getExecutionId())) return true;
                    // Execution 已完成：参与即算（责任人/执行人都覆盖）
                    if (com.jjx.production.enums.ExecutionStatusEnum.COMPLETED.getCode()
                            .equals(vo.getExecutionStatus())) {
                        return false;
                    }
                    // Execution 未完成：仅执行人视角，且我的剩余已归零（完成/已释放）
                    return !(vo.getMyAssignmentId() != null
                            && vo.getMyRemainingQuantity() != null
                            && vo.getMyRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0);
                });
            } else {
                // 我的当前任务：责任人（ACTIVE Node assignee + 未完成）∪ 执行人（ACTIVE Assignment 剩余>0）
                vos.removeIf(vo -> {
                    // 已完成/已取消不出现在“我的当前任务”
                    if (com.jjx.production.enums.ExecutionStatusEnum.COMPLETED.getCode().equals(vo.getExecutionStatus())
                            || com.jjx.production.enums.ExecutionStatusEnum.CANCELLED.getCode().equals(vo.getExecutionStatus())) {
                        return true;
                    }
                    boolean isCurrentAssignee = meId.equals(vo.getCurrentAssigneeId());
                    boolean isActiveExecutor = vo.getMyAssignmentId() != null
                            && vo.getMyRemainingQuantity() != null
                            && vo.getMyRemainingQuantity().compareTo(BigDecimal.ZERO) > 0;
                    return !isCurrentAssignee && !isActiveExecutor;
                });
            }
        }
        return vos;
    }

    /**
     * P2-D/WP-D：按 execution 填充 currentAssignee projection + Assignment 视角
     * execution → dispatch(execution_id 1:1) → ACTIVE node → assignee/org 快照
     * Assignment 视角：hasAssignment（存在非 CANCELLED 分配）+ 当前用户的“我的份额”
     * canReport：
     *   - 有 Assignment → 仅当前用户有 ACTIVE Assignment 且剩余>0（执行人报工；责任人无分配不能报）
     *   - 无 Assignment → Legacy：有 work-report:add 权限且是 ACTIVE assignee 本人
     */
    private void fillCurrentAssigneeProjection(List<ProductionOperationExecutionVO> vos) {
        if (vos == null || vos.isEmpty()) return;
        Long me = com.jjx.system.utils.SecurityUtils.getUserId();
        boolean hasReportPerm = com.jjx.system.utils.SecurityUtils.hasPermission("production:work-report:add");
        // 批量取这些 execution 的 Assignment（避免 N+1）
        java.util.Map<Long, List<com.jjx.production.domain.entity.ProductionExecutionAssignment>> execAssignments =
                loadAssignmentsByExecutionIds(vos.stream()
                        .map(ProductionOperationExecutionVO::getExecutionId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList()));
        for (ProductionOperationExecutionVO vo : vos) {
            try {
                List<com.jjx.production.domain.entity.ProductionExecutionAssignment> rows =
                        execAssignments.getOrDefault(vo.getExecutionId(), java.util.Collections.emptyList());
                // hasAssignment：存在非 CANCELLED 分配
                boolean hasAssignment = rows.stream().anyMatch(a -> !com.jjx.production.enums.AssignmentStatusEnum.CANCELLED
                        .getCode().equals(a.getAssignmentStatus()));
                vo.setHasAssignment(hasAssignment);
                // 我的份额（非 CANCELLED 分配；唯一约束下最多一个，防御取第一个）
                com.jjx.production.domain.entity.ProductionExecutionAssignment mine = rows.stream()
                        .filter(a -> !com.jjx.production.enums.AssignmentStatusEnum.CANCELLED
                                .getCode().equals(a.getAssignmentStatus()))
                        .filter(a -> me != null && me.equals(a.getAssigneeId()))
                        .findFirst().orElse(null);
                if (mine != null) {
                    BigDecimal assigned = mine.getAssignedQuantity() != null ? mine.getAssignedQuantity() : BigDecimal.ZERO;
                    BigDecimal released = mine.getReleasedQuantity() != null ? mine.getReleasedQuantity() : BigDecimal.ZERO;
                    BigDecimal reported = reportedByAssignment(mine.getAssignmentId());
                    BigDecimal remaining = assigned.subtract(released).subtract(reported);
                    if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
                    vo.setMyAssignmentId(mine.getAssignmentId());
                    vo.setMyAssignedQuantity(assigned);
                    vo.setMyReportedQuantity(reported);
                    vo.setMyRemainingQuantity(remaining);
                    vo.setMyAssignmentStatus(remaining.compareTo(BigDecimal.ZERO) <= 0
                            ? com.jjx.production.enums.AssignmentStatusEnum.COMPLETED.getCode()
                            : com.jjx.production.enums.AssignmentStatusEnum.ACTIVE.getCode());
                } else {
                    vo.setMyAssignmentId(null);
                    vo.setMyAssignedQuantity(null);
                    vo.setMyReportedQuantity(null);
                    vo.setMyRemainingQuantity(null);
                    vo.setMyAssignmentStatus(null);
                }

                ProductionDispatch dispatch = dispatchMapper.selectOne(
                        Wrappers.<ProductionDispatch>lambdaQuery()
                                .eq(ProductionDispatch::getExecutionId, vo.getExecutionId())
                                .last("LIMIT 1"));
                if (dispatch == null) {
                    vo.setAssigneeSource("NONE");
                    vo.setCanReport(false);
                    continue;
                }
                vo.setDispatchId(dispatch.getDispatchId());
                com.jjx.production.domain.vo.DispatchNodeVO cur =
                        dispatchNodeReadService.getCurrentActiveNode(dispatch.getDispatchId());
                if (cur == null) {
                    vo.setAssigneeSource("NONE");
                    vo.setCanReport(false);
                    continue;
                }
                vo.setCurrentNodeId(cur.getNodeId());
                vo.setCurrentAssigneeId(cur.getAssigneeId());
                vo.setCurrentAssigneeName(cur.getAssigneeName());
                vo.setCurrentOrgName(cur.getOrgName());
                vo.setAssigneeSource(cur.getSource());
                // canReport：Assignment 链路 → 执行人（我的 ACTIVE 分配剩余>0）；Legacy → ACTIVE assignee + 权限
                if (hasAssignment) {
                    vo.setCanReport(vo.getMyAssignmentId() != null
                            && vo.getMyRemainingQuantity() != null
                            && vo.getMyRemainingQuantity().compareTo(BigDecimal.ZERO) > 0);
                } else {
                    vo.setCanReport(hasReportPerm && me != null && me.equals(cur.getAssigneeId()));
                }
            } catch (Exception e) {
                log.warn("填充 currentAssignee 投影失败 executionId={}: {}", vo.getExecutionId(), e.getMessage());
                vo.setCanReport(false);
            }
        }
    }

    /** 批量加载 executionId → 其 Assignment 列表 */
    private java.util.Map<Long, List<com.jjx.production.domain.entity.ProductionExecutionAssignment>>
    loadAssignmentsByExecutionIds(List<Long> executionIds) {
        java.util.Map<Long, List<com.jjx.production.domain.entity.ProductionExecutionAssignment>> map = new java.util.HashMap<>();
        if (executionIds == null || executionIds.isEmpty()) return map;
        try {
            List<com.jjx.production.domain.entity.ProductionExecutionAssignment> rows = assignmentMapper.selectList(
                    Wrappers.<com.jjx.production.domain.entity.ProductionExecutionAssignment>lambdaQuery()
                            .in(com.jjx.production.domain.entity.ProductionExecutionAssignment::getExecutionId, executionIds)
                            .orderByAsc(com.jjx.production.domain.entity.ProductionExecutionAssignment::getAssignmentId));
            for (com.jjx.production.domain.entity.ProductionExecutionAssignment r : rows) {
                map.computeIfAbsent(r.getExecutionId(), k -> new java.util.ArrayList<>()).add(r);
            }
        } catch (Exception e) {
            log.warn("批量加载 Assignment 失败: {}", e.getMessage());
        }
        return map;
    }

    /** 某 Assignment 的有效 SUBMITTED 报工汇总（qualified+defective） */
    private BigDecimal reportedByAssignment(Long assignmentId) {
        if (assignmentId == null) return BigDecimal.ZERO;
        try {
            List<com.jjx.production.domain.entity.ProductionWorkReport> reports = workReportMapper.selectList(
                    Wrappers.<com.jjx.production.domain.entity.ProductionWorkReport>lambdaQuery()
                            .eq(com.jjx.production.domain.entity.ProductionWorkReport::getAssignmentId, assignmentId)
                            .eq(com.jjx.production.domain.entity.ProductionWorkReport::getReportStatus,
                                    com.jjx.production.enums.WorkReportStatusEnum.SUBMITTED.getCode()));
            BigDecimal sum = BigDecimal.ZERO;
            for (com.jjx.production.domain.entity.ProductionWorkReport r : reports) {
                BigDecimal q = r.getQualifiedQuantity() != null ? r.getQualifiedQuantity() : BigDecimal.ZERO;
                BigDecimal d = r.getDefectiveQuantity() != null ? r.getDefectiveQuantity() : BigDecimal.ZERO;
                sum = sum.add(q).add(d);
            }
            return sum;
        } catch (Exception e) {
            log.warn("汇总 Assignment 报工失败 assignmentId={}: {}", assignmentId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** WP-D：当前用户参与过的 Execution ID 集合（Assignment assignee 或 DispatchNode assignee） */
    private java.util.Set<Long> myInvolvedExecutionIds(Long userId) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        if (userId == null) return ids;
        try {
            // 1. Assignment assignee=我
            List<com.jjx.production.domain.entity.ProductionExecutionAssignment> myAssigns = assignmentMapper.selectList(
                    Wrappers.<com.jjx.production.domain.entity.ProductionExecutionAssignment>lambdaQuery()
                            .eq(com.jjx.production.domain.entity.ProductionExecutionAssignment::getAssigneeId, userId));
            for (com.jjx.production.domain.entity.ProductionExecutionAssignment a : myAssigns) {
                if (a.getExecutionId() != null) ids.add(a.getExecutionId());
            }
            // 2. DispatchNode assignee=我（经 dispatch → execution）
            List<Long> nodeExecIds = jdbcTemplate.query(
                    "SELECT d.execution_id FROM production_dispatch_node n "
                            + "JOIN production_dispatch d ON d.dispatch_id = n.dispatch_id "
                            + "WHERE n.assignee_id = ? AND d.execution_id IS NOT NULL",
                    (rs, i) -> rs.getLong("execution_id"), userId);
            ids.addAll(nodeExecIds);
        } catch (Exception e) {
            log.warn("查询我的参与 Execution 失败 userId={}: {}", userId, e.getMessage());
        }
        return ids;
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
                                + com.jjx.production.enums.OrderStatusEnum.CANCELLED.getCode(),
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
        // 派工联动（2026-08-12）：执行开始 → 派工单同步为执行中
        try {
            dispatchService.syncByExecution(executionId, 2);
        } catch (Exception e) {
            log.warn("派工单联动开始失败: {}", e.getMessage());
        }
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
    public boolean qualityCheck(Long executionId, String checkType, String checkResult, String checkItems, String remark) {
        log.info("工序{}: executionId={}", "首检".equals(checkType) ? "首检" : "巡检", executionId);

        ProductionOperationExecution execution = getById(executionId);
        if (execution == null) {
            throw new BusinessException("工序执行记录不存在: " + executionId);
        }
        // 只有执行中可质检
        if (execution.getExecutionStatus() == null
                || execution.getExecutionStatus() != ExecutionStatusEnum.EXECUTING.getCode()) {
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
            execution.setExecutionStatus(ExecutionStatusEnum.PAUSED.getCode());
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

        // 053⑤数量冻结：工单已完工后禁止再报工/改数量
        try {
            ProductionOrder prodOrder = productionOrderMapper.selectById(execution.getOrderId());
            if (prodOrder != null && com.jjx.production.enums.OrderStatusEnum.COMPLETED.getCode()
                    .equals(prodOrder.getOrderStatus())) {
                throw new BusinessException("工单已完工，数量已冻结，禁止再报工/修改");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("完工冻结校验异常(跳过): {}", e.getMessage());
        }

        // 检查记录状态是否可以完成
        if (!canCompleteExecution(execution)) {
            throw new BusinessException("记录状态不允许完成");
        }

        // P2-C 完工 gate：至少存在 1 条 SUBMITTED WorkReport 才能完成（报工≠完成，但完成必须有生产事实）
        if (!workReportProjectionService.hasAnySubmitted(executionId)) {
            throw new BusinessException("当前工序尚无有效报工记录，不能完成");
        }

        // WP-B：Assignment-aware 完工 gate（不破坏无 Assignment 的历史柔性流程）
        // 存在 Assignment 时：
        //   ① 不允许存在 remaining > 0 的有效 Assignment
        //   ② unassigned > 0 → 明确业务错误（“低于计划/短缺完工”是后续独立业务动作，不混入普通完成）
        // 无 Assignment 的历史 Execution → 保持现有 V1 gate（柔性）
        try {
            Long activeCnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM production_execution_assignment WHERE execution_id = ? AND assignment_status = 'ACTIVE'",
                    Long.class, executionId);
            if (activeCnt != null && activeCnt > 0) {
                // ① 存在剩余 > 0 的有效 Assignment → 拒绝
                Long remainingCnt = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM production_execution_assignment a WHERE a.execution_id = ? "
                                + "AND a.assignment_status = 'ACTIVE' "
                                + "AND (a.assigned_quantity - a.released_quantity) > "
                                + "COALESCE((SELECT SUM(qualified_quantity + defective_quantity) FROM production_work_report w "
                                + "         WHERE w.assignment_id = a.assignment_id AND w.report_status = 'SUBMITTED'),0)",
                        Long.class, executionId);
                if (remainingCnt != null && remainingCnt > 0) {
                    throw new BusinessException("存在未完成的作业分配（剩余数量 > 0），不能完成工序；请先完成或释放剩余");
                }
                // ② unassigned > 0 → 拒绝（计划未全部分配）
                BigDecimal planned = execution.getInputQuantity() != null ? execution.getInputQuantity() : BigDecimal.ZERO;
                BigDecimal assignedSum = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(assigned_quantity - released_quantity),0) FROM production_execution_assignment "
                                + "WHERE execution_id = ? AND assignment_status <> 'CANCELLED'",
                        BigDecimal.class, executionId);
                if (assignedSum == null) assignedSum = BigDecimal.ZERO;
                if (assignedSum.compareTo(planned) < 0) {
                    throw new BusinessException("工序计划数量未全部分配（已分配 "
                            + assignedSum.stripTrailingZeros().toPlainString()
                            + " / 计划 " + planned.stripTrailingZeros().toPlainString()
                            + "），不能完成工序");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Assignment 完工校验异常(跳过): {}", e.getMessage());
        }

        // 更新状态为已完成
        execution.setExecutionStatus(ExecutionStatusEnum.COMPLETED.getCode());
        execution.setActualEndTime(LocalDateTime.now());

        // P2-C：不再自动补全生产数量（output/qualified/defective 由 WorkReport projection 决定，禁止把计划当实际）
        // 数量字段保持 WorkReport SUM 值，不在此伪造

        boolean success = updateById(execution);
        if (!success) {
            throw new BusinessException("完成工序执行失败");
        }

        // 更新生产工单的完成数量
        updateOrderCompletedQuantity(execution.getOrderId());

        log.info("工序执行完成成功, ID: {}", executionId);
        // 派工联动（2026-08-12）：执行完成 → 派工单同步为已完成
        try {
            dispatchService.syncByExecution(executionId, 4);
        } catch (Exception e) {
            log.warn("派工单联动完成失败: {}", e.getMessage());
        }

        // P3-C：最后有效 Execution 完成后自动创建 PENDING FQC（幂等：已有 PENDING 不重复创建）
        // 判断“最后有效工序”：同 order 下不存在 process_order 更大且未完成(非 COMPLETED/SKIPPED) 的工序
        try {
            Long laterPending = productionOperationExecutionMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionOperationExecution>()
                            .eq(ProductionOperationExecution::getOrderId, execution.getOrderId())
                            .gt(ProductionOperationExecution::getProcessOrder,
                                    execution.getProcessOrder() == null ? 0 : execution.getProcessOrder())
                            .notIn(ProductionOperationExecution::getExecutionStatus,
                                    ExecutionStatusEnum.COMPLETED.getCode(),
                                    ExecutionStatusEnum.SKIPPED.getCode()));
            if (laterPending == null || laterPending == 0) {
                Long fqcId = qualityActionService.createFqcForExecution(executionId);
                if (fqcId != null) {
                    log.info("最后工序 execution={} 完成，自动创建 PENDING FQC={}", executionId, fqcId);
                }
            }
        } catch (Exception e) {
            log.warn("P3-C 自动创建 FQC 失败（不影响工序完成）: {}", e.getMessage());
        }
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
     * 更新生产工单的完成数量（052口径修正）
     * completedQuantity = 各工序合格汇总（仅作进度展示，避免中间环节虚高）
     * finishedQuantity = 成品完工数量（最后一道工序/完工检验合格数，用于完工判断/入库/订单回写）
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

        // 成品完工数量 = 最后一道工序（process_order 最大）的合格数
        BigDecimal finishedQty = BigDecimal.ZERO;
        ProductionOperationExecution lastOp = completedExecutions.stream()
                .filter(e -> e.getProcessOrder() != null)
                .max(java.util.Comparator.comparingInt(e -> e.getProcessOrder() == null ? 0 : e.getProcessOrder()))
                .orElse(null);
        if (lastOp != null && lastOp.getQualifiedQuantity() != null) {
            finishedQty = lastOp.getQualifiedQuantity();
        }

        // 更新工单的完成数量
        ProductionOrder order = productionOrderMapper.selectById(orderId);
        if (order != null) {
            order.setCompletedQuantity(totalQualified);
            order.setFinishedQuantity(finishedQty);
            if (order.getPlannedQuantity() != null) {
                order.setRemainingQuantity(order.getPlannedQuantity().subtract(totalQualified));
            }
            productionOrderMapper.updateById(order);
            log.info("更新工单 {} 完成数量: 工序汇总={}, 成品完工={}", orderId, totalQualified, finishedQty);
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
        if (queryDTO.getExecutionStatus() != null && !queryDTO.getExecutionStatus().isEmpty()) {
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
        if (updateDTO.getExecutionStatus() != null) {
            execution.setExecutionStatus(updateDTO.getExecutionStatus());
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
