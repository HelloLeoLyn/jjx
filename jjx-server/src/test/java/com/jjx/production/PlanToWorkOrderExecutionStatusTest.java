package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringRoutingItem;
import com.jjx.event.EventPublisher;
import com.jjx.production.domain.converter.ProductionOrderConverter;
import com.jjx.production.domain.dto.ConvertPlanToWorkOrdersDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.product.mapper.EngineeringRoutingItemMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.QualityActionService;
import com.jjx.production.service.TaskNodeService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl;
import com.jjx.production.service.impl.ProductionOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DEV-1081 定向回归测试：
 * 新 PLAN 转工单后，所有 Execution 一律 PENDING；不得自动启动首道工序。
 */
class PlanToWorkOrderExecutionStatusTest {

    private ProductionOrderServiceImpl orderService;
    private ProductionOperationExecutionServiceImpl execService;

    private ProductionOrderMapper orderMapper;
    private ProductionOperationExecutionMapper execMapper;
    private EngineeringRoutingItemMapper routingItemMapper;

    @BeforeEach
    void setUp() throws Exception {
        orderMapper = mock(ProductionOrderMapper.class);
        ProductionOrderConverter converter = mock(ProductionOrderConverter.class);
        execMapper = mock(ProductionOperationExecutionMapper.class);
        routingItemMapper = mock(EngineeringRoutingItemMapper.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);
        com.jjx.production.service.QualityInspectionService qiService =
                mock(com.jjx.production.service.QualityInspectionService.class);
        com.jjx.production.mapper.ProductionQualityInspectionMapper qiMapper =
                mock(com.jjx.production.mapper.ProductionQualityInspectionMapper.class);
        com.jjx.inventory.service.InventoryInboundService inbound =
                mock(com.jjx.inventory.service.InventoryInboundService.class);
        com.jjx.inventory.service.InventoryOutboundService outbound =
                mock(com.jjx.inventory.service.InventoryOutboundService.class);
        com.jjx.inventory.service.OrderStockReserveService stockReserve =
                mock(com.jjx.inventory.service.OrderStockReserveService.class);
        com.jjx.inventory.service.OrderMaterialReserveService materialReserve =
                mock(com.jjx.inventory.service.OrderMaterialReserveService.class);
        com.jjx.sales.mapper.OrderMapper salesOrderMapper =
                mock(com.jjx.sales.mapper.OrderMapper.class);
        // PdfConfigLoader 类层次无法被 Mockito 内联 mock（且转工单路径不触及），传 null
        com.jjx.common.utils.pdf.PdfConfigLoader pdfConfigLoader = null;

        var ctor = ProductionOrderServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        orderService = (ProductionOrderServiceImpl) ctor.newInstance(
                orderMapper, converter, execMapper, routingItemMapper, eventPublisher,
                qiService, qiMapper, inbound, outbound, stockReserve, materialReserve,
                salesOrderMapper, pdfConfigLoader);
        // 反射构造不触发 Spring 注入，补设 ServiceImpl.baseMapper（getById/save/updateById 依赖）
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);

        // 执行服务：6 依赖（execMapper/orderMapper/jdbcTemplate/workReportProjectionService/qualityActionService/taskNodeService）
        ProductionOrderMapper oMapper = mock(ProductionOrderMapper.class);
        JdbcTemplate jdbcTemplate = null; // startExecution 路径不触达 jdbcTemplate，且其类层次无法内联 mock
        WorkReportProjectionService projectionService = mock(WorkReportProjectionService.class);
        QualityActionService qualityActionService = mock(QualityActionService.class);
        TaskNodeService taskNodeService = mock(TaskNodeService.class);

        var ctor2 = ProductionOperationExecutionServiceImpl.class.getDeclaredConstructors()[0];
        ctor2.setAccessible(true);
        execService = (ProductionOperationExecutionServiceImpl) ctor2.newInstance(
                execMapper, oMapper, jdbcTemplate, projectionService, qualityActionService, taskNodeService);
        ReflectionTestUtils.setField(execService, "baseMapper", execMapper);
    }

    // ==================== 定向测试 1：转工单生成 3 道工序 → 全部 PENDING ====================

    @Test
    void convertPlanToWorkOrderCreatesAllExecutionsPending() {
        ProductionOrder plan = new ProductionOrder();
        plan.setOrderId(500L);
        plan.setOrderNo("PLAN-20260821-001");
        plan.setOrderType("PLAN");
        plan.setOrderStatus(2); // 已批准
        plan.setPlannedQuantity(new BigDecimal("100"));
        plan.setRoutingId(1L);
        plan.setBomId(null); // routingId 非空即可通过校验

        when(orderMapper.selectById(500L)).thenReturn(plan);
        when(orderMapper.selectList(any())).thenReturn(Collections.emptyList()); // 有效子工单/历史编号
        when(routingItemMapper.selectList(any())).thenReturn(List.of(
                routingItem(11L, "印刷", 1),
                routingItem(12L, "冲切", 2),
                routingItem(13L, "装配", 3)));
        when(orderMapper.insert(any(ProductionOrder.class))).thenAnswer(inv -> {
            ProductionOrder wo = inv.getArgument(0);
            wo.setOrderId(501L);
            return 1;
        });
        when(orderMapper.updateById(any(ProductionOrder.class))).thenReturn(1);

        ConvertPlanToWorkOrdersDTO dto = new ConvertPlanToWorkOrdersDTO();
        dto.setPlanId(500L);
        ConvertPlanToWorkOrdersDTO.WorkOrderItem item = new ConvertPlanToWorkOrdersDTO.WorkOrderItem();
        item.setProductId(10L);
        item.setProductCode("P01");
        item.setProductName("产品1");
        item.setPlannedQuantity(new BigDecimal("10"));
        item.setPlanStartDate(LocalDate.of(2026, 8, 21));
        item.setPlanEndDate(LocalDate.of(2026, 8, 28));
        dto.setWorkOrders(List.of(item));

        List<Long> ids = orderService.convertPlanToWorkOrders(dto);
        assertEquals(List.of(501L), ids);

        var cap = ArgumentCaptor.forClass(ProductionOperationExecution.class);
        verify(execMapper, times(3)).insert(cap.capture());
        List<ProductionOperationExecution> all = cap.getAllValues();
        assertEquals(3, all.size());
        // 全部 PENDING，且无自动启动时间（不得自动激活首道）
        for (ProductionOperationExecution e : all) {
            assertEquals(ExecutionStatusEnum.PENDING.getCode(), e.getExecutionStatus(),
                    "工序 " + e.getProcessName() + " 应为 PENDING");
            assertNull(e.getActualStartTime(), "工序 " + e.getProcessName() + " 不应有实际开始时间");
        }
    }

    private EngineeringRoutingItem routingItem(Long processId, String name, int order) {
        EngineeringRoutingItem it = new EngineeringRoutingItem();
        it.setRoutingId(1L);
        it.setProcessId(processId);
        it.setProcessName(name);
        it.setMajorCategory("ASSEMBLY");
        it.setProcessOrder(order);
        return it;
    }
}
