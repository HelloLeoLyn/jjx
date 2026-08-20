package com.jjx.production;

import com.jjx.production.domain.entity.ProductionDispatchLog;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.OrderTraceVO;
import com.jjx.production.domain.vo.ProductionOperationExecutionVO;
import com.jjx.production.domain.vo.ProductionOrderVO;
import com.jjx.production.domain.vo.QualityInspectionVO;
import com.jjx.production.domain.vo.TraceEventVO;
import com.jjx.production.enums.TraceEventType;
import com.jjx.production.mapper.ProductionDispatchLogMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.ProductionOperationExecutionService;
import com.jjx.production.service.ProductionOrderService;
import com.jjx.production.service.QualityInspectionService;
import com.jjx.production.service.impl.TraceQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * P4-B 测试：TraceQueryService 只读聚合
 * - 完整 order trace 聚合（Order/Execution/DispatchLog/WorkReport/Quality）
 * - 事件时间排序稳定 + 相同时间 sourceRank 排序
 * - 空 WorkReport/Quality 正常；无 Dispatch 正常
 * - 历史 execution processName 缺失时降级 "工序 {processOrder}"
 * - WorkReport SUBMITTED/CANCELLED；Quality CREATED/PASS/FAIL；DispatchLog 动作映射
 * - 只读：不产生任何业务数据修改
 */
class TraceQueryServiceTest {

    private TraceQueryServiceImpl service;
    private ProductionOrderService orderService;
    private ProductionOrderMapper orderMapper;
    private ProductionOperationExecutionService executionService;
    private ProductionDispatchLogMapper dispatchLogMapper;
    private ProductionWorkReportMapper workReportMapper;
    private QualityInspectionService qualityInspectionService;

    @BeforeEach
    void setUp() throws Exception {
        orderService = mock(ProductionOrderService.class);
        orderMapper = mock(ProductionOrderMapper.class);
        executionService = mock(ProductionOperationExecutionService.class);
        dispatchLogMapper = mock(ProductionDispatchLogMapper.class);
        workReportMapper = mock(ProductionWorkReportMapper.class);
        qualityInspectionService = mock(QualityInspectionService.class);

        var ctor = TraceQueryServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (TraceQueryServiceImpl) ctor.newInstance(
                orderService, orderMapper, executionService, dispatchLogMapper, workReportMapper, qualityInspectionService);
    }

    private ProductionOrderVO order(Long id, String no) {
        ProductionOrderVO o = new ProductionOrderVO();
        o.setOrderId(id);
        o.setOrderNo(no);
        o.setCreateTime(LocalDateTime.of(2026, 8, 19, 9, 0));
        o.setCreateBy("planner");
        return o;
    }

    /** 实体：携带 createBy/completedBy 快照（VO 未映射）；同时 mock orderService + orderMapper */
    private ProductionOrder orderEntity(Long id, String no) {
        ProductionOrder e = new ProductionOrder();
        e.setOrderId(id);
        e.setOrderNo(no);
        e.setCreateTime(LocalDateTime.of(2026, 8, 19, 9, 0));
        e.setCreateBy("planner");
        return e;
    }

    private void mockOrder(Long id, String no, ProductionOrder entity) {
        ProductionOrderVO vo = order(id, no);
        when(orderService.getOrderById(id)).thenReturn(vo);
        when(orderMapper.selectById(id)).thenReturn(entity);
    }

    private ProductionOperationExecutionVO exec(Long id, Integer processOrder, String processName,
                                                LocalDateTime start, LocalDateTime end) {
        ProductionOperationExecutionVO e = new ProductionOperationExecutionVO();
        e.setExecutionId(id);
        e.setProcessOrder(processOrder);
        e.setProcessName(processName);
        e.setActualStartTime(start);
        e.setActualEndTime(end);
        e.setOperatorId(100L);
        e.setOperatorName("张三");
        e.setQualifiedQuantity(BigDecimal.TEN);
        e.setDefectiveQuantity(BigDecimal.ONE);
        return e;
    }

    private ProductionDispatchLog log(Long logId, Long dispatchId, String action, String content,
                                      LocalDateTime time, String operator) {
        ProductionDispatchLog l = new ProductionDispatchLog();
        l.setLogId(logId);
        l.setDispatchId(dispatchId);
        l.setOrderId(1L);
        l.setAction(action);
        l.setContent(content);
        l.setCreateTime(time);
        l.setOperatorName(operator);
        l.setOperatorId(94L);
        return l;
    }

    private ProductionWorkReport report(Long reportId, LocalDateTime reportTime, LocalDateTime cancelledAt,
                                        String status, String reporter) {
        ProductionWorkReport r = new ProductionWorkReport();
        r.setReportId(reportId);
        r.setOrderId(1L);
        r.setExecutionId(3L);
        r.setDispatchId(1L);
        r.setDispatchNodeId(20L);
        r.setReporterName(reporter);
        r.setReporterId(96L);
        r.setQualifiedQuantity(BigDecimal.valueOf(100));
        r.setDefectiveQuantity(BigDecimal.valueOf(2));
        r.setReportTime(reportTime);
        r.setReportStatus(status);
        r.setCancelledAt(cancelledAt);
        r.setCancelledByName("主任");
        return r;
    }

    private QualityInspectionVO quality(Long inspectionId, String result, LocalDateTime createTime,
                                        LocalDateTime inspectTime, String inspector) {
        QualityInspectionVO q = new QualityInspectionVO();
        q.setInspectionId(inspectionId);
        q.setInspectionNo("QCI202608190001");
        q.setInspectionType("FQC");
        q.setInspectionTypeName("完工检验");
        q.setOrderId(1L);
        q.setExecutionId(3L);
        q.setInspector(inspector);
        q.setResult(result);
        q.setCreateTime(createTime);
        q.setInspectTime(inspectTime);
        q.setTotalQty(BigDecimal.valueOf(100));
        q.setPassQty(BigDecimal.valueOf(100));
        q.setFailQty(BigDecimal.ZERO);
        return q;
    }

    // ==================== 1. 完整聚合 + 排序 ====================

    @Test
    void fullOrderTrace_aggregatesAllSources_sortedByEventTime() {
        ProductionOrder entity = orderEntity(1L, "WO-001");
        entity.setActualStartTime(LocalDateTime.of(2026, 8, 19, 10, 0));
        entity.setActualEndTime(LocalDateTime.of(2026, 8, 19, 16, 0));
        entity.setCompletedBy("prod_manager");
        mockOrder(1L, "WO-001", entity);

        when(executionService.getExecutionsByOrderId(1L)).thenReturn(Arrays.asList(
                exec(1L, 1, "印刷", LocalDateTime.of(2026, 8, 19, 10, 5), LocalDateTime.of(2026, 8, 19, 11, 0)),
                exec(2L, 2, "冲型", LocalDateTime.of(2026, 8, 19, 11, 10), LocalDateTime.of(2026, 8, 19, 12, 0))));

        when(dispatchLogMapper.selectList(any())).thenReturn(Arrays.asList(
                log(1L, 1L, "ASSIGN", "指派：班组=印刷车间", LocalDateTime.of(2026, 8, 19, 10, 2), "prod_manager"),
                log(2L, 1L, "REASSIGN", "改派为冲型车间主任", LocalDateTime.of(2026, 8, 19, 10, 3), "prod_manager")));

        when(workReportMapper.selectList(any())).thenReturn(Arrays.asList(
                report(1L, LocalDateTime.of(2026, 8, 19, 11, 0), null, "SUBMITTED", "张三")));

        when(qualityInspectionService.listByOrderId(1L)).thenReturn(Arrays.asList(
                quality(1L, "pass", LocalDateTime.of(2026, 8, 19, 13, 0), LocalDateTime.of(2026, 8, 19, 13, 5), "质检员")));

        OrderTraceVO trace = service.getOrderTrace(1L);

        assertNotNull(trace.getOrderHeader());
        assertEquals(1L, trace.getOrderHeader().getOrderId());
        List<TraceEventVO> events = trace.getEvents();
        // ORDER: CREATED + STARTED + COMPLETED = 3; EXECUTION: 2x2 = 4; DISPATCH: 2; WORK_REPORT: 1; QUALITY: 2 (CREATED+PASS)
        assertEquals(3 + 4 + 2 + 1 + 2, events.size());

        // 时间升序验证
        for (int i = 1; i < events.size(); i++) {
            assertTrue(!events.get(i).getEventTime().isBefore(events.get(i - 1).getEventTime()),
                    "事件应时间升序: " + events.get(i - 1).getEventType() + " -> " + events.get(i).getEventType());
        }
        // 首事件应为 ORDER_CREATED
        assertEquals(TraceEventType.ORDER_CREATED, events.get(0).getEventType());
        // 末事件应为 ORDER_COMPLETED（16:00 最晚）
        assertEquals(TraceEventType.ORDER_COMPLETED, events.get(events.size() - 1).getEventType());
    }

    // ==================== 2. 相同时间 sourceRank 排序 ====================

    @Test
    void sameEventTime_stableBySourceRank() {
        mockOrder(1L, "WO-001", orderEntity(1L, "WO-001"));

        // 所有事件时间完全相同 10:00
        when(executionService.getExecutionsByOrderId(1L)).thenReturn(Arrays.asList(
                exec(1L, 1, "印刷", LocalDateTime.of(2026, 8, 19, 10, 0), null)));
        when(dispatchLogMapper.selectList(any())).thenReturn(Arrays.asList(
                log(1L, 1L, "ASSIGN", "指派", LocalDateTime.of(2026, 8, 19, 10, 0), "prod_manager")));
        when(workReportMapper.selectList(any())).thenReturn(Arrays.asList(
                report(1L, LocalDateTime.of(2026, 8, 19, 10, 0), null, "SUBMITTED", "张三")));
        when(qualityInspectionService.listByOrderId(1L)).thenReturn(Arrays.asList(
                quality(1L, "pending", LocalDateTime.of(2026, 8, 19, 10, 0), null, "质检员")));

        OrderTraceVO trace = service.getOrderTrace(1L);
        List<String> types = trace.getEvents().stream().map(TraceEventVO::getEventType).toList();

        // ORDER(1) < EXECUTION(2) < DISPATCH(3) < WORK_REPORT(4) < QUALITY(5)
        assertEquals(TraceEventType.ORDER_CREATED, types.get(0));
        assertEquals(TraceEventType.EXECUTION_STARTED, types.get(1));
        assertEquals(TraceEventType.DISPATCH_ASSIGNED, types.get(2));
        assertEquals(TraceEventType.WORK_REPORT_SUBMITTED, types.get(3));
        assertEquals(TraceEventType.QUALITY_CREATED, types.get(4));
    }

    // ==================== 3. 空 WorkReport / Quality 正常 ====================

    @Test
    void emptyWorkReportAndQuality_ok() {
        ProductionOrder entity = orderEntity(1L, "WO-001");
        entity.setActualStartTime(LocalDateTime.of(2026, 8, 19, 10, 0));
        mockOrder(1L, "WO-001", entity);
        when(executionService.getExecutionsByOrderId(1L)).thenReturn(Arrays.asList(
                exec(1L, 1, "印刷", LocalDateTime.of(2026, 8, 19, 10, 5), null)));
        when(dispatchLogMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(workReportMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(qualityInspectionService.listByOrderId(1L)).thenReturn(new ArrayList<>());

        OrderTraceVO trace = service.getOrderTrace(1L);
        // ORDER_CREATED + ORDER_STARTED + EXECUTION_STARTED
        assertEquals(3, trace.getEvents().size());
    }

    // ==================== 4. 无 Dispatch 正常 ====================

    @Test
    void noDispatch_ok() {
        mockOrder(1L, "WO-001", orderEntity(1L, "WO-001"));
        when(executionService.getExecutionsByOrderId(1L)).thenReturn(new ArrayList<>());
        when(dispatchLogMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(workReportMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(qualityInspectionService.listByOrderId(1L)).thenReturn(new ArrayList<>());

        OrderTraceVO trace = service.getOrderTrace(1L);
        assertEquals(1, trace.getEvents().size()); // 仅 ORDER_CREATED
        assertEquals(TraceEventType.ORDER_CREATED, trace.getEvents().get(0).getEventType());
    }

    // ==================== 5. 历史 execution processName 缺失降级 ====================

    @Test
    void missingProcessName_fallbackToProcessOrder() {
        mockOrder(1L, "WO-001", orderEntity(1L, "WO-001"));
        when(executionService.getExecutionsByOrderId(1L)).thenReturn(Arrays.asList(
                exec(3L, 2, null, LocalDateTime.of(2026, 8, 19, 10, 0), LocalDateTime.of(2026, 8, 19, 11, 0))));
        when(dispatchLogMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(workReportMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(qualityInspectionService.listByOrderId(1L)).thenReturn(new ArrayList<>());

        OrderTraceVO trace = service.getOrderTrace(1L);
        TraceEventVO started = trace.getEvents().stream()
                .filter(e -> TraceEventType.EXECUTION_STARTED.equals(e.getEventType())).findFirst().orElseThrow();
        assertTrue(started.getTitle().contains("工序 2"), "应降级为 工序 {processOrder}: " + started.getTitle());
    }

    // ==================== 6. WorkReport SUBMITTED/CANCELLED ====================

    @Test
    void workReportSubmittedAndCancelled_bothEmitted() {
        mockOrder(1L, "WO-001", orderEntity(1L, "WO-001"));
        when(executionService.getExecutionsByOrderId(1L)).thenReturn(new ArrayList<>());
        when(dispatchLogMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(workReportMapper.selectList(any())).thenReturn(Arrays.asList(
                report(1L, LocalDateTime.of(2026, 8, 19, 11, 0), LocalDateTime.of(2026, 8, 19, 11, 30), "CANCELLED", "张三")));
        when(qualityInspectionService.listByOrderId(1L)).thenReturn(new ArrayList<>());

        OrderTraceVO trace = service.getOrderTrace(1L);
        List<TraceEventVO> events = trace.getEvents();
        assertEquals(3, events.size());
        assertEquals(TraceEventType.WORK_REPORT_SUBMITTED, events.get(1).getEventType());
        assertEquals(TraceEventType.WORK_REPORT_CANCELLED, events.get(2).getEventType());
        assertEquals("张三", events.get(1).getActorName());
        assertEquals("主任", events.get(2).getActorName());
    }

    // ==================== 7. Quality CREATED/PASS/FAIL ====================

    @Test
    void qualityCreatedPassFail_emitted() {
        mockOrder(1L, "WO-001", orderEntity(1L, "WO-001"));
        when(executionService.getExecutionsByOrderId(1L)).thenReturn(new ArrayList<>());
        when(dispatchLogMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(workReportMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(qualityInspectionService.listByOrderId(1L)).thenReturn(Arrays.asList(
                quality(1L, "pass", LocalDateTime.of(2026, 8, 19, 13, 0), LocalDateTime.of(2026, 8, 19, 13, 5), "质检员"),
                quality(2L, "fail", LocalDateTime.of(2026, 8, 19, 14, 0), LocalDateTime.of(2026, 8, 19, 14, 5), "质检员")));

        OrderTraceVO trace = service.getOrderTrace(1L);
        List<TraceEventVO> events = trace.getEvents();
        assertEquals(1 + 2 + 2, events.size()); // ORDER_CREATED + 2x(CREATED+PASS/FAIL)
        assertTrue(events.stream().anyMatch(e -> TraceEventType.QUALITY_PASSED.equals(e.getEventType())));
        assertTrue(events.stream().anyMatch(e -> TraceEventType.QUALITY_FAILED.equals(e.getEventType())));
        // pending 只产生 CREATED
        TraceEventVO failCreated = events.stream()
                .filter(e -> TraceEventType.QUALITY_CREATED.equals(e.getEventType()) && e.getSourceId() == 2L)
                .findFirst().orElseThrow();
        assertEquals("pending", failCreated.getStatus());
    }

    // ==================== 8. DispatchLog 动作映射 ====================

    @Test
    void dispatchLogActionMapping() {
        mockOrder(1L, "WO-001", orderEntity(1L, "WO-001"));
        when(executionService.getExecutionsByOrderId(1L)).thenReturn(new ArrayList<>());
        LocalDateTime t = LocalDateTime.of(2026, 8, 19, 10, 0);
        when(dispatchLogMapper.selectList(any())).thenReturn(Arrays.asList(
                log(1L, 1L, "ASSIGN", "指派", t, "a"),
                log(2L, 1L, "DELEGATE", "下派", t, "a"),
                log(3L, 1L, "REASSIGN", "改派", t, "a"),
                log(4L, 1L, "RETURN", "退回", t, "a"),
                log(5L, 1L, "REJECT", "整单退回", t, "a"),
                log(6L, 1L, "COMPLETE", "完成", t, "a"),
                log(7L, 1L, "START", "开始", t, "a")));
        when(workReportMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(qualityInspectionService.listByOrderId(1L)).thenReturn(new ArrayList<>());

        OrderTraceVO trace = service.getOrderTrace(1L);
        List<String> types = trace.getEvents().stream().map(TraceEventVO::getEventType).toList();
        // START 无对应 eventType，跳过；其余 6 个按 sourceId 稳定排序
        assertEquals(TraceEventType.DISPATCH_ASSIGNED, types.get(1));
        assertEquals(TraceEventType.DISPATCH_DELEGATED, types.get(2));
        assertEquals(TraceEventType.DISPATCH_REASSIGNED, types.get(3));
        assertEquals(TraceEventType.DISPATCH_RETURNED, types.get(4));
        assertEquals(TraceEventType.DISPATCH_REJECTED, types.get(5));
        assertEquals(TraceEventType.DISPATCH_COMPLETED, types.get(6));
        assertFalse(types.contains("DISPATCH_STARTED"));
    }

    // ==================== 9. category / executionId 过滤 ====================

    @Test
    void filterByCategoryAndExecutionId() {
        ProductionOrder entity = orderEntity(1L, "WO-001");
        entity.setActualStartTime(LocalDateTime.of(2026, 8, 19, 10, 0));
        mockOrder(1L, "WO-001", entity);
        when(executionService.getExecutionsByOrderId(1L)).thenReturn(Arrays.asList(
                exec(1L, 1, "印刷", LocalDateTime.of(2026, 8, 19, 10, 5), null),
                exec(2L, 2, "冲型", LocalDateTime.of(2026, 8, 19, 11, 0), null)));
        when(dispatchLogMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(workReportMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(qualityInspectionService.listByOrderId(1L)).thenReturn(new ArrayList<>());

        // category=EXECUTION
        OrderTraceVO trace = service.getOrderTrace(1L, "EXECUTION", null);
        assertTrue(trace.getEvents().stream().allMatch(e -> "EXECUTION".equals(e.getSourceType())));
        assertEquals(2, trace.getEvents().size());

        // executionId=2
        OrderTraceVO trace2 = service.getOrderTrace(1L, null, 2L);
        assertEquals(1, trace2.getEvents().size());
        assertEquals(2L, trace2.getEvents().get(0).getExecutionId());
    }

    // ==================== 10. 只读：不产生任何业务数据修改 ====================

    @Test
    void traceIsReadOnly_noWrites() {
        mockOrder(1L, "WO-001", orderEntity(1L, "WO-001"));
        when(executionService.getExecutionsByOrderId(1L)).thenReturn(new ArrayList<>());
        when(dispatchLogMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(workReportMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(qualityInspectionService.listByOrderId(1L)).thenReturn(new ArrayList<>());

        service.getOrderTrace(1L);

        // 只读验证：任何 insert/update/delete 均未被调用（BaseMapper 新签名兼容）
        verify(dispatchLogMapper, never()).insert(any(ProductionDispatchLog.class));
        verify(dispatchLogMapper, never()).update(any(ProductionDispatchLog.class), any());
        verify(dispatchLogMapper, never()).delete(any());
        verify(workReportMapper, never()).insert(any(ProductionWorkReport.class));
        verify(workReportMapper, never()).update(any(ProductionWorkReport.class), any());
        verify(workReportMapper, never()).delete(any());
        verify(orderService, never()).createOrder(any());
        verify(orderService, never()).updateOrder(any());
        verify(qualityInspectionService, never()).delete(any());
        // 确认只调用读方法
        verify(orderService).getOrderById(1L);
        verify(executionService).getExecutionsByOrderId(1L);
        verify(dispatchLogMapper).selectList(any());
        verify(workReportMapper).selectList(any());
        verify(qualityInspectionService).listByOrderId(1L);
    }
}
