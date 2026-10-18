package com.jjx.sales.service.impl;

import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.system.service.ReviewFlowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 样品单审核/退回写 review_flow 的证据关联（dev-20261018-021 补收尾）。
 * 断言：attachmentRefIds 按 review_flow 现有格式序列化为 [1,2]；无证据时保持 null；remark 不被覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SampleReviewFlowAttachmentTest {

    @Mock OrderMapper orderMapper;
    @Mock ReviewFlowService reviewFlowService;
    @InjectMocks SampleOrderServiceImpl service;

    private SalesOrder order(int status, int round) {
        SalesOrder o = new SalesOrder();
        o.setOrderId(1L);
        o.setSampleStatus(status);
        o.setSampleRound(round);
        o.setDeleted(0);
        return o;
    }

    private void passTransition(SalesOrder o) {
        when(orderMapper.updateSampleStatus(anyLong(), anyInt(), anyInt())).thenReturn(1);
        when(orderMapper.selectById(1L)).thenReturn(o);
    }

    @Test
    void approveWritesAttachmentRefsAsJson() {
        passTransition(order(SampleOrderStatusEnum.REQUEST.getValue(), 1));
        service.approveReview(1L, "意见", List.of(11L, 12L));
        verify(reviewFlowService).recordWithRefs(eq("sample"), eq(1L), eq("APPROVE"), eq("审核通过"),
                eq(SampleOrderStatusEnum.REQUEST.getValue()), eq(SampleOrderStatusEnum.ENGINEERING.getValue()),
                eq("意见"), eq("[11,12]"));
    }

    @Test
    void approveWithoutEvidenceKeepsNull() {
        passTransition(order(SampleOrderStatusEnum.REQUEST.getValue(), 1));
        service.approveReview(1L, "意见", null);
        verify(reviewFlowService).recordWithRefs(eq("sample"), eq(1L), eq("APPROVE"), eq("审核通过"),
                any(), any(), eq("意见"), isNull());
    }

    @Test
    void rejectReviewWritesAttachmentRefsAsJson() {
        passTransition(order(SampleOrderStatusEnum.REQUEST.getValue(), 1));
        service.rejectReview(1L, "驳回原因", List.of(7L));
        verify(reviewFlowService).recordWithRefs(eq("sample"), eq(1L), eq("REJECT"), eq("审核驳回"),
                eq(SampleOrderStatusEnum.REQUEST.getValue()), eq(SampleOrderStatusEnum.CREATED.getValue()),
                eq("驳回原因"), eq("[7]"));
    }

    @Test
    void rejectSampleWritesAttachmentRefsAsJsonAndKeepsRemark() {
        passTransition(order(SampleOrderStatusEnum.SAMPLE_SENT.getValue(), 2));
        service.rejectSample(1L, "客户退回原因", List.of(9L));
        verify(reviewFlowService).recordWithRefs(eq("sample"), eq(1L), eq("CUSTOMER_REJECT"), eq("客户退回"),
                eq(SampleOrderStatusEnum.SAMPLE_SENT.getValue()), eq(SampleOrderStatusEnum.REJECTED.getValue()),
                eq("客户退回原因"), eq("[9]"));

        ArgumentCaptor<SalesOrder> upd = ArgumentCaptor.forClass(SalesOrder.class);
        verify(orderMapper).updateById(upd.capture());
        assertNull(upd.getValue().getRemark(), "退回原因不得覆盖 sales_order.remark");
        org.junit.jupiter.api.Assertions.assertEquals(3, upd.getValue().getSampleRound());
    }
}
