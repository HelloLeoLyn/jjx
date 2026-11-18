package com.jjx.purchase;

import com.jjx.common.exception.BusinessException;
import com.jjx.purchase.controller.PurchasePaymentController;
import com.jjx.purchase.domain.dto.PurchasePaymentApprovalDTO;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 依赖用 @InjectMocks 注入（dev-20261016-053）：服务再加构造依赖时本类无需跟着改。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchasePaymentApproverTest {
    @Mock private PurchasePaymentMapper payments;
    @Mock private PurchaseOrderMapper orders;
    /** 本类未用到的依赖也备成 mock：将来补用例走到该路径时不会空指针。 */
    @Mock private com.jjx.event.EventPublisher eventPublisher;
    @Mock private com.jjx.system.service.AttachmentRefService attachmentRefService;
    @InjectMocks private PurchasePaymentServiceImpl service;

    @Test void approvalUsesLoggedInActorForBothDecisions() {
        when(payments.update(any(PurchasePayment.class), any())).thenReturn(1);
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
            verify(payments, times(2)).update(any(PurchasePayment.class), any());
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
        var batch = List.of(new PurchasePaymentApprovalDTO(2L, decision, "批量意见"));
        controller.batchApprove(batch);
        verify(apiService).approvePayment(1L, decision, "单笔意见");
        verify(apiService).batchApprovePayments(batch);
        verifyNoMoreInteractions(apiService);
        assertEquals(3, PurchasePaymentController.class.getMethod("approve", Long.class, String.class, String.class).getParameterCount());
    }

    @Test void exportReturnsFileBytesInsteadOfPlaceholderPath() {
        var apiService = mock(IPurchasePaymentService.class);
        var controller = new PurchasePaymentController(apiService, mock(IPurchaseOrderService.class), mock(ISysAttachmentService.class));
        var query = new com.jjx.purchase.domain.dto.PurchasePaymentDTO();
        byte[] bytes = new byte[]{80, 75, 3, 4};
        when(apiService.exportPaymentList(query)).thenReturn(bytes);
        var response = controller.export(query);
        assertSame(bytes, response.getBody());
        assertTrue(response.getHeaders().getContentType().toString().contains("spreadsheetml.sheet"));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("purchase-payments.xlsx"));
    }
}
