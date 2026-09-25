package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionTask;
import com.jjx.production.domain.vo.ReworkTraceVO;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.ProductionTaskStatus;
import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionTaskMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.ProductionReworkTraceService;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.domain.entity.QualityNcrAction;
import com.jjx.quality.mapper.QualityLotMapper;
import com.jjx.quality.mapper.QualityNcrActionMapper;
import com.jjx.quality.mapper.QualityNcrMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 返工链投影（只读）实现 —— dev-20260923-031 一期。
 *
 * <p>口径：一条返工 = 不良单里的一条 REWORK 处置动作 + 它生成的返工工序（execution_type=REWORK）
 * + 该工序的报工 + 复检批（reinspection_lot_id）。本类只读不写，供页面贴「返工身份与进度」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionReworkTraceServiceImpl implements ProductionReworkTraceService {

    private final QualityNcrActionMapper ncrActionMapper;
    private final QualityNcrMapper ncrMapper;
    private final ProductionOperationExecutionMapper executionMapper;
    private final ProductionTaskMapper taskMapper;
    private final ProductionWorkReportMapper workReportMapper;
    private final QualityLotMapper qualityLotMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ReworkTraceVO> trace(Long orderId, Long executionId, Long ncrId) {
        List<QualityNcrAction> actions = reworkActions(orderId, executionId, ncrId);
        List<ReworkTraceVO> result = new ArrayList<>();
        for (QualityNcrAction action : actions) {
            result.add(toTrace(action));
        }
        return result;
    }

    /** 三种入口任选其一：不良单 / 返工工序 / 工单 */
    private List<QualityNcrAction> reworkActions(Long orderId, Long executionId, Long ncrId) {
        LambdaQueryWrapper<QualityNcrAction> query = new LambdaQueryWrapper<QualityNcrAction>()
                .eq(QualityNcrAction::getActionType, "REWORK")
                .orderByAsc(QualityNcrAction::getActionId);
        if (ncrId != null) {
            query.eq(QualityNcrAction::getNcrId, ncrId);
            return ncrActionMapper.selectList(query);
        }
        if (executionId != null) {
            query.eq(QualityNcrAction::getReworkExecutionId, executionId);
            return ncrActionMapper.selectList(query);
        }
        if (orderId == null) {
            return List.of();
        }
        List<QualityNcr> ncrs = ncrMapper.selectList(new LambdaQueryWrapper<QualityNcr>()
                .eq(QualityNcr::getOrderId, orderId));
        if (ncrs.isEmpty()) {
            return List.of();
        }
        query.in(QualityNcrAction::getNcrId, ncrs.stream().map(QualityNcr::getNcrId).toList());
        return ncrActionMapper.selectList(query);
    }

    private ReworkTraceVO toTrace(QualityNcrAction action) {
        ReworkTraceVO vo = new ReworkTraceVO();
        vo.setActionId(action.getActionId());
        vo.setActionStatus(action.getStatus());
        vo.setReworkQuantity(nz(action.getQuantity()));

        QualityNcr ncr = action.getNcrId() == null ? null : ncrMapper.selectById(action.getNcrId());
        if (ncr != null) {
            vo.setNcrId(ncr.getNcrId());
            vo.setNcrNo(ncr.getNcrNo());
            vo.setDefectQuantity(nz(ncr.getDefectQuantity()));
            vo.setDisposedQuantity(nz(ncr.getDisposedQuantity()));
        }

        ProductionOperationExecution execution = action.getReworkExecutionId() == null
                ? null : executionMapper.selectById(action.getReworkExecutionId());
        if (execution != null) {
            vo.setExecutionId(execution.getExecutionId());
            vo.setProcessName(execution.getProcessName());
            vo.setExecutionStatus(execution.getExecutionStatus());
            vo.setReworkRequirement(reworkRequirementOf(execution));
            vo.setReportedQuantity(reportedQuantity(execution.getExecutionId()));
            attachTaskProgress(vo, execution.getExecutionId());
        }

        QualityLot lot = reinspectionLotOf(action, ncr);
        if (lot != null) {
            vo.setReinspectionLotId(lot.getLotId());
            vo.setReinspectionLotNo(lot.getLotNo());
            vo.setReinspectionStatus(lot.getStatus());
            vo.setReinspectionResult(lot.getResult());
            vo.setRecoveredQuantity(nz(lot.getPassQuantity()));
        }

        vo.setStatusText(buildStatusText(action.getStatus(), vo.getExecutionStatus(),
                vo.getReworkQuantity(), vo.getReportedQuantity(), vo.getRecoveredQuantity(), lot,
                vo.getTaskNo(), vo.getTaskStatus(), vo.getTaskAssigneeName(),
                Boolean.TRUE.equals(vo.getHasWorkerTasks())));
        return vo;
    }

    private void attachTaskProgress(ReworkTraceVO vo, Long executionId) {
        List<ProductionTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ProductionTask>()
                .eq(ProductionTask::getExecutionId, executionId)
                .orderByAsc(ProductionTask::getTaskId));
        ProductionTask root = tasks.stream()
                .filter(task -> task.getParentTaskId() == null)
                .findFirst()
                .orElse(null);
        if (root == null) {
            vo.setHasWorkerTasks(false);
            return;
        }
        vo.setTaskId(root.getTaskId());
        vo.setTaskNo(root.getTaskNo());
        vo.setTaskStatus(root.getStatus());
        if (root.getAssigneeId() != null) {
            vo.setTaskAssigneeName(jdbcTemplate.query(
                    "SELECT COALESCE(NULLIF(nick_name, ''), user_name) FROM sys_user WHERE user_id = ?",
                    (ResultSetExtractor<String>) rs -> rs.next() ? rs.getString(1) : null,
                    root.getAssigneeId()));
        }
        vo.setHasWorkerTasks(tasks.stream().anyMatch(task -> root.getTaskId().equals(task.getParentTaskId())
                && !ProductionTaskStatus.CANCELLED.getCode().equals(task.getStatus())));
    }

    private QualityLot reinspectionLotOf(QualityNcrAction action, QualityNcr ncr) {
        if (action.getReinspectionLotId() != null) {
            return qualityLotMapper.selectById(action.getReinspectionLotId());
        }
        if (ncr == null || ncr.getLotId() == null) {
            return null;
        }
        return qualityLotMapper.selectOne(new LambdaQueryWrapper<QualityLot>()
                .eq(QualityLot::getParentLotId, ncr.getLotId())
                .eq(QualityLot::getDelFlag, 0)
                .orderByDesc(QualityLot::getLotId)
                .last("LIMIT 1"));
    }

    /** 该返工工序已审批通过的报工量（合格+不良，即实际修了多少件） */
    private BigDecimal reportedQuantity(Long executionId) {
        List<ProductionWorkReport> reports = workReportMapper.selectList(
                new LambdaQueryWrapper<ProductionWorkReport>()
                        .eq(ProductionWorkReport::getExecutionId, executionId)
                        .eq(ProductionWorkReport::getReportStatus, "APPROVED"));
        BigDecimal total = BigDecimal.ZERO;
        for (ProductionWorkReport report : reports) {
            total = total.add(nz(report.getQualifiedQuantity())).add(nz(report.getDefectiveQuantity()));
        }
        return total;
    }

    /** 返工要求/作业说明：取自返工工序的 custom_process_params（建工序时写入的 NCR 快照） */
    private String reworkRequirementOf(ProductionOperationExecution execution) {
        String raw = execution.getCustomProcessParams();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> map = objectMapper.readValue(raw, Map.class);
            for (String key : List.of("reworkRequirement", "qualityStandard", "description")) {
                Object value = map.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return String.valueOf(value);
                }
            }
        } catch (Exception e) {
            log.debug("返工工序作业说明解析失败: executionId={} err={}", execution.getExecutionId(), e.getMessage());
        }
        return null;
    }

    /**
     * 一句人话：现在到哪一步、下一步谁做什么（方案 §5.3 模板）。
     * 抽成静态方法便于单测覆盖全部分支。
     */
    public static String buildStatusText(String actionStatus, Integer executionStatus, BigDecimal reworkQuantity,
                                         BigDecimal reportedQuantity, BigDecimal recoveredQuantity,
                                         boolean hasReinspectionLot) {
        return buildStatusText(actionStatus, executionStatus, reworkQuantity, reportedQuantity, recoveredQuantity,
                hasReinspectionLot ? new QualityLot() : null, null, null, null, false);
    }

    public static String buildStatusText(String actionStatus, Integer executionStatus, BigDecimal reworkQuantity,
                                         BigDecimal reportedQuantity, BigDecimal recoveredQuantity, QualityLot lot,
                                         String taskNo, String taskStatus, String taskAssigneeName,
                                         boolean hasWorkerTasks) {
        BigDecimal qty = nz(reworkQuantity);
        String quota = plain(qty);
        if ("DONE".equals(actionStatus)) {
            BigDecimal recovered = nz(recoveredQuantity);
            if (recovered.compareTo(qty) >= 0) {
                return "返工复检合格 " + plain(recovered) + "/" + quota + " 件，已回收良品";
            }
            return "返工复检合格 " + plain(recovered) + "/" + quota + " 件，还差 "
                    + plain(qty.subtract(recovered)) + " 件（走让步接收或报废）";
        }
        if (executionStatus == null) {
            return "返工处置已登记，返工工序待生成";
        }
        if (ExecutionStatusEnum.PENDING.getValue().equals(executionStatus)
                || ExecutionStatusEnum.PREPARING.getValue().equals(executionStatus)) {
            if (!hasWorkerTasks) {
                String owner = taskAssigneeName == null || taskAssigneeName.isBlank()
                        ? "一级负责人" : "一级负责人「" + taskAssigneeName + "」";
                return "返工任务「" + displayTaskNo(taskNo) + "」（" + quota + " 件，"
                        + taskStatusLabel(taskStatus) + "）"
                        + "；请" + owner + "到「生产管理 → 派工管理」分配给实际执行人";
            }
            return "返工任务「" + displayTaskNo(taskNo) + "」已派给执行人，请先开工，再提交报工并完成审批";
        }
        if (ExecutionStatusEnum.EXECUTING.getValue().equals(executionStatus)) {
            return "返工进行中，已审批报工 " + plain(nz(reportedQuantity)) + "/" + quota + " 件";
        }
        if (ExecutionStatusEnum.PAUSED.getValue().equals(executionStatus)) {
            return "返工已暂停，由执行人恢复后继续报工";
        }
        if (ExecutionStatusEnum.COMPLETED.getValue().equals(executionStatus)) {
            if (lot == null) {
                return "返工已完工，待生成或关联 FQC 复检批";
            }
            if (QualityInspectionResultEnum.PASS.getCode().equalsIgnoreCase(lot.getResult())) {
                return "复检批「" + displayLotNo(lot.getLotNo()) + "」已合格，可回到不良台账推进返工闭环";
            }
            if (QualityInspectionResultEnum.FAIL.getCode().equalsIgnoreCase(lot.getResult())) {
                return "复检批「" + displayLotNo(lot.getLotNo()) + "」判定不合格，请按复检处置完成后再推进闭环";
            }
            return "返工已完工，复检批「" + displayLotNo(lot.getLotNo()) + "」待检；请到成品检验录入并判定";
        }
        if (ExecutionStatusEnum.SKIPPED.getValue().equals(executionStatus)
                || ExecutionStatusEnum.CANCELLED.getValue().equals(executionStatus)) {
            return "返工工序已跳过或取消——需另走让步接收或报废";
        }
        ExecutionStatusEnum status = ExecutionStatusEnum.getByValue(executionStatus);
        return status == null ? "返工工序状态待核对" : "返工工序「" + status.getLabel() + "」";
    }

    private static String displayTaskNo(String taskNo) {
        return taskNo == null || taskNo.isBlank() ? "待生成任务号" : taskNo;
    }

    private static String displayLotNo(String lotNo) {
        return lotNo == null || lotNo.isBlank() ? "待生成批号" : lotNo;
    }

    private static String taskStatusLabel(String taskStatus) {
        if (ProductionTaskStatus.PENDING.getCode().equals(taskStatus)) return "待派工";
        if (ProductionTaskStatus.ACTIVE.getCode().equals(taskStatus)) return "进行中";
        if (ProductionTaskStatus.COMPLETED.getCode().equals(taskStatus)) return "已完成";
        return "";
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String plain(BigDecimal value) {
        return nz(value).stripTrailingZeros().toPlainString();
    }
}
