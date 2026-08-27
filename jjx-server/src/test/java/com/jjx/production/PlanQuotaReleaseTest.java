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
 * V1 验收：取消工单释放计划占用回归测试
 * <p>
 * 业务链：Plan 1000 → 转 550 → 转 450 → 剩余 0 → cancel 550 → 剩余 550
 * → cancel 450 → 剩余 1000 → 再转 450 → 剩余 550 → 新 Execution.inputQuantity=450
 * <p>
 * 覆盖：转工单扣减 / 取消回补 / 幂等（重复 cancel 不二次释放）/ 上限（不超过原计划）/ 历史保留
 */
class PlanQuotaReleaseTest {

    private ProductionOrderServiceImpl service;
    private ProductionOrderMapper orderMapper;
    private com.jjx.production.mapper.ProductionOperationExecutionMapper executionMapper;
    private com.jjx.product.mapper.EngineeringRoutingItemMapper routingItemMapper;

    /** 计划行（模拟 DB 状态，updateById 时同步） */
    private ProductionOrder planRow;
    /** 子工单表（selectList 模拟；转出后追加，供动态剩余计算） */
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

        // 注入 ServiceImpl baseMapper
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
    }

    private com.jjx.production.mapper.ProductionQualityInspectionMapper qualityInspectionMapper() {
        return mock(com.jjx.production.mapper.ProductionQualityInspectionMapper.class);
    }

    private ProductionOrder plan(Long id, String no, BigDecimal qty, BigDecimal remaining, Integer status) {
        ProductionOrder p = new ProductionOrder();
        p.setOrderId(id);
        p.setOrderNo(no);
        p.setOrderType("PLAN");
        p.setPlannedQuantity(qty);
        p.setRemainingQuantity(remaining);
        p.setOrderStatus(status);
        p.setRoutingId(100L);
        p.setBomId(200L);
        return p;
    }

    private ProductionOrder workOrder(Long id, String no, Long parentId, BigDecimal qty, Integer status) {
        ProductionOrder w = new ProductionOrder();
        w.setOrderId(id);
        w.setOrderNo(no);
        w.setOrderType("WORK_ORDER");
        w.setParentOrderId(parentId);
        w.setPlannedQuantity(qty);
        w.setRemainingQuantity(qty);
        w.setOrderStatus(status);
        return w;
    }

    private ConvertPlanToWorkOrdersDTO.WorkOrderItem item(Long productId, BigDecimal qty, String code, String name) {
        ConvertPlanToWorkOrdersDTO.WorkOrderItem it = new ConvertPlanToWorkOrdersDTO.WorkOrderItem();
        it.setProductId(productId);
        it.setProductCode(code);
        it.setProductName(name);
        it.setPlannedQuantity(qty);
        it.setPlanStartDate(LocalDate.of(2026, 8, 20));
        it.setPlanEndDate(LocalDate.of(2026, 8, 29));
        return it;
    }

    private ConvertPlanToWorkOrdersDTO dto(Long planId, List<ConvertPlanToWorkOrdersDTO.WorkOrderItem> items) {
        ConvertPlanToWorkOrdersDTO d = new ConvertPlanToWorkOrdersDTO();
        d.setPlanId(planId);
        d.setWorkOrders(items);
        return d;
    }

    // ==================== 完整场景链 ====================

    @Test
    void fullChain_plan1000_convert550_450_cancelBoth_reconvert() {
        planRow = plan(1L, "PL2608200001", new BigDecimal("1000"), new BigDecimal("1000"),
                OrderStatusEnum.APPROVED.getCode());
        when(orderMapper.selectById(1L)).thenAnswer(inv -> planRow);
        when(orderMapper.updateById(any(ProductionOrder.class))).thenAnswer(inv -> {
            planRow = inv.getArgument(0);
            return 1;
        });
        when(executionMapper.insert(any(com.jjx.production.domain.entity.ProductionOperationExecution.class))).thenReturn(1);
        // 工艺路线 3 道工序（转工单时生成 Execution）
        com.jjx.engineering.domain.entity.EngineeringRoutingItem r1 = new com.jjx.engineering.domain.entity.EngineeringRoutingItem();
        r1.setProcessId(1L); r1.setProcessName("印刷"); r1.setProcessOrder(1);
        com.jjx.engineering.domain.entity.EngineeringRoutingItem r2 = new com.jjx.engineering.domain.entity.EngineeringRoutingItem();
        r2.setProcessId(2L); r2.setProcessName("冲型"); r2.setProcessOrder(2);
        com.jjx.engineering.domain.entity.EngineeringRoutingItem r3 = new com.jjx.engineering.domain.entity.EngineeringRoutingItem();
        r3.setProcessId(3L); r3.setProcessName("组装"); r3.setProcessOrder(3);
        when(routingItemMapper.selectList(any())).thenReturn(Arrays.asList(r1, r2, r3));
        // 子工单查询（generateWorkOrderNo / sumEffectiveWorkOrderQuantity）——模拟 CANCELLED 过滤
        when(orderMapper.selectList(any())).thenAnswer(inv -> {
            Object w = inv.getArgument(0);
            if (w instanceof com.baomidou.mybatisplus.core.conditions.query.QueryWrapper) {
                return childrenRows.stream()
                        .filter(child -> !OrderStatusEnum.CANCELLED.getCode().equals(child.getOrderStatus()))
                        .collect(java.util.stream.Collectors.toList());
            }
            return new ArrayList<>(childrenRows);
        });

        // 1. 转 550
        List<Long> ids1 = service.convertPlanToWorkOrders(dto(1L, Arrays.asList(
                item(1L, new BigDecimal("550"), "P001", "产品A"))));
        assertEquals(1, ids1.size());
        assertEquals(0, new BigDecimal("450").compareTo(planRow.getRemainingQuantity()), "转 550 后剩余应 450");
        assertEquals(OrderStatusEnum.APPROVED.getCode(), planRow.getOrderStatus(), "还有剩余可下达，计划保持已批准");
        // 模拟子工单入库（动态剩余计算依赖）——实例稍后由 wo550[0] 接管
        ProductionOrder wo550Row = workOrder(2L, "WO-PL2608200001-01", 1L, new BigDecimal("550"),
                OrderStatusEnum.PLANNED.getCode());
        childrenRows.add(wo550Row);

        // 2. 转 450
        service.convertPlanToWorkOrders(dto(1L, Arrays.asList(
                item(1L, new BigDecimal("450"), "P001", "产品A"))));
        assertEquals(0, new BigDecimal("0").compareTo(planRow.getRemainingQuantity()), "再转 450 后剩余应 0");
        childrenRows.add(workOrder(3L, "WO-PL2608200001-02", 1L, new BigDecimal("450"),
                OrderStatusEnum.PLANNED.getCode()));
        assertEquals(OrderStatusEnum.CLOSED.getCode(), planRow.getOrderStatus(), "全部下达后计划 CLOSED");

        // 3. 超量拦截
        assertThrows(BusinessException.class, () -> service.convertPlanToWorkOrders(dto(1L, Arrays.asList(
                item(1L, new BigDecimal("100"), "P001", "产品A")))));

        // 4. cancel 550 工单 → 释放
        ProductionOrder[] wo550 = { workOrder(2L, "WO-PL2608200001-01", 1L, new BigDecimal("550"),
                OrderStatusEnum.IN_PROGRESS.getCode()) };
        when(orderMapper.selectById(2L)).thenAnswer(inv -> wo550[0]);
        // 同步 childrenRows 引用（动态剩余计算能看到 CANCELLED 状态）
        childrenRows.clear();
        childrenRows.add(wo550[0]);
        when(orderMapper.updateById(any(ProductionOrder.class))).thenAnswer(inv -> {
            Object o = inv.getArgument(0);
            if (o instanceof ProductionOrder po && po.getOrderId() != null && po.getOrderId() == 2L) {
                wo550[0] = po;
            } else if (o instanceof ProductionOrder po && po.getOrderType() != null && "PLAN".equals(po.getOrderType())) {
                planRow = po;
            }
            return 1;
        });
        service.cancelOrder(2L);
        assertEquals(OrderStatusEnum.CANCELLED.getCode(), wo550[0].getOrderStatus());
        assertEquals(0, new BigDecimal("550").compareTo(planRow.getRemainingQuantity()), "cancel 550 后剩余应恢复 550");
        assertEquals(OrderStatusEnum.APPROVED.getCode(), planRow.getOrderStatus(), "计划恢复可下达");

        // 5. cancel 450 工单 → 释放
        ProductionOrder[] wo450 = { workOrder(3L, "WO-PL2608200001-02", 1L, new BigDecimal("450"),
                OrderStatusEnum.IN_PROGRESS.getCode()) };
        when(orderMapper.selectById(3L)).thenAnswer(inv -> wo450[0]);
        when(orderMapper.updateById(any(ProductionOrder.class))).thenAnswer(inv -> {
            Object o = inv.getArgument(0);
            if (o instanceof ProductionOrder po && po.getOrderId() != null && po.getOrderId() == 3L) {
                wo450[0] = po;
            } else if (o instanceof ProductionOrder po && po.getOrderType() != null && "PLAN".equals(po.getOrderType())) {
                planRow = po;
            }
            return 1;
        });
        service.cancelOrder(3L);
        assertEquals(0, new BigDecimal("1000").compareTo(planRow.getRemainingQuantity()), "两张都取消后剩余应恢复 1000");

        // 6. 再转 450 → 新工单
        List<Long> ids2 = service.convertPlanToWorkOrders(dto(1L, Arrays.asList(
                item(1L, new BigDecimal("450"), "P001", "产品A"))));
        assertEquals(1, ids2.size());
        assertEquals(0, new BigDecimal("550").compareTo(planRow.getRemainingQuantity()), "再转 450 后剩余应 550");

        // 7. 新 Execution 每道 inputQuantity=450（Bug#1 修复联动）
        // 前两次转单（550/450）共 insert 6 次，第三次转单（450）insert 3 次 → 取最后 3 次验证
        var captor = org.mockito.ArgumentCaptor.forClass(
                com.jjx.production.domain.entity.ProductionOperationExecution.class);
        verify(executionMapper, times(9)).insert(captor.capture());
        var all = captor.getAllValues();
        assertEquals(9, all.size());
        for (int i = 6; i < 9; i++) {
            assertEquals(0, new BigDecimal("450").compareTo(all.get(i).getInputQuantity()),
                    "新 Execution.inputQuantity 应继承 450，实际: " + all.get(i).getInputQuantity());
        }

        // 8. 旧工单保留历史（CANCELLED 状态）
        assertEquals(OrderStatusEnum.CANCELLED.getCode(), wo550[0].getOrderStatus());
        assertEquals(OrderStatusEnum.CANCELLED.getCode(), wo450[0].getOrderStatus());
    }

    // ==================== 幂等：重复 cancel 不二次释放 ====================

    @Test
    void repeatedCancel_doesNotDoubleRelease() throws Exception {
        planRow = plan(1L, "PL2608200001", new BigDecimal("1000"), new BigDecimal("550"),
                OrderStatusEnum.APPROVED.getCode());
        when(orderMapper.selectById(1L)).thenAnswer(inv -> planRow);
        when(orderMapper.updateById(any(ProductionOrder.class))).thenAnswer(inv -> {
            Object o = inv.getArgument(0);
            if (o instanceof ProductionOrder po && po.getOrderType() != null && "PLAN".equals(po.getOrderType())) {
                planRow = po;
            }
            return 1;
        });

        // 已 CANCELLED 的工单再次走 updateOrderStatus(CANCELLED) → validateStatusTransition 直接 return（同状态），不释放
        ProductionOrder[] cancelledWo = { workOrder(2L, "WO-PL2608200001-01", 1L, new BigDecimal("550"),
                OrderStatusEnum.CANCELLED.getCode()) };
        when(orderMapper.selectById(2L)).thenAnswer(inv -> cancelledWo[0]);
        when(orderMapper.updateById(any(ProductionOrder.class))).thenAnswer(inv -> {
            Object o = inv.getArgument(0);
            if (o instanceof ProductionOrder po && po.getOrderId() != null && po.getOrderId() == 2L) {
                cancelledWo[0] = po;
            } else if (o instanceof ProductionOrder po && po.getOrderType() != null && "PLAN".equals(po.getOrderType())) {
                planRow = po;
            }
            return 1;
        });

        boolean ok = service.updateOrderStatus(2L, OrderStatusEnum.CANCELLED.getCode(), "重复取消");
        assertTrue(ok);
        assertEquals(0, new BigDecimal("550").compareTo(planRow.getRemainingQuantity()),
                "重复 cancel 不应二次释放（剩余保持 550）");
    }

    // ==================== 上限：回补不超过原计划 ====================

    @Test
    void release_neverExceedsOriginalPlanQuantity() throws Exception {
        // 极端：计划剩余已被外部改大（异常态），取消回补也不得超过 planned_quantity
        planRow = plan(1L, "PL2608200001", new BigDecimal("1000"), new BigDecimal("900"),
                OrderStatusEnum.APPROVED.getCode());
        when(orderMapper.selectById(1L)).thenAnswer(inv -> planRow);
        when(orderMapper.updateById(any(ProductionOrder.class))).thenAnswer(inv -> {
            Object o = inv.getArgument(0);
            if (o instanceof ProductionOrder po && po.getOrderType() != null && "PLAN".equals(po.getOrderType())) {
                planRow = po;
            }
            return 1;
        });

        ProductionOrder[] wo = { workOrder(2L, "WO-PL2608200001-01", 1L, new BigDecimal("550"),
                OrderStatusEnum.IN_PROGRESS.getCode()) };
        when(orderMapper.selectById(2L)).thenAnswer(inv -> wo[0]);
        when(orderMapper.updateById(any(ProductionOrder.class))).thenAnswer(inv -> {
            Object o = inv.getArgument(0);
            if (o instanceof ProductionOrder po && po.getOrderId() != null && po.getOrderId() == 2L) {
                wo[0] = po;
            } else if (o instanceof ProductionOrder po && po.getOrderType() != null && "PLAN".equals(po.getOrderType())) {
                planRow = po;
            }
            return 1;
        });

        service.cancelOrder(2L);
        assertEquals(0, new BigDecimal("1000").compareTo(planRow.getRemainingQuantity()),
                "回补不得超过原计划数量 1000，实际: " + planRow.getRemainingQuantity());
    }

    // ==================== 非工单/无父计划不处理 ====================

    @Test
    void nonWorkOrderOrNoParent_skipped() throws Exception {
        planRow = plan(1L, "PL2608200001", new BigDecimal("1000"), new BigDecimal("550"),
                OrderStatusEnum.APPROVED.getCode());
        when(orderMapper.selectById(1L)).thenAnswer(inv -> planRow);
        when(orderMapper.updateById(any(ProductionOrder.class))).thenAnswer(inv -> {
            Object o = inv.getArgument(0);
            if (o instanceof ProductionOrder po && po.getOrderType() != null && "PLAN".equals(po.getOrderType())) {
                planRow = po;
            }
            return 1;
        });

        // 无父计划的工单取消 → 不释放
        ProductionOrder[] orphan = { workOrder(2L, "WO-ORPHAN", null, new BigDecimal("100"),
                OrderStatusEnum.IN_PROGRESS.getCode()) };
        when(orderMapper.selectById(2L)).thenAnswer(inv -> orphan[0]);
        when(orderMapper.updateById(any(ProductionOrder.class))).thenAnswer(inv -> {
            Object o = inv.getArgument(0);
            if (o instanceof ProductionOrder po && po.getOrderId() != null && po.getOrderId() == 2L) {
                orphan[0] = po;
            }
            return 1;
        });
        service.cancelOrder(2L);
        assertEquals(0, new BigDecimal("550").compareTo(planRow.getRemainingQuantity()),
                "无父计划工单取消不应释放计划占用");
    }
}
