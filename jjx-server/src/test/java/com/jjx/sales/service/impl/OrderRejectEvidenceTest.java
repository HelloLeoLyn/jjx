package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.jjx.system.service.ReviewFlowService;
import com.jjx.system.utils.SecurityUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderRejectEvidenceTest {
    @Mock OrderMapper salesOrderMapper;
    @Mock SysAttachmentRefMapper attachmentRefMapper;
    @Mock ReviewFlowService reviewFlowService;
    @Mock com.jjx.event.EventPublisher eventPublisher;
    @InjectMocks OrderStatusServiceImpl service;
    static {TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"order-reject"),SysAttachmentRef.class);}

    @Test void rejectsLegacyBeforeUpdatingOrder() {
        var dto=new com.jjx.sales.domain.dto.ReviewDTO();dto.setOrderId(10L);dto.setAttachments("99");
        assertThrows(BusinessException.class,()->service.rejectOrder(dto));
        verifyNoInteractions(salesOrderMapper,reviewFlowService);
    }
    @Test void recordsRejectedOrderRefSnapshot() {
        var dto=new com.jjx.sales.domain.dto.ReviewDTO();dto.setOrderId(10L);dto.setRemark("需要修改");dto.setAttachmentRefIds(List.of(101L));
        var ref=new SysAttachmentRef();ref.setId(101L);ref.setSourceType("sales_order");ref.setSourceId(10L);
        when(attachmentRefMapper.selectOne(any())).thenReturn(ref);
        var order=new SalesOrder();order.setOrderId(10L);order.setOrderStatus(com.jjx.sales.enums.SalesOrderStatusEnum.REVIEWING.getValue());
        when(salesOrderMapper.selectById(10L)).thenReturn(order);
        when(salesOrderMapper.updateStatusWithCheck(eq(10L),anyInt(),anyInt())).thenReturn(1);
        try(var security=mockStatic(SecurityUtils.class)){service.rejectOrder(dto);}
        verify(reviewFlowService).recordWithRefs(eq("sales_order"),eq(10L),eq("REJECT"),anyString(),any(),any(),eq("需要修改"),eq("[101]"));
    }
}
