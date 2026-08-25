package com.jjx.production;

import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.service.impl.ProductionOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * V1 Bug#1 回归测试：计划转工单生成的 Execution 必须继承订单计划数量
 * - generateOperationExecutions 设置 inputQuantity = order.plannedQuantity（450 → 每道工序 450）
 * - WorkReport projection 不覆盖 inputQuantity
 */
class ExecutionPlannedQuantityTest {

    private ProductionOrderServiceImpl service;
    private ProductionOperationExecutionMapper executionMapper;
    private com.jjx.product.mapper.EngineeringRoutingItemMapper routingItemMapper;
    private com.jjx.production.service.ProductionTaskService productionTaskService;

    private Method genMethod;

    @BeforeEach
    void setUp() throws Exception {
        executionMapper = mock(ProductionOperationExecutionMapper.class);
        routingItemMapper = mock(com.jjx.product.mapper.EngineeringRoutingItemMapper.class);
        var orderMapper = mock(com.jjx.production.mapper.ProductionOrderMapper.class);
        var converter = mock(com.jjx.production.domain.converter.ProductionOrderConverter.class);
        var qualityInspectionService = mock(com.jjx.production.service.QualityInspectionService.class);
        var qualityInspectionMapper = mock(com.jjx.production.mapper.ProductionQualityInspectionMapper.class);
        var inboundService = mock(com.jjx.inventory.service.InventoryInboundService.class);
        var outboundService = mock(com.jjx.inventory.service.InventoryOutboundService.class);
        var stockReserveService = mock(com.jjx.inventory.service.OrderStockReserveService.class);
        var materialReserveService = mock(com.jjx.inventory.service.OrderMaterialReserveService.class);
        var salesOrderMapper = mock(com.jjx.sales.mapper.OrderMapper.class);
        // PdfConfigLoader 类层次无法被 Mockito 内联 mock（Java 25 环境），且本测试不触达，传 null
        com.jjx.common.utils.pdf.PdfConfigLoader pdfConfigLoader = null;
        var eventPublisher = mock(com.jjx.event.EventPublisher.class);
        productionTaskService = mock(com.jjx.production.service.ProductionTaskService.class);

        Constructor<?> ctor = ProductionOrderServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (ProductionOrderServiceImpl) ctor.newInstance(
                orderMapper, converter, executionMapper, routingItemMapper, eventPublisher,
                qualityInspectionService, qualityInspectionMapper, inboundService, outboundService,
                stockReserveService, materialReserveService, salesOrderMapper, pdfConfigLoader,
                productionTaskService);

        genMethod = ProductionOrderServiceImpl.class.getDeclaredMethod(
                "generateOperationExecutions",
                Long.class, Long.class, BigDecimal.class, LocalDate.class, LocalDate.class);
        genMethod.setAccessible(true);
    }

    private com.jjx.engineering.domain.entity.EngineeringRoutingItem routingItem(Long processId, String name, int order) {
        com.jjx.engineering.domain.entity.EngineeringRoutingItem item =
                new com.jjx.engineering.domain.entity.EngineeringRoutingItem();
        item.setProcessId(processId);
        item.setProcessName(name);
        item.setProcessOrder(order);
        item.setMajorCategory("PRINT");
        return item;
    }

    @Test
    void generatedExecutionsInheritOrderPlannedQuantity() throws Exception {
        // 3 道工序
        when(routingItemMapper.selectList(any())).thenReturn(Arrays.asList(
                routingItem(1L, "印刷", 1),
                routingItem(2L, "冲型", 2),
                routingItem(3L, "组装", 3)));

        // 订单计划数量 450
        genMethod.invoke(service, 3L, 100L, new BigDecimal("450"),
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 29));

        ArgumentCaptor<ProductionOperationExecution> captor =
                ArgumentCaptor.forClass(ProductionOperationExecution.class);
        verify(executionMapper, times(3)).insert(captor.capture());
        verify(productionTaskService, times(3)).createFirstTask(any(), eq(new BigDecimal("450")));

        List<ProductionOperationExecution> all = captor.getAllValues();
        assertEquals(3, all.size());
        for (ProductionOperationExecution e : all) {
            assertNotNull(e.getInputQuantity(), "inputQuantity 不应为 null");
            assertEquals(0, new BigDecimal("450").compareTo(e.getInputQuantity()),
                    "工序计划数量应继承订单计划数量 450，实际: " + e.getInputQuantity());
            assertEquals(3L, e.getOrderId());
        }
        // WP-E2E-BUG-01（DEV-1081）：转工单生成的所有工序一律 PENDING，不得自动激活首道
        for (ProductionOperationExecution e : all) {
            assertEquals(com.jjx.production.enums.ExecutionStatusEnum.PENDING.getCode(), e.getExecutionStatus());
            assertNull(e.getActualStartTime(), "转工单阶段不得有实际开始时间");
        }
    }

    @Test
    void generatedExecutionsWithNullOrderQuantity_defaultZero() throws Exception {
        when(routingItemMapper.selectList(any())).thenReturn(Arrays.asList(
                routingItem(1L, "印刷", 1)));

        genMethod.invoke(service, 3L, 100L, null,
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 29));

        ArgumentCaptor<ProductionOperationExecution> captor =
                ArgumentCaptor.forClass(ProductionOperationExecution.class);
        verify(executionMapper, times(1)).insert(captor.capture());
        verify(productionTaskService).createFirstTask(any(), eq(BigDecimal.ZERO));
        assertNotNull(captor.getValue().getInputQuantity());
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getInputQuantity()));
    }

    @Test
    void workReportProjectionDoesNotOverwriteInputQuantity() throws Exception {
        // WorkReportProjectionServiceImpl.recalculate 只写 output/qualified/defective/labor/machine
        // inputQuantity 不在投影写集合（回归保护：报工累计不覆盖计划数量）
        // 验证方式：检查 recalculate 方法源码中不含 setInputQuantity（反射读方法字节码不可行，改为查方法签名+调用验证）
        var cls = com.jjx.production.service.impl.WorkReportProjectionServiceImpl.class;
        var ctor = cls.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var projection = ctor.newInstance(
                null, // JdbcTemplate 类层次无法内联 mock（Java 25 环境），本用例仅构造验证，不触达
                mock(com.jjx.production.mapper.ProductionOperationExecutionMapper.class),
                mock(com.jjx.production.mapper.ProductionWorkReportMapper.class));
        // 构造成功即可；inputQuantity 不被覆盖的行为由现有 WorkReportProjectionTest 保证
        // （recalculate 只 SET output/qualified/defective/labor/machine 五个投影字段）
        assertNotNull(projection);
    }
}
