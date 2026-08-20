package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionDispatchLog;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.OrderTraceVO;
import com.jjx.production.domain.vo.ProductionOperationExecutionVO;
import com.jjx.production.domain.vo.ProductionOrderVO;
import com.jjx.production.domain.vo.QualityInspectionVO;
import com.jjx.production.domain.vo.TraceEventVO;
import com.jjx.production.enums.DispatchLogActionEnum;
import com.jjx.production.enums.TraceEventType;
import com.jjx.production.mapper.ProductionDispatchLogMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.ProductionOperationExecutionService;
import com.jjx.production.service.ProductionOrderService;
import com.jjx.production.service.QualityInspectionService;
import com.jjx.production.service.TraceQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * P4-B：生产履历只读查询实现
 * <p>
 * 只读投影：从真实业务表（Order/Execution/DispatchLog/WorkReport/QualityInspection）查询，
 * 转换成统一 TraceEventVO，按业务时间排序形成 Timeline。
 * 不新增 Trace 事实表；不修改任何业务状态；禁止用 updateTime 推断事件。
 * <p>
 * sourceRank（同时间稳定排序权重）：ORDER=1 < EXECUTION=2 < DISPATCH=3 < WORK_REPORT=4 < QUALITY=5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceQueryServiceImpl implements TraceQueryService {

    private final ProductionOrderService orderService;
    private final ProductionOrderMapper orderMapper;
    private final ProductionOperationExecutionService executionService;
    private final ProductionDispatchLogMapper dispatchLogMapper;
    private final ProductionWorkReportMapper workReportMapper;
    private final QualityInspectionService qualityInspectionService;

    @Override
    public OrderTraceVO getOrderTrace(Long orderId) {
        return getOrderTrace(orderId, null, null);
    }

    @Override
    public OrderTraceVO getOrderTrace(Long orderId, String category, Long executionId) {
        if (orderId == null) {
            throw new BusinessException("缺少订单ID");
        }
        ProductionOrderVO orderHeader = orderService.getOrderById(orderId); // 不存在时抛异常（与现有接口一致）
        ProductionOrder order = orderMapper.selectById(orderId); // 实体：携带 createBy/completedBy 快照（VO 未映射）
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }

        List<TraceEventVO> events = new ArrayList<>();
        events.addAll(buildOrderEvents(order));
        events.addAll(buildExecutionEvents(orderId));
        events.addAll(buildDispatchEvents(orderId));
        events.addAll(buildWorkReportEvents(orderId));
        events.addAll(buildQualityEvents(orderId));

        // 过滤
        List<TraceEventVO> filtered = events.stream()
                .filter(e -> category == null || category.equalsIgnoreCase(e.getSourceType()))
                .filter(e -> executionId == null || executionId.equals(e.getExecutionId()))
                .collect(Collectors.toList());

        // 稳定排序：eventTime ASC → sourceRank ASC → sourceId ASC
        filtered.sort(Comparator
                .comparing(TraceEventVO::getEventTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(e -> sourceRank(e.getSourceType()))
                .thenComparing(TraceEventVO::getSourceId, Comparator.nullsLast(Comparator.naturalOrder())));

        OrderTraceVO vo = new OrderTraceVO();
        vo.setOrderHeader(orderHeader);
        vo.setEvents(filtered);
        return vo;
    }

    // ==================== ORDER ====================

    private List<TraceEventVO> buildOrderEvents(ProductionOrder order) {
        List<TraceEventVO> list = new ArrayList<>();

        // ORDER_CREATED：订单创建无独立业务时间字段，用 createTime（审计时间，P4-A 拍板可用并标注）
        TraceEventVO created = base(order.getOrderId(), TraceEventType.ORDER_CREATED, "ORDER");
        created.setEventTime(order.getCreateTime());
        created.setActorId(null);
        created.setActorName(order.getCreateBy());
        created.setTitle("订单创建");
        created.setDescription("订单 " + order.getOrderNo() + " 创建");
        created.setStatus("DRAFT");
        created.setSourceId(order.getOrderId());
        list.add(created);

        // ORDER_STARTED：actualStartTime 非空即发生过开始（业务时间）
        if (order.getActualStartTime() != null) {
            TraceEventVO started = base(order.getOrderId(), TraceEventType.ORDER_STARTED, "ORDER");
            started.setEventTime(order.getActualStartTime());
            started.setTitle("订单开始生产");
            started.setStatus("IN_PROGRESS");
            started.setSourceId(order.getOrderId());
            list.add(started);
        }

        // ORDER_COMPLETED：actualEndTime 非空即发生过完成（业务时间）
        if (order.getActualEndTime() != null) {
            TraceEventVO completed = base(order.getOrderId(), TraceEventType.ORDER_COMPLETED, "ORDER");
            completed.setEventTime(order.getActualEndTime());
            completed.setActorName(order.getCompletedBy());
            completed.setTitle("订单完成");
            completed.setStatus("COMPLETED");
            completed.setSourceId(order.getOrderId());
            list.add(completed);
        }
        return list;
    }

    // ==================== EXECUTION ====================

    private List<TraceEventVO> buildExecutionEvents(Long orderId) {
        List<TraceEventVO> list = new ArrayList<>();
        List<ProductionOperationExecutionVO> executions = executionService.getExecutionsByOrderId(orderId);
        for (ProductionOperationExecutionVO e : executions) {
            String processName = resolveProcessName(e);

            // EXECUTION_STARTED
            if (e.getActualStartTime() != null) {
                TraceEventVO ev = base(orderId, TraceEventType.EXECUTION_STARTED, "EXECUTION");
                ev.setEventTime(e.getActualStartTime());
                ev.setExecutionId(e.getExecutionId());
                ev.setActorId(e.getOperatorId());
                ev.setActorName(e.getOperatorName());
                ev.setTitle("工序开始：" + processName);
                ev.setStatus("EXECUTING");
                ev.setSourceId(e.getExecutionId());
                list.add(ev);
            }

            // EXECUTION_COMPLETED
            if (e.getActualEndTime() != null) {
                TraceEventVO ev = base(orderId, TraceEventType.EXECUTION_COMPLETED, "EXECUTION");
                ev.setEventTime(e.getActualEndTime());
                ev.setExecutionId(e.getExecutionId());
                ev.setActorId(e.getOperatorId());
                ev.setActorName(e.getOperatorName());
                ev.setTitle("工序完成：" + processName);
                ev.setDescription("合格 " + nvl(e.getQualifiedQuantity()) + " / 不良 " + nvl(e.getDefectiveQuantity()));
                ev.setStatus("COMPLETED");
                ev.setSourceId(e.getExecutionId());
                list.add(ev);
            }
        }
        return list;
    }

    /** 工序名：优先复用 execution VO 的 processName；缺失时降级 "工序 {processOrder}" */
    private String resolveProcessName(ProductionOperationExecutionVO e) {
        if (e.getProcessName() != null && !e.getProcessName().isBlank()) {
            return e.getProcessName();
        }
        return "工序 " + (e.getProcessOrder() == null ? e.getExecutionId() : e.getProcessOrder());
    }

    // ==================== DISPATCH（责任流转，来自 dispatch_log） ====================

    private List<TraceEventVO> buildDispatchEvents(Long orderId) {
        List<TraceEventVO> list = new ArrayList<>();
        List<ProductionDispatchLog> logs = dispatchLogMapper.selectList(
                Wrappers.<ProductionDispatchLog>lambdaQuery()
                        .eq(ProductionDispatchLog::getOrderId, orderId)
                        .orderByAsc(ProductionDispatchLog::getCreateTime)
                        .orderByAsc(ProductionDispatchLog::getLogId));
        for (ProductionDispatchLog log : logs) {
            String eventType = mapDispatchAction(log.getAction());
            if (eventType == null) {
                continue; // START 等无对应 eventType，跳过（不伪造）
            }
            TraceEventVO ev = base(orderId, eventType, "DISPATCH");
            ev.setEventTime(log.getCreateTime()); // dispatch_log 无独立业务时间，createTime 即事件时间
            ev.setDispatchId(log.getDispatchId());
            ev.setDispatchNodeId(null); // log 无 node 关联
            ev.setActorId(log.getOperatorId());
            ev.setActorName(log.getOperatorName());
            ev.setTitle(DispatchLogActionEnum.labelOf(log.getAction()));
            ev.setDescription(log.getContent());
            ev.setStatus(log.getAction());
            ev.setSourceId(log.getLogId());
            list.add(ev);
        }
        return list;
    }

    /** dispatch_log action → TraceEventType 映射；无对应事件返回 null（跳过） */
    private String mapDispatchAction(String action) {
        if (action == null) return null;
        switch (action) {
            case "ASSIGN": return TraceEventType.DISPATCH_ASSIGNED;
            case "DELEGATE": return TraceEventType.DISPATCH_DELEGATED;
            case "REASSIGN": return TraceEventType.DISPATCH_REASSIGNED;
            case "RETURN": return TraceEventType.DISPATCH_RETURNED;
            case "REJECT": return TraceEventType.DISPATCH_REJECTED;
            case "COMPLETE": return TraceEventType.DISPATCH_COMPLETED;
            default: return null; // START 等无 eventType
        }
    }

    // ==================== WORK REPORT ====================

    private List<TraceEventVO> buildWorkReportEvents(Long orderId) {
        List<TraceEventVO> list = new ArrayList<>();
        List<ProductionWorkReport> reports = workReportMapper.selectList(
                Wrappers.<ProductionWorkReport>lambdaQuery()
                        .eq(ProductionWorkReport::getOrderId, orderId)
                        .orderByAsc(ProductionWorkReport::getReportTime)
                        .orderByAsc(ProductionWorkReport::getReportId));
        for (ProductionWorkReport r : reports) {
            // SUBMITTED：reportTime 即提交业务时间
            TraceEventVO submitted = base(orderId, TraceEventType.WORK_REPORT_SUBMITTED, "WORK_REPORT");
            submitted.setEventTime(r.getReportTime());
            submitted.setExecutionId(r.getExecutionId());
            submitted.setDispatchId(r.getDispatchId());
            submitted.setDispatchNodeId(r.getDispatchNodeId());
            submitted.setWorkReportId(r.getReportId());
            submitted.setActorId(r.getReporterId());
            submitted.setActorName(r.getReporterName());
            submitted.setTitle("报工提交");
            submitted.setDescription("合格 " + nvl(r.getQualifiedQuantity()) + " / 不良 " + nvl(r.getDefectiveQuantity()));
            submitted.setStatus("SUBMITTED");
            submitted.setSourceId(r.getReportId());
            list.add(submitted);

            // CANCELLED：cancelledAt 非空才生成（真实撤销事实）
            if (r.getCancelledAt() != null) {
                TraceEventVO cancelled = base(orderId, TraceEventType.WORK_REPORT_CANCELLED, "WORK_REPORT");
                cancelled.setEventTime(r.getCancelledAt());
                cancelled.setExecutionId(r.getExecutionId());
                cancelled.setDispatchId(r.getDispatchId());
                cancelled.setDispatchNodeId(r.getDispatchNodeId());
                cancelled.setWorkReportId(r.getReportId());
                cancelled.setActorId(r.getCancelledBy());
                cancelled.setActorName(r.getCancelledByName());
                cancelled.setTitle("报工撤销");
                cancelled.setDescription(r.getCancelReason());
                cancelled.setStatus("CANCELLED");
                cancelled.setSourceId(r.getReportId());
                list.add(cancelled);
            }
        }
        return list;
    }

    // ==================== QUALITY ====================

    private List<TraceEventVO> buildQualityEvents(Long orderId) {
        List<TraceEventVO> list = new ArrayList<>();
        List<QualityInspectionVO> inspections = qualityInspectionService.listByOrderId(orderId);
        for (QualityInspectionVO q : inspections) {
            // QUALITY_CREATED：createTime（P4-B 明确：Quality 创建用 createTime）
            TraceEventVO created = base(orderId, TraceEventType.QUALITY_CREATED, "QUALITY");
            created.setEventTime(q.getCreateTime());
            created.setExecutionId(q.getExecutionId());
            created.setWorkReportId(q.getWorkReportId());
            created.setQualityInspectionId(q.getInspectionId());
            created.setActorId(null);
            created.setActorName(q.getInspector());
            created.setTitle("质检创建（" + q.getInspectionTypeName() + "）");
            created.setDescription("检验单 " + q.getInspectionNo());
            created.setStatus("pending");
            created.setSourceId(q.getInspectionId());
            list.add(created);

            // PASS / FAIL：inspectTime 即判定业务时间
            if (q.getResult() != null && q.getResult().equalsIgnoreCase("pass")) {
                TraceEventVO ev = base(orderId, TraceEventType.QUALITY_PASSED, "QUALITY");
                ev.setEventTime(q.getInspectTime());
                ev.setExecutionId(q.getExecutionId());
                ev.setWorkReportId(q.getWorkReportId());
                ev.setQualityInspectionId(q.getInspectionId());
                ev.setActorId(null);
                ev.setActorName(q.getInspector());
                ev.setTitle("质检合格");
                ev.setDescription("合格 " + nvl(q.getPassQty()) + " / 总数 " + nvl(q.getTotalQty()));
                ev.setStatus("pass");
                ev.setSourceId(q.getInspectionId());
                list.add(ev);
            } else if (q.getResult() != null && q.getResult().equalsIgnoreCase("fail")) {
                TraceEventVO ev = base(orderId, TraceEventType.QUALITY_FAILED, "QUALITY");
                ev.setEventTime(q.getInspectTime());
                ev.setExecutionId(q.getExecutionId());
                ev.setWorkReportId(q.getWorkReportId());
                ev.setQualityInspectionId(q.getInspectionId());
                ev.setActorId(null);
                ev.setActorName(q.getInspector());
                ev.setTitle("质检不合格");
                ev.setDescription("不合格 " + nvl(q.getFailQty()) + " / 总数 " + nvl(q.getTotalQty()));
                ev.setStatus("fail");
                ev.setSourceId(q.getInspectionId());
                list.add(ev);
            }
        }
        return list;
    }

    // ==================== 工具 ====================

    private TraceEventVO base(Long orderId, String eventType, String sourceType) {
        TraceEventVO ev = new TraceEventVO();
        ev.setEventType(eventType);
        ev.setOrderId(orderId);
        ev.setSourceType(sourceType);
        return ev;
    }

    /** sourceRank：ORDER=1 < EXECUTION=2 < DISPATCH=3 < WORK_REPORT=4 < QUALITY=5 */
    private int sourceRank(String sourceType) {
        if (sourceType == null) return 99;
        switch (sourceType) {
            case "ORDER": return 1;
            case "EXECUTION": return 2;
            case "DISPATCH": return 3;
            case "WORK_REPORT": return 4;
            case "QUALITY": return 5;
            default: return 99;
        }
    }

    private String nvl(java.math.BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }
}
