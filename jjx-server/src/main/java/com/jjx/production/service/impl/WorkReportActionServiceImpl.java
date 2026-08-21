package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.WorkReportCancelDTO;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionTaskNode;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.WorkReportVO;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.TaskNodeService;
import com.jjx.production.service.WorkReportActionService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.WorkReportReadService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产报工动作服务实现（P2-C）
 * <p>
 * SUBMIT：校验（execution/权限/数量/工时/时间/设备）
 * → insert WorkReport（SUBMITTED）→ 重算 execution projection → commit
 * CANCEL：条件 UPDATE SUBMITTED→CANCELLED（affectedRows 检查，已撤销幂等）
 * → 重算 projection → commit
 * <p>
 * 权限：production:work-report:add（SUBMIT）/ production:work-report:cancel（CANCEL）
 * 报工提交人 = 操作人（operatorId 由上层会话注入）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkReportActionServiceImpl implements WorkReportActionService {

    private final ProductionWorkReportMapper workReportMapper;
    private final ProductionOperationExecutionMapper executionMapper;
    private final WorkReportProjectionService projectionService;
    private final WorkReportReadService readService;
    private final JdbcTemplate jdbcTemplate;
    /** P2：报工绑定 TaskNode——新报工必须属于某任务节点且由节点持有人提交（数量受 selfRemaining 约束） */
    private final TaskNodeService taskNodeService;
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
        // 2.5 TaskNode 绑定：新报工必须绑定任务节点；当前用户 = taskNode.assigneeId
        if (dto.getTaskNodeId() == null) throw new BusinessException("报工必须绑定任务节点");
        ProductionTaskNode taskNode = taskNodeService.getNode(dto.getTaskNodeId());
        if (!dto.getExecutionId().equals(taskNode.getExecutionId())) {
            throw new BusinessException("任务节点不属于该工序执行记录");
        }
        if (operatorId == null || !operatorId.equals(taskNode.getAssigneeId())) {
            throw new BusinessException("只有任务节点持有人本人可以报工");
        }
        // 3. 数量校验
        BigDecimal qualified = dto.getQualifiedQuantity() == null ? BigDecimal.ZERO : dto.getQualifiedQuantity();
        BigDecimal defective = dto.getDefectiveQuantity() == null ? BigDecimal.ZERO : dto.getDefectiveQuantity();
        if (qualified.compareTo(BigDecimal.ZERO) < 0 || defective.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("数量不能为负数");
        }
        if (qualified.add(defective).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("本次报工合格与不良数量之和必须大于 0");
        }
        // 3.5 本次数量 <= 节点 selfRemaining（selfReported 从 WorkReport 动态汇总，撤销后自动恢复）
        BigDecimal selfRemaining = taskNodeService.remaining(taskNode.getTaskNodeId());
        if (qualified.add(defective).compareTo(selfRemaining) > 0) {
            throw new BusinessException("报工数量 " + strip(qualified.add(defective))
                    + " 超过节点剩余可报数量 " + strip(selfRemaining));
        }
        // 超计划：允许（不校验 <= planned）
        // defective>0 → defectReason 必填（推荐规则）
        if (defective.compareTo(BigDecimal.ZERO) > 0
                && (dto.getDefectReason() == null || dto.getDefectReason().isBlank())) {
            throw new BusinessException("存在不良数量时，不良原因必填");
        }

        // 4. 工时校验
        BigDecimal labor = dto.getLaborHours() == null ? BigDecimal.ZERO : dto.getLaborHours();
        BigDecimal machine = dto.getMachineHours() == null ? BigDecimal.ZERO : dto.getMachineHours();
        if (labor.compareTo(BigDecimal.ZERO) < 0 || machine.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("工时不能为负数");
        }

        // 5. 时间区间：要么都不传，要么同时传且 end>=start
        if (dto.getWorkStartTime() != null && dto.getWorkEndTime() == null
                || dto.getWorkStartTime() == null && dto.getWorkEndTime() != null) {
            throw new BusinessException("生产开始/结束时间需同时填写");
        }
        if (dto.getWorkStartTime() != null && dto.getWorkEndTime() != null
                && dto.getWorkEndTime().isBefore(dto.getWorkStartTime())) {
            throw new BusinessException("生产结束时间不能早于开始时间");
        }

        // 6. 设备解析：客户端传 → 验证存在并保存本次实际设备；不传 → 默认 execution 设备
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

        // 7. 创建 WorkReport（客户端不能决定 status/reportTime）
        ProductionWorkReport r = new ProductionWorkReport();
        r.setOrderId(exec.getOrderId());
        r.setOrderNo(orderNoOf(exec.getOrderId()));
        r.setExecutionId(exec.getExecutionId());
        r.setTaskNodeId(taskNode.getTaskNodeId());
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

        // 8. 重算 execution projection（同一事务）
        projectionService.recalculate(exec.getExecutionId());
        log.info("报工成功 reportId={}, executionId={}, 合格={} 不良={}, 报工人={}",
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

    private String strip(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
