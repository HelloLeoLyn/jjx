package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.entity.ProductionQualityInspection;
import com.jjx.production.enums.OrderStatusEnum;
import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.enums.QualityInspectionTypeEnum;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionQualityInspectionMapper;
import com.jjx.production.service.ProductionOrderService;
import com.jjx.production.service.impl.ProductionOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * V1 Release Fix：订单完成路径收口回归测试
 * - updateOrderStatus 禁止直接进入 COMPLETED（防绕过 FQC gate）
 * - 非 COMPLETED 状态流转不受影响
 * - completeOrder 仍走正式 gate：FQC PENDING/FAIL 拒绝；FQC PASS 通过
 */
class OrderCompletionBypassTest {

    private ProductionOrderServiceImpl service;
    private ProductionOrderMapper orderMapper;
    private ProductionQualityInspectionMapper qualityInspectionMapper;
    private com.jjx.inventory.service.InventoryInboundService inboundService;

    // 便于 mock 的内部引用（直接 mock 具体类型）
    private com.jjx.production.mapper.ProductionOperationExecutionMapper executionMapper;

    @BeforeEach
    void setUp() throws Exception {
        orderMapper = mock(ProductionOrderMapper.class);
        executionMapper = mock(com.jjx.production.mapper.ProductionOperationExecutionMapper.class);
        qualityInspectionMapper = mock(ProductionQualityInspectionMapper.class);
        var qualityInspectionService = mock(com.jjx.production.service.QualityInspectionService.class);
        var converter = mock(com.jjx.production.domain.converter.ProductionOrderConverter.class);
        var routingItemMapper = mock(com.jjx.product.mapper.EngineeringRoutingItemMapper.class);
        var eventPublisher = mock(com.jjx.event.EventPublisher.class);
        inboundService = mock(com.jjx.inventory.service.InventoryInboundService.class);
        var outboundService = mock(com.jjx.inventory.service.InventoryOutboundService.class);
        var stockReserveService = mock(com.jjx.inventory.service.OrderStockReserveService.class);
        var materialReserveService = mock(com.jjx.inventory.service.OrderMaterialReserveService.class);
        var salesOrderMapper = mock(com.jjx.sales.mapper.OrderMapper.class);
        var pdfConfigLoader = mock(com.jjx.common.utils.pdf.PdfConfigLoader.class);

        Constructor<?> ctor = ProductionOrderServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (ProductionOrderServiceImpl) ctor.newInstance(
                orderMapper, converter, executionMapper, routingItemMapper, eventPublisher,
                qualityInspectionService, qualityInspectionMapper, inboundService, outboundService,
                stockReserveService, materialReserveService, salesOrderMapper, pdfConfigLoader,
                mock(com.jjx.production.service.ProductionTaskService.class));

        // ServiceImpl 的 getById/updateById 走 baseMapper 字段（protected，反射注入 mock）
        java.lang.reflect.Field bm = null;
        Class<?> c = service.getClass();
        while (c != null && bm == null) {
            try {
                bm = c.getDeclaredField("baseMapper");
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        assertNotNull(bm, "baseMapper 字段应存在");
        bm.setAccessible(true);
        bm.set(service, orderMapper);
    }

    private ProductionOrder inProgressOrder(Long id) {
        ProductionOrder o = new ProductionOrder();
        o.setOrderId(id);
        o.setOrderNo("WO-TEST-001");
        o.setOrderStatus(OrderStatusEnum.IN_PROGRESS.getValue());
        return o;
    }

    private ProductionQualityInspection fqc(String result) {
        ProductionQualityInspection q = new ProductionQualityInspection();
        q.setInspectionId(1L);
        q.setInspectionType(QualityInspectionTypeEnum.FQC.getCode());
        q.setResult(result);
        return q;
    }

    // ==================== 1. updateOrderStatus 防绕过 ====================

    @Test
    void updateOrderStatus_rejectsCompleted() {
        ProductionOrder order = inProgressOrder(1L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any(ProductionOrder.class))).thenReturn(1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateOrderStatus(1L, OrderStatusEnum.COMPLETED.getValue(), null));
        assertTrue(ex.getMessage().contains("完成操作") || ex.getMessage().contains("生产订单完成"),
                "应提示使用完成操作: " + ex.getMessage());
        // 状态未被修改
        verify(orderMapper, never()).updateById(any(ProductionOrder.class));
    }

    @Test
    void updateOrderStatus_normalTransitionStillWorks() {
        ProductionOrder order = inProgressOrder(1L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any(ProductionOrder.class))).thenReturn(1);

        // 进行中 → 暂停（正常流转，不受影响）
        boolean ok = service.updateOrderStatus(1L, OrderStatusEnum.PAUSED.getValue(), "测试暂停");
        assertTrue(ok);
        assertEquals(OrderStatusEnum.PAUSED.getValue(), order.getOrderStatus());
    }

    // ==================== 2. completeOrder FQC gate ====================

    @Test
    void completeOrder_fqcPending_rejected() {
        ProductionOrder order = inProgressOrder(1L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        // 工序全部完成（无未完成工序）
        when(executionMapper.selectCount(any())).thenReturn(0L);
        // 最新 FQC 为 pending
        when(qualityInspectionMapper.selectOne(any())).thenReturn(fqc(QualityInspectionResultEnum.PENDING.getCode()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.completeOrder(1L));
        assertTrue(ex.getMessage().contains("最新完工检验未通过") && ex.getMessage().contains("待检"),
                "FQC PENDING 应拒绝完成: " + ex.getMessage());
        verify(orderMapper, never()).updateById(any(ProductionOrder.class));
    }

    @Test
    void completeOrder_fqcFail_rejected() {
        ProductionOrder order = inProgressOrder(1L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(executionMapper.selectCount(any())).thenReturn(0L);
        when(qualityInspectionMapper.selectOne(any())).thenReturn(fqc(QualityInspectionResultEnum.FAIL.getCode()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.completeOrder(1L));
        assertTrue(ex.getMessage().contains("最新完工检验未通过") && ex.getMessage().contains("不合格"),
                "FQC FAIL 应拒绝完成: " + ex.getMessage());
    }

    @Test
    void completeOrder_fqcPass_succeeds() throws Exception {
        ProductionOrder order = inProgressOrder(1L);
        order.setFinishedQuantity(new BigDecimal("100"));
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(executionMapper.selectCount(any())).thenReturn(0L);
        when(qualityInspectionMapper.selectOne(any())).thenReturn(fqc(QualityInspectionResultEnum.PASS.getCode()));
        // 完工入库幂等（已有入库单返回 null）
        when(orderMapper.updateById(any(ProductionOrder.class))).thenReturn(1);
        // 人工成本核算：无工序执行 → Σ=0，不触发 routing 查询
        when(executionMapper.selectList(any())).thenReturn(new ArrayList<>());

        try (org.mockito.MockedStatic<com.jjx.system.utils.SecurityUtils> mocked =
                     org.mockito.Mockito.mockStatic(com.jjx.system.utils.SecurityUtils.class)) {
            mocked.when(com.jjx.system.utils.SecurityUtils::getUsername).thenReturn("测试员");
            boolean ok = service.completeOrder(1L);
            assertTrue(ok);
            assertEquals(OrderStatusEnum.COMPLETED.getValue(), order.getOrderStatus());
            assertNotNull(order.getActualEndTime());
            verify(inboundService).createFromProduction(1L);
        }
    }

    @Test
    void completeOrder_notInProgress_rejected() {
        ProductionOrder order = inProgressOrder(1L);
        order.setOrderStatus(OrderStatusEnum.PLANNED.getValue());
        when(orderMapper.selectById(1L)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.completeOrder(1L));
        assertTrue(ex.getMessage().contains("当前为已计划"),
                "非进行中应拒绝: " + ex.getMessage());
    }

    @Test
    void completeOrder_unfinishedExecutions_reportsExactCount() {
        ProductionOrder order = inProgressOrder(1L);
        order.setFinishedQuantity(new BigDecimal("100"));
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(executionMapper.selectCount(any())).thenReturn(2L);
        when(qualityInspectionMapper.selectOne(any())).thenReturn(fqc(QualityInspectionResultEnum.PASS.getCode()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.completeOrder(1L));
        assertTrue(ex.getMessage().contains("还有2道工序未完成"), ex.getMessage());
    }

    @Test
    void completeOrder_zeroFinishedQuantity_reportsFqcWritebackRequirement() {
        ProductionOrder order = inProgressOrder(1L);
        order.setFinishedQuantity(BigDecimal.ZERO);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(executionMapper.selectCount(any())).thenReturn(0L);
        when(qualityInspectionMapper.selectOne(any())).thenReturn(fqc(QualityInspectionResultEnum.PASS.getCode()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.completeOrder(1L));
        assertTrue(ex.getMessage().contains("由FQC合格数量回写"), ex.getMessage());
    }
}
