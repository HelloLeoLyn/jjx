package com.jjx.purchase;

import com.jjx.common.exception.BusinessException;
import com.jjx.purchase.controller.PurchasePaymentController;
import com.jjx.purchase.domain.entity.PurchasePayment;
import com.jjx.purchase.domain.enums.PurchasePaymentApprovalStatusEnum;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.mapper.PurchasePaymentMapper;
import com.jjx.purchase.service.IPurchaseOrderService;
import com.jjx.purchase.service.IPurchasePaymentService;
import com.jjx.purchase.service.impl.PurchasePaymentServiceImpl;
import com.jjx.system.service.ISysAttachmentService;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchasePaymentApproverTest {
    private final PurchasePaymentMapper payments = mock(PurchasePaymentMapper.class);
    private final PurchaseOrderMapper orders = mock(PurchaseOrderMapper.class);
    private final PurchasePaymentServiceImpl service = new PurchasePaymentServiceImpl(payments, null, orders);

    @Test void approvalUsesLoggedInActorForBothDecisions() {
        try (var identity = mockStatic(SecurityUtils.class)) {
            identity.when(SecurityUtils::getUserId).thenReturn(31L);
            identity.when(SecurityUtils::getDisplayName).thenReturn("当前审批员");
            for (var decision : List.of(PurchasePaymentApprovalStatusEnum.APPROVED, PurchasePaymentApprovalStatusEnum.REJECTED)) {
                var payment = new PurchasePayment();
                payment.setPaymentId(1L);
                payment.setApprovalStatus(PurchasePaymentApprovalStatusEnum.PENDING.getCode());
                when(payments.selectById(1L)).thenReturn(payment);
                service.approvePayment(1L, decision.getCode(), "审批意见");
                assertEquals("当前审批员", payment.getApproverName());
                assertEquals("审批意见", payment.getApprovalComment());
                assertEquals(decision.getCode(), payment.getApprovalStatus());
                assertNotNull(payment.getApprovalTime());
            }
            verify(payments, times(2)).updateById(any(PurchasePayment.class));
        }
    }

    @Test void missingIdentityCannotWriteApproval() {
        try (var identity = mockStatic(SecurityUtils.class)) {
            identity.when(SecurityUtils::getUserId).thenReturn(null);
            identity.when(SecurityUtils::getDisplayName).thenReturn("姓名");
            assertThrows(BusinessException.class, () -> service.approvePayment(1L, PurchasePaymentApprovalStatusEnum.APPROVED.getCode(), null));
            verifyNoInteractions(payments);
        }
    }

    @Test void blankDisplayNameCannotWriteApproval() {
        try (var identity = mockStatic(SecurityUtils.class)) {
            identity.when(SecurityUtils::getUserId).thenReturn(31L);
            identity.when(SecurityUtils::getDisplayName).thenReturn("  ");
            assertThrows(BusinessException.class, () -> service.approvePayment(1L, PurchasePaymentApprovalStatusEnum.APPROVED.getCode(), null));
            verifyNoInteractions(payments);
        }
    }

    @Test void singleAndBatchEntryPointsDoNotForwardClientApproverName() throws Exception {
        var apiService = mock(IPurchasePaymentService.class);
        var controller = new PurchasePaymentController(apiService, mock(IPurchaseOrderService.class), mock(ISysAttachmentService.class));
        String decision = PurchasePaymentApprovalStatusEnum.APPROVED.getCode();
        controller.approve(1L, decision, "单笔意见");
        controller.batchApprove(List.of(Map.of("paymentId", 2L, "approvalStatus", decision,
                "approverName", "冒填姓名", "approvalComment", "批量意见")));
        verify(apiService).approvePayment(1L, decision, "单笔意见");
        verify(apiService).approvePayment(2L, decision, "批量意见");
        verifyNoMoreInteractions(apiService);
        assertEquals(3, PurchasePaymentController.class.getMethod("approve", Long.class, String.class, String.class).getParameterCount());
    }
}
