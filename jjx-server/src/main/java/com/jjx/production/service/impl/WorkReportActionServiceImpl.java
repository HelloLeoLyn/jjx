package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.WorkReportCancelDTO;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.domain.entity.ProductionDispatch;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.WorkReportVO;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionDispatchMapper;
import com.jjx.production.mapper.ProductionDispatchNodeMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.WorkReportActionService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.WorkReportReadService;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.mapper.SysUserMapper;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产报工动作服务实现（P2-C）
 * <p>
 * SUBMIT：校验（execution/订单/dispatch/ACTIVE Node/权限/数量/工时/时间/设备/关系一致）
 * → insert WorkReport（SUBMITTED）→ 重算 execution projection → commit
 * CANCEL：条件 UPDATE SUBMITTED→CANCELLED（affectedRows 检查，已撤销幂等）
 * → 重算 projection → commit
 * <p>
 * 权限：production:work-report:add（SUBMIT）/ production:work-report:cancel（CANCEL）
 * 业务关系：当前登录用户必须是当前 ACTIVE Node assignee（P2 V1 不允许代报）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkReportActionServiceImpl implements WorkReportActionService {

    private final ProductionWorkReportMapper workReportMapper;
    private final ProductionOperationExecutionMapper executionMapper;
    private final ProductionDispatchMapper dispatchMapper;
    private final ProductionDispatchNodeMapper nodeMapper;
    private final SysUserMapper sysUserMapper;
    private final WorkReportProjectionService projectionService;
    private final WorkReportReadService readService;
    private final JdbcTemplate jdbcTemplate;
    /** P3-C：WorkReport 撤销质检联动（PASS/FAIL 禁撤；PENDING 联动逻辑删除） */
    private final com.jjx.production.service.QualityInspectionService qualityInspectionService;

    // ==================== SUBMIT ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkReportVO submit(WorkReportSubmitDTO dto, String operatorName, Long operatorId) {
        // 1. 权限：work-report:add（独立于 execution edit）
        if (!SecurityUtils.hasPermission("*:*:*") && !SecurityUtils.hasPermission("production:work-report:add")) {
            throw new BusinessException("无报工权限");
        }
        if (dto.getExecutionId() == null) throw new BusinessException("缺少工序执行ID");

        // 2. execution 存在 + 状态
        ProductionOperationExecution exec = executionMapper.selectById(dto.getExecutionId());
        if (exec == null) throw new BusinessException("工序执行记录不存在");
        Integer st = exec.getExecutionStatus();
        boolean allowed = ExecutionStatusEnum.EXECUTING.getCode().equals(st)
                || ExecutionStatusEnum.PAUSED.getCode().equals(st);
        if (!allowed) {
            throw new BusinessException("当前工序状态不允许报工（仅执行中/已暂停可报工）");
        }
        // 3. dispatch 存在 + ACTIVE Node 存在
        ProductionDispatch dispatch = dispatchMapper.selectOne(Wrappers.<ProductionDispatch>lambdaQuery()
                .eq(ProductionDispatch::getExecutionId, dto.getExecutionId()).last("LIMIT 1"));
        if (dispatch == null) throw new BusinessException("该工序尚未派工，无法报工");
        ProductionDispatchNode activeNode = nodeMapper.selectOne(Wrappers.<ProductionDispatchNode>lambdaQuery()
                .eq(ProductionDispatchNode::getDispatchId, dispatch.getDispatchId())
                .eq(ProductionDispatchNode::getNodeStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (activeNode == null) throw new BusinessException("当前无进行中的责任节点，无法报工");

        // 4. WP-B：Assignment-aware 报工权限（在步骤 5 数量解析后统一判断）
        // 5. 数量校验
        BigDecimal qualified = dto.getQualifiedQuantity() == null ? BigDecimal.ZERO : dto.getQualifiedQuantity();
        BigDecimal defective = dto.getDefectiveQuantity() == null ? BigDecimal.ZERO : dto.getDefectiveQuantity();
        if (qualified.compareTo(BigDecimal.ZERO) < 0 || defective.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("数量不能为负数");
        }
        if (qualified.add(defective).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("本次报工合格与不良数量之和必须大于 0");
        }
        // 超计划：允许（不校验 <= planned）
        // defective>0 → defectReason 必填（推荐规则）
        if (defective.compareTo(BigDecimal.ZERO) > 0
                && (dto.getDefectReason() == null || dto.getDefectReason().isBlank())) {
            throw new BusinessException("存在不良数量时，不良原因必填");
        }

        // 4b. WP-B：Assignment-aware 报工权限与数量校验（依赖 qualified/defective 已解析）
        //     若 Execution 存在 Assignment → 必须绑定当前用户的有效 Assignment，累计不超有效份额
        //     若 Execution 完全无 Assignment → Legacy Compatibility：保留 ACTIVE Node assignee 直接报工
        Long assignmentIdToWrite;
        List<com.jjx.production.domain.entity.ProductionExecutionAssignment> activeAssignments =
                jdbcTemplate.query(
                        "SELECT assignment_id, assignee_id, assigned_quantity, released_quantity "
                                + "FROM production_execution_assignment "
                                + "WHERE execution_id = ? AND assignment_status = 'ACTIVE'",
                        (rs, i) -> {
                            com.jjx.production.domain.entity.ProductionExecutionAssignment a =
                                    new com.jjx.production.domain.entity.ProductionExecutionAssignment();
                            a.setAssignmentId(rs.getLong("assignment_id"));
                            a.setAssigneeId(rs.getLong("assignee_id"));
                            a.setAssignedQuantity(rs.getBigDecimal("assigned_quantity"));
                            a.setReleasedQuantity(rs.getBigDecimal("released_quantity"));
                            return a;
                        }, dto.getExecutionId());
        boolean hasAssignment = activeAssignments != null && !activeAssignments.isEmpty();
        if (hasAssignment) {
            // 新链路：必须找到当前用户的有效 Assignment
            com.jjx.production.domain.entity.ProductionExecutionAssignment mine = null;
            for (com.jjx.production.domain.entity.ProductionExecutionAssignment a : activeAssignments) {
                if (operatorId != null && operatorId.equals(a.getAssigneeId())) {
                    mine = a;
                    break;
                }
            }
            if (mine == null) {
                throw new BusinessException("当前用户没有该工序的有效作业分配，无法报工（责任人需先分配作业给自己）");
            }
            // 数量校验：累计有效 output + 本次 <= effective（assigned - released）
            BigDecimal effective = mine.getAssignedQuantity()
                    .subtract(mine.getReleasedQuantity() != null ? mine.getReleasedQuantity() : BigDecimal.ZERO);
            BigDecimal reported = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(qualified_quantity + defective_quantity),0) FROM production_work_report "
                            + "WHERE assignment_id = ? AND report_status = 'SUBMITTED'",
                    BigDecimal.class, mine.getAssignmentId());
            if (reported == null) reported = BigDecimal.ZERO;
            BigDecimal after = reported.add(qualified).add(defective);
            if (after.compareTo(effective) > 0) {
                throw new BusinessException("报工数量超过分配有效数量（分配 " + effective.stripTrailingZeros().toPlainString()
                        + "，已报 " + reported.stripTrailingZeros().toPlainString()
                        + "，本次 " + qualified.add(defective).stripTrailingZeros().toPlainString() + "）");
            }
            assignmentIdToWrite = mine.getAssignmentId();
        } else {
            // Legacy Compatibility：Execution 完全无 Assignment → 旧规则（ACTIVE Node assignee 本人报工）
            if (operatorId == null || !operatorId.equals(activeNode.getAssigneeId())) {
                throw new BusinessException("只有当前责任人（" + activeNode.getAssigneeName() + "）可以报工");
            }
            assignmentIdToWrite = null;
        }

        // 6. 工时校验
        BigDecimal labor = dto.getLaborHours() == null ? BigDecimal.ZERO : dto.getLaborHours();
        BigDecimal machine = dto.getMachineHours() == null ? BigDecimal.ZERO : dto.getMachineHours();
        if (labor.compareTo(BigDecimal.ZERO) < 0 || machine.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("工时不能为负数");
        }

        // 7. 时间区间：要么都不传，要么同时传且 end>=start
        if (dto.getWorkStartTime() != null && dto.getWorkEndTime() == null
                || dto.getWorkStartTime() == null && dto.getWorkEndTime() != null) {
            throw new BusinessException("生产开始/结束时间需同时填写");
        }
        if (dto.getWorkStartTime() != null && dto.getWorkEndTime() != null
                && dto.getWorkEndTime().isBefore(dto.getWorkStartTime())) {
            throw new BusinessException("生产结束时间不能早于开始时间");
        }

        // 8. 设备解析：客户端传 → 验证存在并保存本次实际设备；不传 → 默认 execution 设备
        Long equipmentId = dto.getEquipmentId() != null ? dto.getEquipmentId() : exec.getEquipmentId();
        String equipmentName = null;
        if (equipmentId != null) {
            if (dto.getEquipmentId() != null) {
                equipmentName = equipmentNameOf(equipmentId);
                if (equipmentName == null) throw new BusinessException("设备不存在");
            } else {
                equipmentName = exec.getEquipmentName(); // 默认用 execution 快照名
            }
        }

        // 9. 关系一致性：execution.dispatch / node.dispatch 一致（dispatchId 冗余校验）
        if (!dispatch.getExecutionId().equals(dto.getExecutionId())) {
            throw new BusinessException("派工单与工序执行关系不一致");
        }
        if (!activeNode.getDispatchId().equals(dispatch.getDispatchId())) {
            throw new BusinessException("责任节点与派工单关系不一致");
        }

        // 10. 创建 WorkReport（客户端不能决定 status/reportTime）
        ProductionWorkReport r = new ProductionWorkReport();
        r.setOrderId(exec.getOrderId());
        r.setOrderNo(orderNoOf(exec.getOrderId()));
        r.setExecutionId(exec.getExecutionId());
        r.setDispatchId(dispatch.getDispatchId());
        r.setDispatchNodeId(activeNode.getNodeId());
        // WP-B：Assignment 链路报工写入 assignment_id（无 Assignment 的 legacy 报工为 null）
        r.setAssignmentId(assignmentIdToWrite);
        r.setReporterId(operatorId);
        r.setReporterName(operatorName);
        r.setEquipmentId(equipmentId);
        r.setEquipmentName(equipmentName);
        r.setQualifiedQuantity(qualified);
        r.setDefectiveQuantity(defective);
        r.setLaborHours(labor);
        r.setMachineHours(machine);
        r.setWorkStartTime(dto.getWorkStartTime());
        r.setWorkEndTime(dto.getWorkEndTime());
        r.setReportTime(LocalDateTime.now());
        r.setDefectReason(dto.getDefectReason());
        r.setRemark(dto.getRemark());
        r.setReportStatus(WorkReportStatusEnum.SUBMITTED.getCode());
        r.setCreateBy(operatorName);
        workReportMapper.insert(r);

        // 11. 重算 execution projection（同一事务）
        projectionService.recalculate(exec.getExecutionId());
        log.info("报工成功 reportId={}, executionId={}, 合格={} 不良={}, 责任人={}",
                r.getReportId(), exec.getExecutionId(), qualified, defective, operatorName);
        return readService.getById(r.getReportId());
    }

    // ==================== CANCEL ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkReportVO cancel(Long reportId, WorkReportCancelDTO dto, String operatorName, Long operatorId) {
        if (dto == null || dto.getCancelReason() == null || dto.getCancelReason().isBlank()) {
            throw new BusinessException("撤销原因必填");
        }
        ProductionWorkReport r = workReportMapper.selectById(reportId);
        if (r == null) throw new BusinessException("报工记录不存在");

        // 已完成 execution 禁止撤销（P2 V1：完成后的数量可能已影响 order/库存）
        ProductionOperationExecution exec = executionMapper.selectById(r.getExecutionId());
        if (exec != null && ExecutionStatusEnum.COMPLETED.getCode().equals(exec.getExecutionStatus())) {
            throw new BusinessException("工序已完成，不允许撤销报工");
        }

        // P3-C：质检关联 gate——已关联 PASS/FAIL 质检的报工禁止撤销；仅 PENDING 质检时联动处理
        java.util.List<com.jjx.production.domain.vo.QualityInspectionVO> related =
                qualityInspectionService.listByWorkReportId(reportId);
        boolean hasFinalized = related.stream().anyMatch(q ->
                com.jjx.production.enums.QualityInspectionResultEnum.PASS.getCode().equals(q.getResult())
                        || com.jjx.production.enums.QualityInspectionResultEnum.FAIL.getCode().equals(q.getResult()));
        if (hasFinalized) {
            throw new BusinessException("该报工已关联质检判定结果（PASS/FAIL），不允许撤销；如需更正请走质检复检");
        }
        // 仅 PENDING 质检：允许撤销报工，但同步逻辑删除这些 PENDING 质检（历史可追踪，不留指向已撤销事实的有效质检单）
        for (com.jjx.production.domain.vo.QualityInspectionVO q : related) {
            qualityInspectionService.delete(q.getInspectionId());
            log.info("报工撤销联动：逻辑删除 PENDING 质检 {}", q.getInspectionId());
        }

        // 权限：work-report:cancel 权限点 + 业务关系（本人或超管）
        if (!SecurityUtils.hasPermission("*:*:*") && !SecurityUtils.hasPermission("production:work-report:cancel")) {
            throw new BusinessException("无撤销报工权限");
        }
        // 业务关系：reporter 本人 或 超管（权限点 + 业务关系共同判断）
        if (!SecurityUtils.hasPermission("*:*:*") && (operatorId == null || !operatorId.equals(r.getReporterId()))) {
            throw new BusinessException("只有报工提交人本人或管理员可以撤销");
        }

        // 条件更新：SUBMITTED → CANCELLED（并发保护）
        ProductionWorkReport upd = new ProductionWorkReport();
        upd.setReportId(reportId);
        upd.setReportStatus(WorkReportStatusEnum.CANCELLED.getCode());
        upd.setCancelledBy(operatorId);
        upd.setCancelledByName(operatorName);
        upd.setCancelledAt(LocalDateTime.now());
        upd.setCancelReason(dto.getCancelReason());
        upd.setUpdateBy(operatorName);
        int rows = workReportMapper.update(upd, Wrappers.<ProductionWorkReport>lambdaUpdate()
                .eq(ProductionWorkReport::getReportId, reportId)
                .eq(ProductionWorkReport::getReportStatus, WorkReportStatusEnum.SUBMITTED.getCode()));
        if (rows == 0) {
            // 已 CANCELLED → 幂等返回当前状态
            ProductionWorkReport cur = workReportMapper.selectById(reportId);
            if (cur != null && WorkReportStatusEnum.CANCELLED.getCode().equals(cur.getReportStatus())) {
                log.info("撤销幂等：报工 {} 已撤销", reportId);
                return readService.getById(reportId);
            }
            throw new BusinessException("报工状态已变化，请刷新后重试");
        }

        // 重算 projection（同一事务）
        projectionService.recalculate(r.getExecutionId());
        log.info("撤销报工 reportId={}, executionId={}, 原因={}", reportId, r.getExecutionId(), dto.getCancelReason());
        return readService.getById(reportId);
    }

    // ==================== helpers ====================

    private String equipmentNameOf(Long equipmentId) {
        try {
            var names = jdbcTemplate.query(
                    "SELECT equipment_name FROM production_equipment WHERE equipment_id = ?",
                    (rs, i) -> rs.getString("equipment_name"), equipmentId);
            return names.isEmpty() ? null : names.get(0);
        } catch (Exception e) {
            log.warn("查询设备失败 equipmentId={}: {}", equipmentId, e.getMessage());
            return null;
        }
    }

    private String orderNoOf(Long orderId) {
        try {
            var nos = jdbcTemplate.query(
                    "SELECT order_no FROM production_order WHERE order_id = ?",
                    (rs, i) -> rs.getString("order_no"), orderId);
            return nos.isEmpty() ? null : nos.get(0);
        } catch (Exception e) {
            log.warn("查询工单编号失败 orderId={}: {}", orderId, e.getMessage());
            return null;
        }
    }
}
