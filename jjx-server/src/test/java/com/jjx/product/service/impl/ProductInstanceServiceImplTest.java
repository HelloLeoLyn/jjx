package com.jjx.product.service.impl;

import com.jjx.event.EventPublisher;
import com.jjx.product.domain.entity.ProductInstance;
import com.jjx.product.mapper.ProductInstanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductInstanceServiceImplTest {

    private ProductInstanceMapper mapper;
    private EventPublisher eventPublisher;
    private ProductInstanceServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductInstanceMapper.class);
        eventPublisher = mock(EventPublisher.class);
        service = new ProductInstanceServiceImpl(mapper, eventPublisher);
    }

    @Test
    void startProductionPersistsStatusAndPublishesOnlyAfterSuccessfulUpdate() {
        ProductInstance instance = instance(1);
        when(mapper.selectById(10L)).thenReturn(instance);
        when(mapper.updateById(instance)).thenReturn(1);

        assertTrue(service.startProduction(10L));
        assertEquals(3, instance.getInstanceStatus());
        verify(eventPublisher).fire(eq("product.instance.production_started"), any());
    }

    @Test
    void completeProductionReturnsFalseAndDoesNotPublishWhenUpdateFails() {
        ProductInstance instance = instance(3);
        when(mapper.selectById(10L)).thenReturn(instance);
        when(mapper.updateById(instance)).thenReturn(0);

        assertFalse(service.completeProduction(10L));
        assertEquals(5, instance.getInstanceStatus());
        verify(eventPublisher, never()).fire(eq("product.instance.production_completed"), any());
    }

    @Test
    void deliverInstancePersistsDeliveredStatus() {
        ProductInstance instance = instance(5);
        when(mapper.selectById(10L)).thenReturn(instance);
        when(mapper.updateById(instance)).thenReturn(1);

        assertTrue(service.deliverInstance(10L));
        assertEquals(9, instance.getInstanceStatus());
        verify(eventPublisher).fire(eq("product.instance.delivered"), any());
    }

    private ProductInstance instance(int status) {
        ProductInstance instance = new ProductInstance();
        instance.setInstanceId(10L);
        instance.setInstanceCode("PI-0010");
        instance.setInstanceStatus(status);
        return instance;
    }
}
