package com.jjx.production;

import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.impl.ProductionOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * V1 修复：计划重新转工单时工单号不重复
 * - 已有 -01/-02 → 下一张 -03
 * - 已有 -01/-02/-05 → 下一张 -06
 * - 无历史 → -01
 * - 历史编号（含 CANCELLED/COMPLETED/CLOSED）一律不复用（取最大后缀+1）
 */
class WorkOrderNoGenerationTest {

    private ProductionOrderServiceImpl service;
    private ProductionOrderMapper orderMapper;
    private Method genMethod;

    @BeforeEach
    void setUp() throws Exception {
        orderMapper = mock(ProductionOrderMapper.class);
        var executionMapper = mock(com.jjx.production.mapper.ProductionOperationExecutionMapper.class);
        var qualityInspectionService = mock(com.jjx.production.service.QualityInspectionService.class);
        var converter = mock(com.jjx.production.domain.converter.ProductionOrderConverter.class);
        var routingItemMapper = mock(com.jjx.product.mapper.EngineeringRoutingItemMapper.class);
        var eventPublisher = mock(com.jjx.event.EventPublisher.class);
        var inboundService = mock(com.jjx.inventory.service.InventoryInboundService.class);
        var outboundService = mock(com.jjx.inventory.service.InventoryOutboundService.class);
        var stockReserveService = mock(com.jjx.inventory.service.OrderStockReserveService.class);
        var materialReserveService = mock(com.jjx.inventory.service.OrderMaterialReserveService.class);
        var salesOrderMapper = mock(com.jjx.sales.mapper.OrderMapper.class);

        Constructor<?> ctor = ProductionOrderServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (ProductionOrderServiceImpl) ctor.newInstance(
                orderMapper, converter, executionMapper, routingItemMapper, eventPublisher,
                qualityInspectionService,
                mock(com.jjx.production.mapper.ProductionQualityInspectionMapper.class),
                inboundService, outboundService,
                stockReserveService, materialReserveService, salesOrderMapper, mock(com.jjx.production.service.ProductionTaskService.class));

        genMethod = ProductionOrderServiceImpl.class.getDeclaredMethod("generateWorkOrderNo", ProductionOrder.class);
        genMethod.setAccessible(true);
    }

    private ProductionOrder plan(Long id, String no) {
        ProductionOrder p = new ProductionOrder();
        p.setOrderId(id);
        p.setOrderNo(no);
        p.setOrderType("PLAN");
        p.setPlannedQuantity(new BigDecimal("1000"));
        p.setRemainingQuantity(new BigDecimal("1000"));
        return p;
    }

    private ProductionOrder child(String no) {
        ProductionOrder c = new ProductionOrder();
        c.setOrderNo(no);
        c.setOrderType("WORK_ORDER");
        return c;
    }

    private String gen(Long planId, String planNo, List<ProductionOrder> children) throws Exception {
        when(orderMapper.selectList(any())).thenReturn(children);
        return (String) genMethod.invoke(service, plan(planId, planNo));
    }

    @Test
    void existing_01_02_nextIs_03() throws Exception {
        String no = gen(1L, "PL2608200001", Arrays.asList(
                child("WO-PL2608200001-01"),
                child("WO-PL2608200001-02")));
        assertEquals("WO-PL2608200001-03", no);
    }

    @Test
    void existing_01_02_05_nextIs_06() throws Exception {
        String no = gen(1L, "PL2608200001", Arrays.asList(
                child("WO-PL2608200001-01"),
                child("WO-PL2608200001-02"),
                child("WO-PL2608200001-05")));
        assertEquals("WO-PL2608200001-06", no);
    }

    @Test
    void noHistory_firstIs_01() throws Exception {
        String no = gen(1L, "PL2608200001", List.of());
        assertEquals("WO-PL2608200001-01", no);
    }

    @Test
    void cancelledAndCompletedHistory_notReused() throws Exception {
        // 历史编号无论状态（CANCELLED/COMPLETED/CLOSED）都取最大后缀+1，不复用
        String no = gen(1L, "PL2608200001", Arrays.asList(
                child("WO-PL2608200001-07"),
                child("WO-PL2608200001-03")));
        assertEquals("WO-PL2608200001-08", no);
    }
}
