package com.jjx.system.service;

import com.jjx.inventory.mapper.InventoryInboundOrderMapper;
import com.jjx.inventory.mapper.InventoryOutboundOrderMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.EngineeringRoutingMapper;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysErrorLogMapper;
import com.jjx.system.mapper.SysLoginLogMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class LogSaveServiceOrderTest {

    @Test
    void shouldInsertOperLogsInListOrder() {
        SysOperLogMapper operLogMapper = mock(SysOperLogMapper.class);
        LogSaveService service = new LogSaveService(
            operLogMapper, mock(SysLoginLogMapper.class), mock(SysErrorLogMapper.class),
            mock(EngineeringBomMapper.class), mock(EngineeringRoutingMapper.class),
            mock(InventoryInboundOrderMapper.class), mock(InventoryOutboundOrderMapper.class),
            mock(OrderMapper.class));
        SysOperLog inquiryLog = new SysOperLog();
        inquiryLog.setBizType("inquiry");
        SysOperLog quotationLog = new SysOperLog();
        quotationLog.setBizType("quotation");

        service.saveOperLogsInOrder(List.of(inquiryLog, quotationLog));

        InOrder ordered = inOrder(operLogMapper);
        ordered.verify(operLogMapper).insert(inquiryLog);
        ordered.verify(operLogMapper).insert(quotationLog);
    }
}
