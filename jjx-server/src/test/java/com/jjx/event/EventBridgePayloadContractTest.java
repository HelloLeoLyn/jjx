package com.jjx.event;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.event.impl.LocalEventPublisher;
import com.jjx.inventory.event.InventoryEventBridge;
import com.jjx.inventory.service.InventoryInboundService;
import com.jjx.inventory.service.InventoryOutboundService;
import com.jjx.system.domain.entity.SysEventConfig;
import com.jjx.system.mapper.SysEventConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dev-20260930-028 回归：本地 Spring 事件必须自带 eventCode（否则桥接监听器的
 * {@code @EventListener(condition = "#payload?.eventCode == '...'")} 恒不匹配、静默不执行）；
 * 桥接在关键业务键缺失时必须明确失败、不得再走「通用兜底建单」造无来源单据。
 */
class EventBridgePayloadContractTest {

    static {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "event-bridge-payload-test"),
                SysEventConfig.class);
    }

    private LocalEventPublisher publisher(ApplicationEventPublisher springPublisher) {
        return new LocalEventPublisher(springPublisher, mock(SysEventConfigMapper.class),
                null, null, null, null, null, null, null);
    }

    @Test
    void publishedLocalEventCarriesEventCode() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        Map<String, Object> payload = new HashMap<>();
        payload.put("bizType", "order");

        publisher(springPublisher).fire("order.delivering", payload);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(springPublisher).publishEvent(captor.capture());
        assertEquals("order.delivering", captor.getValue().get("eventCode"),
                "本地事件必须带事件名，桥接条件才可能命中：" + captor.getValue());
        assertFalse(payload.containsKey("eventCode"), "不应改动调用方传入的 Map");
    }

    @Test
    void immutablePayloadIsAccepted() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);

        publisher(springPublisher).fire("order.delivering", Map.of("bizId", 1L)); // Map.of 不可变

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(springPublisher).publishEvent(captor.capture());
        assertEquals("order.delivering", captor.getValue().get("eventCode"));
    }

    @Test
    void salesDeliveryWithoutKeysCreatesNothing() {
        InventoryInboundService inbound = mock(InventoryInboundService.class);
        InventoryOutboundService outbound = mock(InventoryOutboundService.class);

        new InventoryEventBridge(inbound, outbound)
                .onSalesDelivery(new HashMap<>(Map.of("eventCode", "order.delivering")));

        verify(outbound, never()).create(any());
        verify(outbound, never()).createFromSales(anyLong());
        verify(outbound, never()).createFromSalesByDelivery(anyLong());
    }

    @Test
    void salesDeliveryWithDeliveryIdGoesThroughDeliveryPath() {
        InventoryInboundService inbound = mock(InventoryInboundService.class);
        InventoryOutboundService outbound = mock(InventoryOutboundService.class);
        when(outbound.createFromSalesByDelivery(7L)).thenReturn(99L);

        new InventoryEventBridge(inbound, outbound)
                .onSalesDelivery(new HashMap<>(Map.of("deliveryId", 7L)));

        verify(outbound).createFromSalesByDelivery(7L);
        verify(outbound, never()).create(any());
    }

    @Test
    void productionCompletedWithoutOrderNoCreatesNothing() {
        InventoryInboundService inbound = mock(InventoryInboundService.class);
        InventoryOutboundService outbound = mock(InventoryOutboundService.class);

        new InventoryEventBridge(inbound, outbound).onProductionCompleted(new HashMap<>());

        verify(inbound, never()).create(any());
    }
}
