package com.jjx.sales;

import com.jjx.inventory.domain.InventoryInboundOrder;
import com.jjx.inventory.service.impl.InventoryInboundServiceImpl;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.enums.ProdStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesOrderProductionWritebackTest {
    @Test void finishedInboundAndReverseUpdateQuantityAndStatusTogether() throws Exception {
        var productionMapper = mock(ProductionOrderMapper.class);
        var salesMapper = mock(OrderMapper.class);
        var constructor = InventoryInboundServiceImpl.class.getDeclaredConstructors()[0];
        var service = (InventoryInboundServiceImpl) constructor.newInstance(new Object[constructor.getParameterCount()]);
        ReflectionTestUtils.setField(service, "productionOrderMapper", productionMapper);
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesMapper);
        ProductionOrder work = new ProductionOrder(); work.setSalesOrderId(2L);
        SalesOrder order = new SalesOrder(); order.setTotalQuantity(2); order.setProducedQuantity(0);
        order.setProdStatus(ProdStatusEnum.NONE.getValue());
        when(productionMapper.selectById(2L)).thenReturn(work);
        when(salesMapper.selectById(2L)).thenReturn(order);
        when(salesMapper.updateById(any(SalesOrder.class))).thenAnswer(call -> {
            SalesOrder patch = call.getArgument(0);
            assertNull(patch.getTotalAmount()); assertNull(patch.getPaidAmount());
            assertNull(patch.getShippedQuantity()); assertNull(patch.getOrderStatus());
            order.setProducedQuantity(patch.getProducedQuantity()); order.setProdStatus(patch.getProdStatus());
            return 1;
        });
        InventoryInboundOrder inbound = new InventoryInboundOrder(); inbound.setSourceId(2L);
        ReflectionTestUtils.invokeMethod(service, "writebackProducedQuantity", inbound, new BigDecimal("2"));
        assertEquals(2, order.getProducedQuantity()); assertEquals(ProdStatusEnum.COMPLETED.getValue(), order.getProdStatus());
        ReflectionTestUtils.invokeMethod(service, "writebackProducedQuantity", inbound, BigDecimal.ZERO);
        assertEquals(2, order.getProducedQuantity());
        ReflectionTestUtils.invokeMethod(service, "writebackProducedQuantity", inbound, new BigDecimal("-1"));
        assertEquals(1, order.getProducedQuantity()); assertEquals(ProdStatusEnum.PARTIAL_PRODUCING.getValue(), order.getProdStatus());
        verify(salesMapper, times(3)).updateById(any(SalesOrder.class));
    }
}
