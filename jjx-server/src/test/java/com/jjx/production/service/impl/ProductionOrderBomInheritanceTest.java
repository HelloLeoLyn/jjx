package com.jjx.production.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.production.domain.dto.ConvertPlanToWorkOrdersDTO;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.enums.ProductionOrderStatusEnum;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.ProductionBomResolver;
import org.junit.jupiter.api.BeforeEach;
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
class ProductionOrderBomInheritanceTest {
    @Mock ProductionOrderMapper productionOrderMapper;
    @Mock ProductionBomResolver productionBomResolver;
    @Mock RedisSequenceService redisSequenceService;
    @Mock com.jjx.product.service.WorkSpecBindingService workSpecBindingService;
    @Spy @InjectMocks ProductionOrderServiceImpl service;
    ProductionOrder plan;
    ConvertPlanToWorkOrdersDTO dto;

    @BeforeEach void setup() {
        plan = new ProductionOrder(); plan.setOrderId(1L); plan.setOrderType("PLAN");
        plan.setProductId(2L); plan.setBomId(5L); plan.setPlannedQuantity(BigDecimal.TEN);
        plan.setOrderStatus(ProductionOrderStatusEnum.APPROVED.getValue());
        doReturn(plan).when(service).getById(1L);
        ConvertPlanToWorkOrdersDTO.WorkOrderItem item = new ConvertPlanToWorkOrdersDTO.WorkOrderItem();
        item.setProductId(2L); item.setProductCode("PRODUCT-2"); item.setPlannedQuantity(BigDecimal.TEN);
        dto = new ConvertPlanToWorkOrdersDTO(); dto.setPlanId(1L); dto.setWorkOrders(List.of(item));
    }

    @Test void conversionPersistsPlanBomOnNewWorkOrder() {
        when(workSpecBindingService.bindOnIssue(2L, null)).thenReturn(11L);
        when(redisSequenceService.generateBusinessNumberByType("production_order", "WO", "yyMMdd", 3)).thenReturn("WO-TEST");
        doAnswer(call -> { ProductionOrder workOrder = call.getArgument(0); workOrder.setOrderId(20L); return true; })
                .when(service).save(any(ProductionOrder.class));
        doReturn(true).when(service).updateById(plan);
        assertEquals(List.of(20L), service.convertPlanToWorkOrders(dto));
        ArgumentCaptor<ProductionOrder> captured = ArgumentCaptor.forClass(ProductionOrder.class);
        verify(service).save(captured.capture());
        assertEquals(5L, captured.getValue().getBomId());
        assertEquals(11L, captured.getValue().getWorkSpecVersionId());
        assertEquals(plan.getProductId(), captured.getValue().getProductId());
        assertEquals(plan.getOrderId(), captured.getValue().getParentOrderId());
        verify(productionBomResolver).resolve(plan);
    }

    @Test void invalidVersionStopsConversionBeforeSaving() {
        dto.getWorkOrders().get(0).setWorkSpecVersionId(99L);
        when(redisSequenceService.generateBusinessNumberByType("production_order", "WO", "yyMMdd", 3)).thenReturn("WO-TEST");
        when(workSpecBindingService.bindOnIssue(2L, 99L)).thenThrow(new BusinessException("版本不可绑定"));
        assertThrows(BusinessException.class, () -> service.convertPlanToWorkOrders(dto));
        verify(service, never()).save(any(ProductionOrder.class));
        verify(service, never()).updateById(any(ProductionOrder.class));
    }

    @Test void invalidPlanBomStopsConversionBeforeSaving() {
        when(productionBomResolver.resolve(plan)).thenThrow(new BusinessException("BOM未批准"));
        assertThrows(BusinessException.class, () -> service.convertPlanToWorkOrders(dto));
        verify(service, never()).save(any(ProductionOrder.class));
        verifyNoInteractions(redisSequenceService);
    }

    @Test void differentProductCannotInheritPlanBom() {
        dto.getWorkOrders().get(0).setProductId(99L);
        assertThrows(BusinessException.class, () -> service.convertPlanToWorkOrders(dto));
        verify(service, never()).save(any(ProductionOrder.class));
        verifyNoInteractions(redisSequenceService);
    }
}
