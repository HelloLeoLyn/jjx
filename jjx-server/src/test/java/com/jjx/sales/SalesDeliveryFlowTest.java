package com.jjx.sales;

import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.event.InventoryEventBridge;
import com.jjx.inventory.service.InventoryOutboundService;
import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.enums.QualityReviewStatusEnum;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.enums.QualityLotStatusEnum;
import com.jjx.quality.mapper.QualityLotMapper;
import com.jjx.quality.service.QualityLotService;
import com.jjx.quality.service.QualityOqcService;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.enums.*;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.impl.OrderStatusServiceImpl;
import com.jjx.sales.service.impl.SalesDeliveryServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.jjx.sales.service.SalesDeliveryWorkflowService;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.OrderStockReserveService;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesDeliveryFlowTest {
    @Mock OrderMapper orderMapper;
    @Mock SalesDeliveryMapper salesDeliveryMapper;
    @Mock SalesDeliveryItemMapper salesDeliveryItemMapper;
    @Mock SalesOrderProductMapper orderProductMapper;
    @Mock QualityLotMapper qualityLotMapper;
    @Mock QualityLotService qualityLotService;
    @Mock QualityOqcService qualityOqcService;
    @Mock InventoryOutboundService outboundService;
    @Mock RedisSequenceService redisSequenceService;
    @Mock EventPublisher eventPublisher;
    @Mock com.jjx.sales.service.DeliveryCapacityService capacityService;
    @Mock CustomerMapper customerMapper;
    @Mock DeliveryArrangeMapper arrangeMapper;
    @Mock InventoryOutboundOrderMapper outboundMapper;
    @Mock InventoryOutboundItemMapper outboundItemMapper;
    @Mock InventoryItemMapper inventoryItemMapper;
    @Mock OrderStockReserveService reserveService;
    @InjectMocks SalesDeliveryWorkflowService workflow;
    @BeforeEach void sharedWorkflow() {
        lenient().when(capacityService.capacities(anyCollection(),anyBoolean())).thenReturn(Map.of(26L,new com.jjx.sales.service.DeliveryCapacityService.Capacity(BigDecimal.valueOf(10000),Map.of(),BigDecimal.valueOf(10000))));
        ReflectionTestUtils.setField(service,"deliveryWorkflow",workflow);
        ReflectionTestUtils.setField(statusService,"deliveryWorkflow",workflow);
    }
    @InjectMocks SalesDeliveryServiceImpl service;
    @InjectMocks OrderStatusServiceImpl statusService;

    private SalesOrder order() {
        SalesOrder o = new SalesOrder();
        o.setOrderId(1L); o.setOrderNo("SO261007001"); o.setOrderStatus(SalesOrderStatusEnum.IN_PRODUCTION.getValue());
        o.setTotalQuantity(3); o.setCustomerId(8L); o.setDeliveryAddress("苏州"); o.setTotalAmount(BigDecimal.valueOf(30)); o.setCustomerName("测试客户"); return o;
    }
    private SalesDelivery delivery(SalesDeliveryStatusEnum status) {
        SalesDelivery d = new SalesDelivery();
        d.setDeliveryId(20L); d.setDeliveryNo("DL261008001"); d.setOrderId(1L);
        d.setDeliveryStatus(status.getValue()); d.setTotalQuantity(2); return d;
    }
    private SalesOrderProduct product(int quantity) {
        SalesOrderProduct p = new SalesOrderProduct();
        p.setId(10L); p.setOrderId(1L); p.setProductId(26L); p.setProductCode("JST003MEOO");
        p.setQuantity(quantity); p.setUnitPrice(BigDecimal.TEN); p.setAmount(BigDecimal.valueOf(quantity * 10L)); return p;
    }
    private SalesDeliveryItem line() {
        SalesDeliveryItem l = new SalesDeliveryItem();
        l.setItemId(30L); l.setDeliveryId(20L); l.setOrderProductId(10L); l.setProductId(26L);
        l.setProductCode("JST003MEOO"); l.setQuantity(2); return l;
    }
    private QualityLot pass() {
        QualityLot l = new QualityLot();
        l.setLotId(11L); l.setLotNo("QL261008011"); l.setSourceItemId(30L);
        l.setResult(QualityInspectionResultEnum.PASS.getCode()); l.setStatus(QualityLotStatusEnum.JUDGED.getCode());
        l.setLotQuantity(BigDecimal.valueOf(2)); l.setInspectedQuantity(BigDecimal.ONE); l.setVersion(1); return l;
    }
    private void pending() {
        when(salesDeliveryMapper.selectByIdForUpdate(20L)).thenReturn(delivery(SalesDeliveryStatusEnum.PENDING));
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order());
        when(salesDeliveryItemMapper.selectList(any())).thenReturn(List.of(line()));
        when(orderProductMapper.selectBatchIds(any())).thenReturn(List.of(product(3)));
    }
    private void released(int ordered) {
        pending(); when(qualityLotMapper.selectList(any())).thenReturn(List.of(pass()));
        when(salesDeliveryMapper.selectBatchIds(any())).thenReturn(List.of(delivery(SalesDeliveryStatusEnum.PENDING)));
        when(orderProductMapper.selectBatchIds(any())).thenReturn(List.of(product(ordered)));
    }
    @Test void createsPendingAndOqcWithoutShippingOrPostingInventory() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order());
        when(orderProductMapper.selectList(any())).thenReturn(List.of(product(3)));
        when(orderProductMapper.selectBatchIds(any())).thenReturn(List.of(product(3)));
        when(redisSequenceService.generateBusinessNumberByType(anyString(), anyString(), anyString(), anyInt())).thenReturn("DL261008001");
        when(salesDeliveryMapper.insert(any(SalesDelivery.class))).thenAnswer(inv -> { ((SalesDelivery) inv.getArgument(0)).setDeliveryId(20L); return 1; });
        when(salesDeliveryItemMapper.insert(any(SalesDeliveryItem.class))).thenAnswer(inv -> { ((SalesDeliveryItem) inv.getArgument(0)).setItemId(30L); return 1; });
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) { statusService.shipOrder(1L, null); }
        ArgumentCaptor<SalesDelivery> record = ArgumentCaptor.forClass(SalesDelivery.class);
        verify(salesDeliveryMapper).insert(record.capture());
        assertEquals(SalesDeliveryStatusEnum.PENDING.getValue(), record.getValue().getDeliveryStatus());
        verify(qualityOqcService).prepareLot(any()); verify(qualityLotService).createLot(any());
        verify(orderMapper, never()).updateStatusWithCheck(any(), any(), any()); verifyNoInteractions(outboundService, eventPublisher);
    }
    @Test void pendingQuantityPreventsCreatingAnotherFullDelivery() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order());
        when(orderProductMapper.selectList(any())).thenReturn(List.of(product(2)));
        when(salesDeliveryMapper.selectBatchIds(any())).thenReturn(List.of(delivery(SalesDeliveryStatusEnum.PENDING)));
        when(salesDeliveryItemMapper.selectList(any())).thenReturn(List.of(line()));
        assertThrows(BusinessException.class, () -> statusService.shipOrder(1L, null));
        verify(salesDeliveryMapper, never()).insert(any(SalesDelivery.class));
    }
    @Test void missingOqcBlocksBeforeInventory() {
        pending(); when(qualityLotMapper.selectList(any())).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.confirmShipment(20L)); verifyNoInteractions(outboundService);
        verify(salesDeliveryMapper, never()).updateById(any(SalesDelivery.class));
    }
    @Test void newerFailedReinspectionBlocksEvenIfOldVersionPassed() {
        pending(); QualityLot latest = pass(); latest.setVersion(2); latest.setResult(QualityInspectionResultEnum.FAIL.getCode());
        when(qualityLotMapper.selectList(any())).thenReturn(List.of(latest, pass()));
        assertThrows(BusinessException.class, () -> service.confirmShipment(20L)); verifyNoInteractions(outboundService);
    }
    @Test void pendingReviewBlocksDespitePassResult() {
        pending(); QualityLot lot = pass(); lot.setReviewStatus(QualityReviewStatusEnum.PENDING.getCode());
        when(qualityLotMapper.selectList(any())).thenReturn(List.of(lot));
        assertThrows(BusinessException.class, () -> service.confirmShipment(20L)); verifyNoInteractions(outboundService);
    }
    @Test void inspectingLotCannotBeReleasedJustByChangingItsResult() {
        pending(); QualityLot lot = pass(); lot.setStatus(QualityLotStatusEnum.INSPECTING.getCode());
        when(qualityLotMapper.selectList(any())).thenReturn(List.of(lot));
        assertThrows(BusinessException.class, () -> service.confirmShipment(20L)); verifyNoInteractions(outboundService);
    }
    @Test void salesArrangementDoesNotPrematurelyShipOrPostQuantity() {
        released(3); when(outboundService.createFromSalesByDelivery(20L)).thenReturn(40L);
        service.confirmShipment(20L);
        verify(outboundService).createFromSalesByDelivery(20L);
        verify(salesDeliveryMapper,never()).updateById(any(SalesDelivery.class));
        verify(orderMapper,never()).updateById(any(SalesOrder.class));
        verifyNoInteractions(eventPublisher);
    }
    @Test void actualWarehouseShipmentRecomputesPartialOrder() {
        when(orderProductMapper.selectBatchIds(any())).thenReturn(List.of(product(3)));
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order());
        when(salesDeliveryItemMapper.selectList(any())).thenReturn(List.of(line()));
        when(orderProductMapper.selectList(any())).thenReturn(List.of(product(3)));
        when(salesDeliveryMapper.selectBatchIds(any())).thenReturn(List.of(delivery(SalesDeliveryStatusEnum.SHIPPED)));
        when(salesDeliveryMapper.updateById(any(SalesDelivery.class))).thenReturn(1);
        when(orderMapper.updateById(any(SalesOrder.class))).thenReturn(1);
        when(salesDeliveryMapper.selectById(20L)).thenReturn(delivery(SalesDeliveryStatusEnum.SHIPPED));
        when(orderProductMapper.selectById(10L)).thenReturn(product(3));
        when(orderMapper.selectById(1L)).thenReturn(order());
        workflow.completeShipment(20L);
        ArgumentCaptor<SalesOrder> patch=ArgumentCaptor.forClass(SalesOrder.class);
        verify(orderMapper).updateById(patch.capture());
        assertEquals(2,patch.getValue().getShippedQuantity());
        assertNotEquals(SalesOrderStatusEnum.SHIPPED.getValue(),patch.getValue().getOrderStatus());
    }
    @Test void cumulativeOverShipmentIsRejectedBeforePosting() {
        released(1); assertThrows(BusinessException.class, () -> service.confirmShipment(20L)); verifyNoInteractions(outboundService);
    }
    @Test void inventoryFailureCannotMarkDeliveryShippedOrPublishEvent() {
        released(3); when(outboundService.createFromSalesByDelivery(20L)).thenThrow(new BusinessException("库存不足"));
        assertThrows(BusinessException.class, () -> service.confirmShipment(20L));
        verify(salesDeliveryMapper, never()).updateById(any(SalesDelivery.class)); verify(orderMapper, never()).updateById(any(SalesOrder.class)); verifyNoInteractions(eventPublisher);
    }
    @Test void repeatedConfirmationCannotPostInventoryAgain() {
        when(salesDeliveryMapper.selectByIdForUpdate(20L)).thenReturn(delivery(SalesDeliveryStatusEnum.SHIPPED));
        assertThrows(BusinessException.class, () -> service.confirmShipment(20L)); verifyNoInteractions(outboundService);
    }
    @Test void pendingOrRejectedDeliveryCannotBeReceived() {
        for (SalesDeliveryStatusEnum status : List.of(SalesDeliveryStatusEnum.PENDING, SalesDeliveryStatusEnum.REJECTED, SalesDeliveryStatusEnum.RECEIVED)) {
            when(salesDeliveryMapper.selectByIdForUpdate(20L)).thenReturn(delivery(status));
            assertThrows(BusinessException.class, () -> service.receive(20L, null));
        }
        verifyNoInteractions(qualityLotMapper, outboundService);
    }
    @Test void existingShippedDeliveryCanRecordReceiptWithoutChangingOqcOrInventory() {
        when(salesDeliveryMapper.selectByIdForUpdate(20L)).thenReturn(delivery(SalesDeliveryStatusEnum.SHIPPED));
        when(salesDeliveryMapper.updateById(any(SalesDelivery.class))).thenReturn(1);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) { service.receive(20L, null); }
        ArgumentCaptor<SalesDelivery> patch = ArgumentCaptor.forClass(SalesDelivery.class); verify(salesDeliveryMapper).updateById(patch.capture());
        assertEquals(SalesDeliveryStatusEnum.RECEIVED.getValue(), patch.getValue().getDeliveryStatus()); verifyNoInteractions(qualityLotMapper, outboundService);
    }
    @Test void committedShipmentEventCannotDeductInventoryTwice() {
        new InventoryEventBridge(null, outboundService).onSalesDelivery(Map.of("inventoryPosted", true, "deliveryId", 20L, "outboundId", 40L)); verifyNoInteractions(outboundService);
    }
    @Test void shipmentBoundaryRollsBackForAllExceptions() throws Exception {
        Transactional transaction = SalesDeliveryServiceImpl.class.getMethod("confirmShipment", Long.class).getAnnotation(Transactional.class);
        assertTrue(Arrays.asList(transaction.rollbackFor()).contains(Exception.class));
    }
}
