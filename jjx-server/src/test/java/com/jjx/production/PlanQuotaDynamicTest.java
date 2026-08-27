package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.ConvertPlanToWorkOrdersDTO;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.enums.OrderStatusEnum;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.impl.ProductionOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WP-E-BUG-01 回归测试：计划剩余可下达数量必须动态计算
 * <p>
 * Bug：PLAN PL2608200002 计划 1000、无子工单，但 DB remaining_quantity=0（脏值），
 * 转工单 600 被后端误判剩余 0 拒绝。
 * <p>
 * 修复规则：remaining = plannedQuantity - Σ(有效子工单 plannedQuantity)
 * （有效 = WORK_ORDER 非 CANCELLED；取消的工单不占额度）
 * <p>
 * 验证：1000 → 下达600成功剩400 → 再下达400剩0 → 再下达应拒绝（超量）
 */
class PlanQuotaDynamicTest {

    private ProductionOrderServiceImpl service;
    private ProductionOrderMapper orderMapper;
    private com.jjx.production.mapper.ProductionOperationExecutionMapper executionMapper;
    private com.jjx.product.mapper.EngineeringRoutingItemMapper routingItemMapper;

    /** 计划行（模拟 DB 状态） */
    private ProductionOrder planRow;
    /** 模拟子工单表（orderMapper.selectList 返回） */
    private final List<ProductionOrder> childrenRows = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        orderMapper = mock(ProductionOrderMapper.class);
        executionMapper = mock(com.jjx.production.mapper.ProductionOperationExecutionMapper.class);
        var qualityInspectionService = mock(com.jjx.production.service.QualityInspectionService.class);
        var converter = mock(com.jjx.production.domain.converter.ProductionOrderConverter.class);
        this.routingItemMapper = mock(com.jjx.product.mapper.EngineeringRoutingItemMapper.class);
        var eventPublisher = mock(com.jjx.event.EventPublisher.class);
        var inboundService = mock(com.jjx.inventory.service.InventoryInboundService.class);
        var outboundService = mock(com.jjx.inventory.service.InventoryOutboundService.class);
        var stockReserveService = mock(com.jjx.inventory.service.OrderStockReserveService.class);
        var materialReserveService = mock(com.jjx.inventory.service.OrderMaterialReserveService.class);
        var salesOrderMapper = mock(com.jjx.sales.mapper.OrderMapper.class);
        var pdfConfigLoader = mock(com.jjx.common.utils.pdf.PdfConfigLoader.class);

        Constructor<?> ctor = ProductionOrderServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (ProductionOrderServiceImpl) ctor.newInstance(
                orderMapper, converter, executionMapper, this.routingItemMapper, eventPublisher,
                qualityInspectionService, qualityInspectionMapper(),
                inboundService, outboundService,
                stockReserveService, materialReserveService, salesOrderMapper, pdfConfigLoader,
                mock(com.jjx.production.service.ProductionTaskService.class));

        java.lang.reflect.Field bm = null;
        Class<?> c = service.getClass();
        while (c != null && bm == null) {
            try {
                bm = c.getDeclaredField("baseMapper");
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        bm.setAccessible(true);
        bm.set(service, orderMapper);

        // selectList：子工单查询（QueryWrapper 无 lambda cache 问题，可解析）——模拟 CANCELLED 过滤
        when(orderMapper.selectList(any())).thenAnswer(inv -> {
            Object w = inv.getArgument(0);
            if (w instanceof com.baomidou.mybatisplus.core.conditions.query.QueryWrapper) {
                // 子工单查询（generateWorkOrderNo / sumEffectiveWorkOrderQuantity）
                return childrenRows.stream()
                        .filter(child -> !OrderStatusEnum.CANCELLED.getCode().equals(child.getOrderStatus()))
                        .collect(java.util.stream.Collectors.toList());
            }
            return new ArrayList<>(childrenRows);
        });
        when(orderMapper.selectById(1L)).thenAnswer(inv -> planRow);
        when(orderMapper.updateById(any(ProductionOrder.class))).thenAnswer(inv -> {
            ProductionOrder po = inv.getArgument(0);
            if (po != null && "PLAN".equals(po.getOrderType())) {
                planRow = po;
            }
            return 1;
        });
        when(executionMapper.insert(any(com.jjx.production.domain.entity.ProductionOperationExecution.class))).thenReturn(1);
        // 工艺路线 1 道工序
        com.jjx.engineering.domain.entity.EngineeringRoutingItem r1 =
                new com.jjx.engineering.domain.entity.EngineeringRoutingItem();
        r1.setProcessId(1L); r1.setProcessName("印刷"); r1.setProcessOrder(1);
        when(routingItemMapper.selectList(any())).thenReturn(Arrays.asList(r1));
    }

    private com.jjx.production.mapper.ProductionQualityInspectionMapper qualityInspectionMapper() {
        return mock(com.jjx.production.mapper.ProductionQualityInspectionMapper.class);
    }

    private ProductionOrder plan(Long id, BigDecimal qty, BigDecimal dirtyRemaining, Integer status) {
        ProductionOrder p = new ProductionOrder();
        p.setOrderId(id);
        p.setOrderNo("PL2608200002");
        p.setOrderType("PLAN");
        p.setPlannedQuantity(qty);
        p.setRemainingQuantity(dirtyRemaining); // 脏值（Bug 复现：0）
        p.setOrderStatus(status);
        p.setRoutingId(100L);
        p.setBomId(200L);
        return p;
    }

    private ConvertPlanToWorkOrdersDTO.WorkOrderItem item(Long productId, BigDecimal qty) {
        ConvertPlanToWorkOrdersDTO.WorkOrderItem it = new ConvertPlanToWorkOrdersDTO.WorkOrderItem();
        it.setProductId(productId);
        it.setProductCode("P001");
        it.setProductName("产品A");
        it.setPlannedQuantity(qty);
        it.setPlanStartDate(LocalDate.of(2026, 8, 20));
        it.setPlanEndDate(LocalDate.of(2026, 8, 29));
        return it;
    }

    private ConvertPlanToWorkOrdersDTO dto(List<ConvertPlanToWorkOrdersDTO.WorkOrderItem> items) {
        ConvertPlanToWorkOrdersDTO d = new ConvertPlanToWorkOrdersDTO();
        d.setPlanId(1L);
        d.setWorkOrders(items);
        return d;
    }

    // ==================== WP-E-BUG-01 主场景 ====================

    @Test
    void dirtyRemainingZero_noChildren_convert600_success() {
        // Bug 复现：DB remaining_quantity=0（脏），但无子工单 → 动态剩余应为 1000
        planRow = plan(1L, new BigDecimal("1000"), BigDecimal.ZERO, OrderStatusEnum.APPROVED.getCode());
        childrenRows.clear();

        List<Long> ids = service.convertPlanToWorkOrders(dto(Arrays.asList(
                item(1L, new BigDecimal("600")))));
        assertEquals(1, ids.size(), "脏 remaining=0 但有 1000 可下达，转 600 应成功");

        // 动态剩余 = 1000 - 600 = 400（持久字段被同步为 400）
        assertEquals(0, new BigDecimal("400").compareTo(planRow.getRemainingQuantity()), "转后剩余应 400");
        assertEquals(OrderStatusEnum.APPROVED.getCode(), planRow.getOrderStatus(), "有剩余保持已批准");
    }

    @Test
    void convert600_then400_thenOverRejected() {
        planRow = plan(1L, new BigDecimal("1000"), BigDecimal.ZERO, OrderStatusEnum.APPROVED.getCode());
        childrenRows.clear();

        // 1. 转 600 成功
        service.convertPlanToWorkOrders(dto(Arrays.asList(item(1L, new BigDecimal("600")))));
        // 模拟子工单已入库（转工单 save 会写库，测试里补一行）
        ProductionOrder wo1 = new ProductionOrder();
        wo1.setOrderId(11L);
        wo1.setOrderNo("WO-PL2608200002-01");
        wo1.setOrderType("WORK_ORDER");
        wo1.setParentOrderId(1L);
        wo1.setPlannedQuantity(new BigDecimal("600"));
        wo1.setOrderStatus(OrderStatusEnum.PLANNED.getCode());
        childrenRows.add(wo1);

        // 2. 再转 400 成功（动态剩余 = 1000 - 600 = 400）
        service.convertPlanToWorkOrders(dto(Arrays.asList(item(1L, new BigDecimal("400")))));
        ProductionOrder wo2 = new ProductionOrder();
        wo2.setOrderId(12L);
        wo2.setOrderNo("WO-PL2608200002-02");
        wo2.setOrderType("WORK_ORDER");
        wo2.setParentOrderId(1L);
        wo2.setPlannedQuantity(new BigDecimal("400"));
        wo2.setOrderStatus(OrderStatusEnum.PLANNED.getCode());
        childrenRows.add(wo2);
        assertEquals(OrderStatusEnum.CLOSED.getCode(), planRow.getOrderStatus(), "全部下达后计划 CLOSED");

        // 3. 再转 100 → 超量拒绝（动态剩余 = 1000 - 1000 = 0）
        //    注：正常业务中 CLOSED 计划前端不再提供转单；此处将计划恢复 APPROVED 以验证超量判定本身
        planRow.setOrderStatus(OrderStatusEnum.APPROVED.getCode());
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.convertPlanToWorkOrders(dto(Arrays.asList(item(1L, new BigDecimal("100"))))));
        assertTrue(ex.getMessage().contains("超过"), ex.getMessage());
    }

    @Test
    void cancelledWorkOrder_notCounted_quotaRestored() {
        // 取消的工单不占额度：转 600 → cancel → 动态剩余恢复 1000（即使 DB remaining 未正确回补）
        planRow = plan(1L, new BigDecimal("1000"), BigDecimal.ZERO, OrderStatusEnum.APPROVED.getCode());
        childrenRows.clear();

        service.convertPlanToWorkOrders(dto(Arrays.asList(item(1L, new BigDecimal("600")))));
        ProductionOrder wo1 = new ProductionOrder();
        wo1.setOrderId(11L);
        wo1.setOrderNo("WO-PL2608200002-01");
        wo1.setOrderType("WORK_ORDER");
        wo1.setParentOrderId(1L);
        wo1.setPlannedQuantity(new BigDecimal("600"));
        wo1.setOrderStatus(OrderStatusEnum.CANCELLED.getCode()); // 已取消
        childrenRows.add(wo1);

        // 动态剩余 = 1000 - 0（无有效子工单）= 1000 → 可再转 600
        List<Long> ids2 = service.convertPlanToWorkOrders(dto(Arrays.asList(item(1L, new BigDecimal("600")))));
        assertEquals(1, ids2.size(), "取消的工单不占额度，可重新下达 600");
        assertEquals(0, new BigDecimal("400").compareTo(planRow.getRemainingQuantity()),
                "600(取消不占) - 新600 → 剩400");
    }
}
