package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.notification.domain.dto.NotificationCreateDTO;
import com.jjx.notification.service.NotificationService;
import com.jjx.production.domain.dto.WorkReportCancelDTO;
import com.jjx.production.domain.dto.WorkReportReviewDTO;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionTask;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.QualityInspectionVO;
import com.jjx.production.domain.vo.WorkReportVO;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.ProductionTaskStatus;
import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionTaskMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.ProductionTaskService;
import com.jjx.production.service.ProductionRoleResolver;
import com.jjx.production.service.QualityInspectionService;
import com.jjx.production.service.WorkReportActionService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.WorkReportReadService;
import com.jjx.system.utils.SecurityUtils;
import com.jjx.system.annotation.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 生产报工动作服务实现（P3 WorkReport + Approval）
 * <p>
 * SUBMIT：Task 行 FOR UPDATE 串行化 → 校验（执行人/生命周期/数量/工时/时间/设备）
 * → INSERT PENDING → 重算 execution projection（只认 APPROVED）
 * APPROVE/REJECT：审批关系 + 状态条件 UPDATE PENDING→APPROVED/REJECTED（防重复审批，无需 version）
 * CANCEL：PENDING→CANCELLED（提交人本人或超管；已撤销幂等）
 * <p>
 * 数量 gate：唯一额度边界 = Task.remainingQuantity（taskQuantity - assigned - pending - completed），
 * 由 ProductionTaskService.remainingQuantity 提供；不校验 execution.inputQuantity（不建两套额度检查）。
 * <p>
 * 质检联动：已关联 PASS/FAIL 质检的报工禁止 reject/cancel；仅 PENDING 质检联动逻辑删除。
 * 权限：submit=production:work-report:add / cancel=production:work-report:cancel
 *       approve/reject=production:work-report:approve（Controller 注解）+ 审批关系（本类）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkReportActionServiceImpl implements WorkReportActionService {

    private final ProductionWorkReportMapper workReportMapper;
    private final ProductionOperationExecutionMapper executionMapper;
    private final ProductionTaskMapper productionTaskMapper;
    private final ProductionTaskService productionTaskService;
    private final WorkReportProjectionService projectionService;
    private final WorkReportReadService readService;
    private final QualityInspectionService qualityInspectionService;
    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;
    private final RedisSequenceService redisSequenceService;
    private final ProductionRoleResolver productionRoleResolver;

    private static final String PROJECTION_MISMATCH = "MISMATCH";
    private static final Long SYSTEM_ADMIN_ID = 1L;
    private static final String REPORT_NO_PREFIX = "WR-";
    private static final DateTimeFormatter REPORT_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int REPORT_NO_MAX_ATTEMPTS = 3;

    // ==================== SUBMIT ====================

    @Override
    @Event(value = "production.work-report.submitted", bizId = "#result.reportId",
            bizType = "'production'", params = {
            "orderNo = #result.orderNo", "executionId = #result.executionId",
            "taskId = #result.taskId", "reportId = #result.reportId",
            "qualifiedQuantity = #result.qualifiedQuantity",
            "defectiveQuantity = #result.defectiveQuantity",
            "reporterId = #result.reporterId", "reporterName = #result.reporterName",
            "receiverId = #result.eventReceiverId"
    })
    @Transactional(rollbackFor = Exception.class)
    public WorkReportVO submit(WorkReportSubmitDTO dto, String operatorName, Long operatorId) {
        if (dto == null || dto.getTaskId() == null || dto.getExecutionId() == null) {
            throw new BusinessException("任务ID与工序执行ID必填");
        }
        validateQuantityScale(dto.getQualifiedQuantity(), "合格数量");
        validateQuantityScale(dto.getDefectiveQuantity(), "不良数量");
        // 锁 Task 行：同一 Task 的报工串行化（防 PENDING 超报），锁内重算 remaining
        ProductionTask task = productionTaskMapper.selectByIdForUpdate(dto.getTaskId());
        if (task == null) {
            throw new BusinessException("生产任务不存在: " + dto.getTaskId());
        }
        if ("CANCELLED".equals(task.getStatus())) {
            throw new BusinessException("任务已取消，不能报工");
        }
        if ("COMPLETED".equals(task.getStatus())) {
            throw new BusinessException("任务已完成，不能报工");
        }
        boolean proxySubmit = operatorId == null || !operatorId.equals(task.getAssigneeId());
        if (proxySubmit && !SecurityUtils.hasPermission("production:work-report:proxy")) {
            throw new BusinessException("只有任务当前执行人可以报工");
        }
        if (proxySubmit && dto.getReporterId() == null) {
            throw new BusinessException("代报时事实报工人必填");
        }
        // 锚点一致性：dto.executionId 必须等于 task.executionId
        if (!dto.getExecutionId().equals(task.getExecutionId())) {
            throw new BusinessException("报工工序与任务工序上下文不一致");
        }
        // OperationExecution 生命周期 gate：未开始/已结束的工序不能报工（使用现有正式状态）
        ProductionOperationExecution exec = executionMapper.selectById(dto.getExecutionId());
        if (exec == null) {
            throw new BusinessException("工序执行记录不存在: " + dto.getExecutionId());
        }
        if (!ExecutionStatusEnum.EXECUTING.getValue().equals(exec.getExecutionStatus())) {
            throw new BusinessException("工序未处于执行中状态，不能报工");
        }

        // 数量/工时/时间/设备校验
        BigDecimal qualified = nvl(dto.getQualifiedQuantity());
        BigDecimal defective = nvl(dto.getDefectiveQuantity());
        if (qualified.signum() < 0 || defective.signum() < 0) {
            throw new BusinessException("数量不能为负数");
        }
        BigDecimal reportQuantity = qualified.add(defective);
        if (reportQuantity.signum() <= 0) {
            throw new BusinessException("合格与不良数量之和必须大于0");
        }
        if (defective.signum() > 0 && (dto.getDefectReason() == null || dto.getDefectReason().isBlank())) {
            throw new BusinessException("不良数量大于0时，不良原因必填");
        }
        BigDecimal labor = nvl(dto.getLaborHours());
        BigDecimal machine = nvl(dto.getMachineHours());
        if (labor.signum() < 0 || machine.signum() < 0) {
            throw new BusinessException("工时不能为负数");
        }
        if ((dto.getWorkStartTime() == null) != (dto.getWorkEndTime() == null)) {
            throw new BusinessException("开始与结束时间必须同时填写");
        }
        if (dto.getWorkStartTime() != null && dto.getWorkEndTime().isBefore(dto.getWorkStartTime())) {
            throw new BusinessException("结束时间不能早于开始时间");
        }

        // 数量 gate：唯一额度边界 = Task.remainingQuantity（已排除 assigned/pending/completed）
        BigDecimal remaining = productionTaskService.remainingQuantity(task.getTaskId());
        if (reportQuantity.compareTo(remaining) > 0) {
            throw new BusinessException("报工数量超过任务当前剩余，剩余可报: "
                    + remaining.stripTrailingZeros().toPlainString());
        }

        // 设备：客户端传 → 校验存在并快照名称；不传 → 默认 execution 设备
        Long equipmentId = dto.getEquipmentId();
        String equipmentName;
        if (equipmentId == null) {
            equipmentId = exec.getEquipmentId();
            equipmentName = exec.getEquipmentName();
        } else {
            equipmentName = equipmentNameOf(equipmentId);
            if (equipmentName == null) {
                throw new BusinessException("设备不存在: " + equipmentId);
            }
        }

        // INSERT 一条 PENDING 报工事实（不修改 Task.task_quantity / 不落库 completed/pending）
        ProductionWorkReport r = new ProductionWorkReport();
        r.setOrderId(exec.getOrderId());
        r.setOrderNo(orderNoOf(exec.getOrderId()));
        r.setExecutionId(exec.getExecutionId());
        r.setTaskId(task.getTaskId());
        Long reporterId = proxySubmit ? dto.getReporterId() : operatorId;
        String reporterName = proxySubmit ? userNameOf(reporterId) : displayName(operatorName);
        if (proxySubmit && reporterName == null) {
            throw new BusinessException("事实报工人不存在: " + reporterId);
        }
        r.setReporterId(reporterId);
        r.setReporterName(reporterName);
        if (proxySubmit) {
            r.setProxyId(operatorId);
            r.setProxyName(displayName(operatorName));
        }
        Long pendingReviewerId = parentAssigneeId(task);
        r.setPendingReviewerId(pendingReviewerId);
        r.setPendingReviewerName(userNameOf(pendingReviewerId));
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
        r.setReportStatus(WorkReportStatusEnum.PENDING.getCode());
        r.setCreateBy(operatorName);
        insertWithReportNo(r);

        // 重算 execution projection（同事务；只认 APPROVED，PENDING 不计入 output）
        projectionService.recalculate(exec.getExecutionId());
        log.info("提交报工 reportId={}, taskId={}, executionId={}, q={} d={}",
                r.getReportId(), task.getTaskId(), exec.getExecutionId(), qualified, defective);
        WorkReportVO result = readService.getById(r.getReportId());
        result.setEventReceiverId(pendingReviewerId);
        result.setEventPublished(true);
        return result;
    }

    /**
     * 生成并插入报工编号。不同 Task 可并发提交，最终由唯一索引裁决；冲突后重新读取最大号重试。
     */
    private void insertWithReportNo(ProductionWorkReport report) {
        for (int attempt = 1; attempt <= REPORT_NO_MAX_ATTEMPTS; attempt++) {
            report.setReportId(null);
            report.setReportNo(redisSequenceService.generateBusinessNumberByType(
                    "work_report", "WR-", "yyyyMMdd-", 4));
            try {
                workReportMapper.insert(report);
                return;
            } catch (DuplicateKeyException ex) {
                if (attempt == REPORT_NO_MAX_ATTEMPTS) {
                    throw new BusinessException("报工单号生成冲突，请重试");
                }
                log.warn("报工单号冲突，准备重试: reportNo={}, attempt={}", report.getReportNo(), attempt);
            }
        }
    }

    static String nextReportNo(LocalDate reportDate, String maxReportNo) {
        String prefix = reportNoPrefix(reportDate);
        int nextSequence = 1;
        if (maxReportNo != null && maxReportNo.startsWith(prefix)) {
            String sequencePart = maxReportNo.substring(prefix.length());
            try {
                nextSequence = Integer.parseInt(sequencePart) + 1;
            } catch (NumberFormatException ex) {
                throw new BusinessException("已有报工单号格式异常: " + maxReportNo);
            }
        }
        if (nextSequence > 9999) {
            throw new BusinessException("当日报工单号已达到上限");
        }
        return prefix + String.format("%04d", nextSequence);
    }

    private static String reportNoPrefix(LocalDate reportDate) {
        return REPORT_NO_PREFIX + reportDate.format(REPORT_NO_DATE) + "-";
    }

    // ==================== APPROVE / REJECT ====================

    @Override
    @Event(value = "production.work-report.approved", bizId = "#result.reportId",
            bizType = "'production'", condition = "#result.eventPublished", params = {
            "orderNo = #result.orderNo", "executionId = #result.executionId",
            "taskId = #result.taskId", "reportId = #result.reportId",
            "qualifiedQuantity = #result.qualifiedQuantity",
            "defectiveQuantity = #result.defectiveQuantity",
            "reporterId = #result.reporterId", "reporterName = #result.reporterName",
            "receiverId = #result.reporterId"
    })
    @Transactional(rollbackFor = Exception.class)
    public WorkReportVO approve(Long reportId, WorkReportReviewDTO dto, String operatorName, Long operatorId) {
        ProductionWorkReport r = workReportMapper.selectById(reportId);
        if (r == null) throw new BusinessException("报工记录不存在");
        checkApprover(r, operatorId);
        int rows = transition(reportId, WorkReportStatusEnum.APPROVED,
                dto == null ? null : dto.getReviewRemark(), operatorName, operatorId);
        if (rows == 0) {
            ProductionWorkReport cur = workReportMapper.selectById(reportId);
            if (cur != null && WorkReportStatusEnum.APPROVED.getCode().equals(cur.getReportStatus())) {
                log.info("审批幂等：报工 {} 已通过", reportId);
                return readService.getById(reportId);
            }
            throw new BusinessException("报工状态已变化，请刷新后重试");
        }
        projectionService.recalculate(r.getExecutionId());
        completeTaskWhenQualified(r, operatorName);
        compareProjectionAndWarn(r);
        log.info("审批通过 reportId={}, executionId={}, reviewer={}", reportId, r.getExecutionId(), operatorName);
        WorkReportVO result = readService.getById(reportId);
        result.setEventPublished(true);
        return result;
    }

    /** 审批事务内按报工 task_id 直达任务；自动完成失败只告警，不改变报工审批语义。 */
    private void completeTaskWhenQualified(ProductionWorkReport report, String operatorName) {
        if (report.getTaskId() == null) {
            log.warn("报工审批后无法自动完成任务：reportId={} 缺少 taskId", report.getReportId());
            return;
        }
        try {
            ProductionTask task = productionTaskMapper.selectById(report.getTaskId());
            if (task == null || ProductionTaskStatus.COMPLETED.getCode().equals(task.getStatus())) {
                return;
            }
            BigDecimal approvedQualified = workReportMapper.sumTaskQualifiedQuantityByStatus(
                    report.getTaskId(), WorkReportStatusEnum.APPROVED.getCode());
            approvedQualified = approvedQualified == null ? BigDecimal.ZERO : approvedQualified;
            if (task.getTaskQuantity() == null || approvedQualified.compareTo(task.getTaskQuantity()) < 0) {
                return;
            }
            int affected = productionTaskMapper.markCompletedByApprovedReports(report.getTaskId(),
                    ProductionTaskStatus.PENDING.getCode(), ProductionTaskStatus.ACTIVE.getCode(),
                    ProductionTaskStatus.COMPLETED.getCode(),
                    WorkReportStatusEnum.APPROVED.getCode(), operatorName);
            if (affected == 1) {
                log.info("报工合格量达标，任务自动完成: reportId={}, taskId={}, approvedQualified={}, taskQuantity={}",
                        report.getReportId(), report.getTaskId(), approvedQualified, task.getTaskQuantity());
            } else {
                log.info("任务自动完成未命中（已完成或状态并发变化）: reportId={}, taskId={}",
                        report.getReportId(), report.getTaskId());
            }
        } catch (Exception e) {
            log.error("报工审批后自动完成任务失败: reportId={}, taskId={}",
                    report.getReportId(), report.getTaskId(), e);
        }
    }

    /** 审批重算后立即对账；异常告警不阻断已经成功的审批事务。 */
    private void compareProjectionAndWarn(ProductionWorkReport report) {
        try {
            String result = projectionService.compareProjection(report.getExecutionId());
            if (!PROJECTION_MISMATCH.equals(result)) {
                return;
            }
            NotificationCreateDTO notification = new NotificationCreateDTO();
            notification.setTitle("生产报工投影对账异常");
            notification.setContent("报工审批后投影仍不一致，请检查。reportId=" + report.getReportId()
                    + "，executionId=" + report.getExecutionId());
            notification.setNotificationType("system");
            notification.setBizType("work_report_projection");
            notification.setBizId(String.valueOf(report.getExecutionId()));
            notification.setReceiverId(SYSTEM_ADMIN_ID);
            notification.setReceiverName("系统管理员");
            notification.setPriority("high");
            notificationService.createNotification(notification);
            log.warn("报工投影对账异常告警已创建: reportId={}, executionId={}",
                    report.getReportId(), report.getExecutionId());
        } catch (Exception e) {
            log.error("报工投影对账或告警创建失败: reportId={}, executionId={}",
                    report.getReportId(), report.getExecutionId(), e);
        }
    }

    @Override
    @Event(value = "production.work-report.rejected", bizId = "#result.reportId",
            bizType = "'production'", condition = "#result.eventPublished", params = {
            "orderNo = #result.orderNo", "executionId = #result.executionId",
            "taskId = #result.taskId", "reportId = #result.reportId",
            "qualifiedQuantity = #result.qualifiedQuantity",
            "defectiveQuantity = #result.defectiveQuantity",
            "reporterId = #result.reporterId", "reporterName = #result.reporterName",
            "receiverId = #result.reporterId"
    })
    @Transactional(rollbackFor = Exception.class)
    public WorkReportVO reject(Long reportId, WorkReportReviewDTO dto, String operatorName, Long operatorId) {
        if (dto == null || dto.getReviewRemark() == null || dto.getReviewRemark().isBlank()) {
            throw new BusinessException("驳回原因必填");
        }
        ProductionWorkReport r = workReportMapper.selectById(reportId);
        if (r == null) throw new BusinessException("报工记录不存在");
        checkApprover(r, operatorId);
        // 质检一致性：已关联 PASS/FAIL 质检的报工禁止驳回（避免 REJECTED 与有效质检结果矛盾）
        syncPendingQualityOrThrow(r);
        int rows = transition(reportId, WorkReportStatusEnum.REJECTED,
                dto.getReviewRemark(), operatorName, operatorId);
        if (rows == 0) {
            ProductionWorkReport cur = workReportMapper.selectById(reportId);
            if (cur != null && WorkReportStatusEnum.REJECTED.getCode().equals(cur.getReportStatus())) {
                log.info("驳回幂等：报工 {} 已驳回", reportId);
                return readService.getById(reportId);
            }
            throw new BusinessException("报工状态已变化，请刷新后重试");
        }
        projectionService.recalculate(r.getExecutionId());
        log.info("审批驳回 reportId={}, executionId={}, reviewer={}", reportId, r.getExecutionId(), operatorName);
        WorkReportVO result = readService.getById(reportId);
        result.setEventPublished(true);
        return result;
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

        // 已完成 execution 禁止撤销（完成后的数量可能已影响 order/库存）
        ProductionOperationExecution exec = executionMapper.selectById(r.getExecutionId());
        if (exec != null && ExecutionStatusEnum.COMPLETED.getValue().equals(exec.getExecutionStatus())) {
            throw new BusinessException("工序已完成，不允许撤销报工");
        }

        // 质检联动 gate：PASS/FAIL 禁撤；PENDING 质检联动逻辑删除
        syncPendingQualityOrThrow(r);

        // 权限：work-report:cancel 权限点 + 业务关系（reporter 本人或超管）
        if (!SecurityUtils.hasPermission("*:*:*") && !SecurityUtils.hasPermission("production:work-report:cancel")) {
            throw new BusinessException("无撤销报工权限");
        }
        if (!SecurityUtils.hasPermission("*:*:*") && (operatorId == null || !operatorId.equals(r.getReporterId()))) {
            throw new BusinessException("只有报工提交人本人或管理员可以撤销");
        }

        // 条件更新：PENDING → CANCELLED（并发保护；APPROVED 不可普通撤销）
        ProductionWorkReport upd = new ProductionWorkReport();
        upd.setReportId(reportId);
        upd.setReportStatus(WorkReportStatusEnum.CANCELLED.getCode());
        upd.setCancelledBy(operatorId);
        upd.setCancelledByName(displayName(operatorName));
        upd.setCancelledAt(LocalDateTime.now());
        upd.setCancelReason(dto.getCancelReason());
        upd.setUpdateBy(operatorName);
        int rows = workReportMapper.update(upd, Wrappers.<ProductionWorkReport>lambdaUpdate()
                .eq(ProductionWorkReport::getReportId, reportId)
                .eq(ProductionWorkReport::getReportStatus, WorkReportStatusEnum.PENDING.getCode()));
        if (rows == 0) {
            ProductionWorkReport cur = workReportMapper.selectById(reportId);
            if (cur != null && WorkReportStatusEnum.CANCELLED.getCode().equals(cur.getReportStatus())) {
                log.info("撤销幂等：报工 {} 已撤销", reportId);
                return readService.getById(reportId);
            }
            throw new BusinessException("报工状态已变化，请刷新后重试");
        }

        projectionService.recalculate(r.getExecutionId());
        log.info("撤销报工 reportId={}, executionId={}, 原因={}", reportId, r.getExecutionId(), dto.getCancelReason());
        return readService.getById(reportId);
    }

    // ==================== helpers ====================

    private static void validateQuantityScale(BigDecimal quantity, String fieldName) {
        if (quantity != null && quantity.scale() > 2) {
            throw new BusinessException(fieldName + "最多 2 位小数");
        }
    }

    private Long parentAssigneeId(ProductionTask task) {
        if (task == null || task.getParentTaskId() == null) {
            return null;
        }
        ProductionTask parent = productionTaskMapper.selectById(task.getParentTaskId());
        return parent == null ? null : parent.getAssigneeId();
    }

    /**
     * 审批关系（P5 业务身份，不使用 admin/*:*:* 全局豁免）：
     * 生产管理者（production:all）可批；普通 Task 由 Parent Task assignee 审批；First Task 仅生产管理者审批。
     */
    private void checkApprover(ProductionWorkReport r, Long operatorId) {
        if (productionRoleResolver.isGlobalProductionScope()) {
            return;
        }
        if (operatorId == null) {
            throw new BusinessException("未登录，无法审批");
        }
        if (r.getPendingReviewerId() != null) {
            if (!operatorId.equals(r.getPendingReviewerId())) {
                throw new BusinessException("仅提交时点审批人可以审批");
            }
            return;
        }
        // 无快照（历史记录或父任务无执行人）仅允许 production:all 兜底。
        throw new BusinessException("该报工需要生产管理员审批，当前未找到可审批人员，请让管理员在 系统管理→基础配置→系统参数→生产配置 中为相关人员开通全部工序的操作范围");
    }

    /** 状态条件更新：PENDING → target（affectedRows=1 才成功；approve/reject 并发只有一个成功，无需 version） */
    private int transition(Long reportId, WorkReportStatusEnum target, String reviewRemark,
                           String operatorName, Long operatorId) {
        ProductionWorkReport upd = new ProductionWorkReport();
        upd.setReportId(reportId);
        upd.setReportStatus(target.getCode());
        upd.setReviewerId(operatorId);
        upd.setReviewerName(displayName(operatorName));
        upd.setReviewTime(LocalDateTime.now());
        upd.setReviewRemark(reviewRemark);
        upd.setUpdateBy(operatorName);
        return workReportMapper.update(upd, Wrappers.<ProductionWorkReport>lambdaUpdate()
                .eq(ProductionWorkReport::getReportId, reportId)
                .eq(ProductionWorkReport::getReportStatus, WorkReportStatusEnum.PENDING.getCode()));
    }

    /**
     * 质检一致性 gate（reject/cancel 共用）：
     * 已关联 PASS/FAIL 质检 → 禁止（避免 REJECTED/CANCELLED 与有效质检结果矛盾）；
     * 仅 PENDING 质检 → 联动逻辑删除（历史可追踪，不留指向已撤销/驳回事实的有效质检单）。
     */
    private void syncPendingQualityOrThrow(ProductionWorkReport r) {
        List<QualityInspectionVO> related = qualityInspectionService.listByWorkReportId(r.getReportId());
        boolean hasFinalized = related.stream().anyMatch(q ->
                QualityInspectionResultEnum.PASS.getCode().equals(q.getResult())
                        || QualityInspectionResultEnum.FAIL.getCode().equals(q.getResult()));
        if (hasFinalized) {
            throw new BusinessException("该报工已关联质检判定结果（PASS/FAIL），不允许驳回/撤销；如需更正请走质检复检");
        }
        for (QualityInspectionVO q : related) {
            qualityInspectionService.delete(q.getInspectionId());
            log.info("报工状态变更联动：逻辑删除 PENDING 质检 {}", q.getInspectionId());
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 姓名快照：优先真实姓名，否则登录名 */
    private String displayName(String operatorName) {
        String realName = SecurityUtils.getRealName();
        return realName != null && !realName.isBlank() ? realName : operatorName;
    }

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

    private String userNameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            var names = jdbcTemplate.query(
                    "SELECT COALESCE(NULLIF(nick_name, ''), user_name) AS display_name FROM sys_user WHERE user_id = ?",
                    (rs, i) -> rs.getString("display_name"), userId);
            return names.isEmpty() ? null : names.get(0);
        } catch (Exception e) {
            log.warn("查询用户失败 userId={}: {}", userId, e.getMessage());
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
