package com.jjx.sales;

import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.dto.QualityLotCreateDTO;
import com.jjx.quality.mapper.QualityLotMapper;
import com.jjx.quality.service.QualityLotService;
import com.jjx.quality.service.QualityOqcService;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.enums.*;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.SalesDeliveryWorkflowService;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MergedDeliveryWorkflowTest {
    @Mock com.jjx.sales.service.DeliveryCapacityService capacityService;
    @Mock CustomerMapper customerMapper;
    @Mock OrderMapper orderMapper;
    @Mock SalesOrderProductMapper productMapper;
    @Mock SalesDeliveryMapper deliveryMapper;
    @Mock SalesDeliveryItemMapper itemMapper;
    @Mock RedisSequenceService sequenceService;
    @Mock QualityLotService lotService;
    @Mock QualityOqcService oqcService;
    @Mock QualityLotMapper lotMapper;
    @Mock com.jjx.inventory.mapper.InventoryOutboundOrderMapper outboundMapper;
    @InjectMocks SalesDeliveryWorkflowService workflow;

    private SalesOrder order(long id) {
        SalesOrder o=new SalesOrder();o.setOrderId(id);o.setOrderNo("SO-"+id);o.setCustomerId(8L);
        o.setOrderStatus(SalesOrderStatusEnum.IN_PRODUCTION.getValue());o.setCurrency("CNY");
        o.setDeliveryAddress("苏州");o.setTotalAmount(BigDecimal.valueOf(100));return o;
    }
    private SalesOrderProduct product(long id,long orderId) {
        SalesOrderProduct p=new SalesOrderProduct();p.setId(id);p.setOrderId(orderId);p.setProductId(26L);
        p.setProductCode("SAME-PRODUCT");p.setProductName("薄膜开关");p.setQuantity(10);p.setUnitPrice(BigDecimal.TEN);return p;
    }
    private SalesDelivery request() {
        SalesDelivery d=new SalesDelivery();d.setDeliveryMethod("快递");
        SalesDeliveryItem a=new SalesDeliveryItem();a.setOrderProductId(10L);a.setQuantity(2);
        SalesDeliveryItem b=new SalesDeliveryItem();b.setOrderProductId(11L);b.setQuantity(3);
        d.setItems(List.of(a,b));return d;
    }
    private void sources(SalesOrder second) {
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product(10L,1L),product(11L,2L)));
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order(1L));
        when(orderMapper.selectByIdForUpdate(2L)).thenReturn(second);
    }
    @Test void sameProductAcrossOrdersStaysSeparateAndCreatesOqcWithEachSourceOrder() {
        sources(order(2L));
        SalesDelivery request = request();
        request.setDeliveryAddress("客户地址簿选定的新地址");
        when(sequenceService.generateBusinessNumberByType(anyString(),anyString(),anyString(),anyInt())).thenReturn("DL-TEST");
        when(deliveryMapper.insert(any(SalesDelivery.class))).thenAnswer(i->{((SalesDelivery)i.getArgument(0)).setDeliveryId(20L);return 1;});
        when(itemMapper.insert(any(SalesDeliveryItem.class))).thenAnswer(i->{SalesDeliveryItem l=i.getArgument(0);l.setItemId(l.getOrderProductId()+20);return 1;});
        try(MockedStatic<SecurityUtils> ignored=mockStatic(SecurityUtils.class)){assertEquals(20L,workflow.create(request));}
        ArgumentCaptor<SalesDeliveryItem> lines=ArgumentCaptor.forClass(SalesDeliveryItem.class);
        verify(itemMapper,times(2)).insert(lines.capture());
        assertEquals(List.of(10L,11L),lines.getAllValues().stream().map(SalesDeliveryItem::getOrderProductId).toList());
        assertEquals(List.of(2,3),lines.getAllValues().stream().map(SalesDeliveryItem::getQuantity).toList());
        ArgumentCaptor<QualityLotCreateDTO> lots=ArgumentCaptor.forClass(QualityLotCreateDTO.class);
        verify(lotService,times(2)).createLot(lots.capture());
        assertEquals(List.of(1L,2L),lots.getAllValues().stream().map(QualityLotCreateDTO::getOrderId).toList());
        ArgumentCaptor<SalesDelivery> header=ArgumentCaptor.forClass(SalesDelivery.class);verify(deliveryMapper).insert(header.capture());
        assertEquals(5,header.getValue().getTotalQuantity());assertEquals(BigDecimal.valueOf(50),header.getValue().getTotalAmount());
        assertEquals("客户地址簿选定的新地址", header.getValue().getDeliveryAddress());
        verify(orderMapper,never()).updateById(any(SalesOrder.class));
    }
    @Test void differentCustomerCannotMerge() {
        SalesOrder o=order(2L);o.setCustomerId(9L);sources(o);
        assertThrows(BusinessException.class,()->workflow.create(request()));verify(deliveryMapper,never()).insert(any(SalesDelivery.class));
    }
    @Test void differentAddressCannotMerge() {
        SalesOrder o=order(2L);o.setDeliveryAddress("上海");sources(o);
        assertThrows(BusinessException.class,()->workflow.create(request()));verifyNoInteractions(lotService);
    }
    @Test void differentCurrencyCannotMerge() {
        SalesOrder o=order(2L);o.setCurrency("USD");sources(o);
        assertThrows(BusinessException.class,()->workflow.create(request()));verifyNoInteractions(lotService);
    }
    @Test void pendingAllocationBlocksOversubscription() {
        sources(order(2L));SalesDeliveryItem previous=new SalesDeliveryItem();previous.setOrderProductId(11L);previous.setQuantity(8);previous.setDeliveryId(21L);
        SalesDelivery active=new SalesDelivery();active.setDeliveryId(21L);active.setDeliveryStatus(SalesDeliveryStatusEnum.PENDING.getValue());
        when(itemMapper.selectList(any())).thenReturn(List.of(previous));when(deliveryMapper.selectBatchIds(any())).thenReturn(List.of(active));
        assertThrows(BusinessException.class,()->workflow.create(request()));verifyNoInteractions(lotService);
    }
    @Test void incompleteHistoricalDeliveryBlocksNewAllocation() {
        sources(order(2L));SalesDelivery old=new SalesDelivery();old.setDeliveryId(21L);old.setTotalQuantity(2);old.setDeliveryStatus(SalesDeliveryStatusEnum.PENDING.getValue());
        when(deliveryMapper.selectActiveBySourceOrders(any(),any())).thenReturn(List.of(old));
        assertThrows(BusinessException.class,()->workflow.create(request()));verifyNoInteractions(lotService);
    }
    @Test void voidedAndRejectedLinesReleaseAllocation() {
        SalesDeliveryItem previous=new SalesDeliveryItem();previous.setOrderProductId(10L);previous.setQuantity(8);previous.setDeliveryId(21L);
        when(itemMapper.selectList(any())).thenReturn(List.of(previous));
        for(SalesDeliveryStatusEnum status:List.of(SalesDeliveryStatusEnum.VOIDED,SalesDeliveryStatusEnum.REJECTED)) {
            SalesDelivery d=new SalesDelivery();d.setDeliveryId(21L);d.setDeliveryStatus(status.getValue());when(deliveryMapper.selectBatchIds(any())).thenReturn(List.of(d));
            assertTrue(workflow.quantities(List.of(10L),List.of(SalesDeliveryStatusEnum.PENDING,SalesDeliveryStatusEnum.SHIPPED,SalesDeliveryStatusEnum.RECEIVED)).isEmpty());
        }
    }
    private SalesDelivery pendingVoid() {
        SalesDelivery d=new SalesDelivery();d.setDeliveryId(20L);d.setDeliveryStatus(SalesDeliveryStatusEnum.PENDING.getValue());
        when(deliveryMapper.selectByIdForUpdate(20L)).thenReturn(d);
        SalesDeliveryItem line=new SalesDeliveryItem();line.setItemId(30L);line.setOrderProductId(10L);line.setProductId(26L);line.setQuantity(2);
        when(itemMapper.selectList(any())).thenReturn(List.of(line));when(productMapper.selectBatchIds(any())).thenReturn(List.of(product(10L,1L)));
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order(1L));return d;
    }
    @Test void voidClosesOqcCancelsUnpostedOutboundAndKeepsOriginalLines() {
        SalesDelivery d=pendingVoid();
        var out=new com.jjx.inventory.domain.InventoryOutboundOrder();out.setOutboundId(40L);out.setOrderStatus(com.jjx.inventory.enums.InventoryOrderStatusEnum.APPROVED.getValue());
        when(outboundMapper.selectList(any())).thenReturn(List.of(out));when(outboundMapper.selectByIdForUpdate(40L)).thenReturn(out);
        when(outboundMapper.updateById(any(com.jjx.inventory.domain.InventoryOutboundOrder.class))).thenReturn(1);
        QualityLot lot=new QualityLot();lot.setLotId(50L);lot.setRemark("原检验备注");when(lotMapper.selectList(any())).thenReturn(List.of(lot));
        when(lotMapper.updateById(any(QualityLot.class))).thenReturn(1);when(deliveryMapper.updateById(any(SalesDelivery.class))).thenReturn(1);
        workflow.voidPending(20L,"客户调整安排");
        assertEquals(SalesDeliveryStatusEnum.VOIDED.getValue(),d.getDeliveryStatus());
        assertEquals(com.jjx.quality.enums.QualityLotStatusEnum.CLOSED.getCode(),lot.getStatus());assertTrue(lot.getRemark().startsWith("原检验备注"));
        assertEquals(com.jjx.inventory.enums.InventoryOrderStatusEnum.CANCELLED.getValue(),out.getOrderStatus());
        verify(itemMapper,never()).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(orderMapper,never()).updateById(any(SalesOrder.class));
    }
    @Test void actuallyPostedOutboundCannotBeVoidedEvenIfHeaderIsPending() {
        pendingVoid();var out=new com.jjx.inventory.domain.InventoryOutboundOrder();out.setOutboundId(40L);out.setOrderStatus(com.jjx.inventory.enums.InventoryOrderStatusEnum.COMPLETED.getValue());
        when(outboundMapper.selectList(any())).thenReturn(List.of(out));when(outboundMapper.selectByIdForUpdate(40L)).thenReturn(out);
        assertThrows(BusinessException.class,()->workflow.voidPending(20L,"调整"));verifyNoInteractions(lotMapper);
        verify(deliveryMapper,never()).updateById(any(SalesDelivery.class));
    }
    @Test void shippedDeliveryCannotUsePendingVoidRoute() {
        SalesDelivery d=new SalesDelivery();d.setDeliveryStatus(SalesDeliveryStatusEnum.SHIPPED.getValue());when(deliveryMapper.selectByIdForUpdate(20L)).thenReturn(d);
        assertThrows(BusinessException.class,()->workflow.voidPending(20L,"调整"));verifyNoInteractions(lotMapper,outboundMapper);
    }
}
