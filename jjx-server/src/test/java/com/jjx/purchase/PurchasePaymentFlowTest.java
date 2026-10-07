package com.jjx.purchase;

import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.purchase.domain.dto.PurchasePaymentDTO;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.entity.PurchasePayment;
import com.jjx.purchase.domain.enums.PurchasePaymentApprovalStatusEnum;
import com.jjx.purchase.domain.enums.PurchasePaymentStatusEnum;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.mapper.PurchasePaymentMapper;
import com.jjx.purchase.service.impl.PurchasePaymentServiceImpl;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchasePaymentFlowTest {
    private final PurchaseOrderMapper orders = mock(PurchaseOrderMapper.class, withSettings().mockMaker("mock-maker-subclass"));
    private final PurchasePaymentMapper payments = mock(PurchasePaymentMapper.class, withSettings().mockMaker("mock-maker-subclass"));
    private final PurchasePaymentServiceImpl service = new PurchasePaymentServiceImpl(payments, null, orders);

    private PurchaseOrder order() {
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderId(1L);
        order.setOrderTotalAmount(new BigDecimal("100.00"));
        order.setApprovalStatus(ApproveStatusEnum.APPROVED.getValue());
        order.setCurrency("CNY");
        when(orders.selectById(1L)).thenReturn(order);
        return order;
    }

    private PurchasePayment payment(long id, String amount, PurchasePaymentStatusEnum status,
                                    PurchasePaymentApprovalStatusEnum approval) {
        PurchasePayment payment = new PurchasePayment();
        payment.setPaymentId(id);
        payment.setOrderId(1L);
        payment.setPaymentAmount(new BigDecimal(amount));
        payment.setPaymentStatus(status.getValue());
        payment.setApprovalStatus(approval.getCode());
        return payment;
    }

    @Test void summaryReservesApplicationsAndReleasesRejectedAmounts() {
        order();
        when(payments.selectByOrderId(1L)).thenReturn(List.of(
                payment(1, "20.00", PurchasePaymentStatusEnum.COMPLETED, PurchasePaymentApprovalStatusEnum.APPROVED),
                payment(2, "30.00", PurchasePaymentStatusEnum.PENDING, PurchasePaymentApprovalStatusEnum.PENDING),
                payment(3, "10.00", PurchasePaymentStatusEnum.PENDING, PurchasePaymentApprovalStatusEnum.APPROVED),
                payment(4, "80.00", PurchasePaymentStatusEnum.PENDING, PurchasePaymentApprovalStatusEnum.REJECTED)));
        var summary = service.getOrderPaymentSummary(1L, null);
        assertEquals(new BigDecimal("20.00"), summary.getPaidAmount());
        assertEquals(new BigDecimal("40.00"), summary.getPendingAmount());
        assertEquals(new BigDecimal("40.00"), summary.getAvailableAmount());
    }

    @Test void editExcludesOnlyThisPendingApplication() {
        order();
        var current = payment(2, "30.00", PurchasePaymentStatusEnum.PENDING, PurchasePaymentApprovalStatusEnum.PENDING);
        when(payments.selectById(2L)).thenReturn(current);
        when(payments.selectByOrderId(1L)).thenReturn(List.of(current,
                payment(3, "20.00", PurchasePaymentStatusEnum.COMPLETED, PurchasePaymentApprovalStatusEnum.APPROVED)));
        assertEquals(new BigDecimal("80.00"), service.getOrderPaymentSummary(1L, 2L).getAvailableAmount());
        current.setOrderId(9L);
        assertThrows(BusinessException.class, () -> service.getOrderPaymentSummary(1L, 2L));
        current.setOrderId(1L);
        current.setApprovalStatus(PurchasePaymentApprovalStatusEnum.APPROVED.getCode());
        assertThrows(BusinessException.class, () -> service.getOrderPaymentSummary(1L, 2L));
    }

    @Test void invalidOrderStatesAndOverApplicationAreRejectedBeforeWrite() {
        PurchaseOrder order = order();
        PurchasePaymentDTO dto = new PurchasePaymentDTO();
        dto.setOrderId(1L);
        dto.setPaymentNo("TEST");
        dto.setPaymentAmount(new BigDecimal("1.00"));
        for (ApproveStatusEnum status : ApproveStatusEnum.values()) {
            if (status == ApproveStatusEnum.APPROVED) continue;
            order.setApprovalStatus(status.getValue());
            assertThrows(BusinessException.class, () -> service.insertPayment(dto));
        }
        order.setApprovalStatus(ApproveStatusEnum.APPROVED.getValue());
        when(payments.selectByOrderId(1L)).thenReturn(List.of(
                payment(2, "90.00", PurchasePaymentStatusEnum.PENDING, PurchasePaymentApprovalStatusEnum.PENDING)));
        dto.setPaymentAmount(new BigDecimal("10.01"));
        assertThrows(BusinessException.class, () -> service.insertPayment(dto));
        dto.setPaymentAmount(BigDecimal.ZERO);
        assertThrows(BusinessException.class, () -> service.insertPayment(dto));
        verify(payments, never()).insert(any(PurchasePayment.class));
        dto.setPaymentAmount(new BigDecimal("10.00"));
        // mapper 默认返回 0，不发布事件；额度边界仍需允许写入调用。
        service.insertPayment(dto);
        verify(payments).insert(any(PurchasePayment.class));
    }

    @Test void recalculationWritesAbsolutePaidAmountRepeatedly() throws Exception {
        order();
        when(payments.selectList(any())).thenReturn(List.of(
                payment(1, "20.00", PurchasePaymentStatusEnum.COMPLETED, PurchasePaymentApprovalStatusEnum.APPROVED)));
        ReflectionTestUtils.invokeMethod(service, "updateOrderPaymentInfo", 1L);
        ReflectionTestUtils.invokeMethod(service, "updateOrderPaymentInfo", 1L);
        verify(orders, times(2)).setPaymentSummary(1L, new BigDecimal("20.00"), PurchasePaymentStatusEnum.PARTIALLY_PAID.getValue());
        verify(orders, never()).updatePaymentInfo(any(), any(), any());
        String sql = PurchaseOrderMapper.class.getMethod("setPaymentSummary", Long.class, BigDecimal.class, Integer.class)
                .getAnnotation(Update.class).value()[0];
        assertTrue(sql.contains("paid_amount = #{paidAmount}"));
        assertFalse(sql.contains("paid_amount +"));
    }

    @Test void pendingOrdersUseNamedParametersInsteadOfLegacyApprovalCode() throws Exception {
        String sql = PurchaseOrderMapper.class.getMethod("selectPendingPaymentOrders", Integer.class, Integer.class, Integer.class)
                .getAnnotation(Select.class).value()[0];
        assertTrue(sql.contains("approval_status = #{approved}"));
        assertTrue(sql.contains("#{pending}"));
        assertTrue(sql.contains("#{partial}"));
    }
}
