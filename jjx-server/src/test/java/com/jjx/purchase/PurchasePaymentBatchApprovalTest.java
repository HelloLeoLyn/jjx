package com.jjx.purchase;

import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.purchase.domain.dto.PurchasePaymentApprovalDTO;
import com.jjx.purchase.domain.entity.PurchasePayment;
import com.jjx.purchase.domain.enums.PurchasePaymentApprovalStatusEnum;
import com.jjx.purchase.domain.enums.PurchasePaymentStatusEnum;
import com.jjx.purchase.mapper.PurchasePaymentMapper;
import com.jjx.purchase.service.IPurchasePaymentService;
import com.jjx.purchase.service.impl.PurchasePaymentServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Spring实际事务代理+H2回滚验证；不连接或改动业务库。 */
class PurchasePaymentBatchApprovalTest {
    JdbcTemplate jdbc;
    PurchasePaymentMapper mapper;
    EventPublisher events;
    IPurchasePaymentService service;
    MockedStatic<SecurityUtils> actor;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE payment_test(id BIGINT PRIMARY KEY, approval VARCHAR(30), actor VARCHAR(80), opinion VARCHAR(500))");
        jdbc.update("INSERT INTO payment_test(id,approval) VALUES(1,?),(2,?)", pending(), pending());
        mapper = mock(PurchasePaymentMapper.class);
        when(mapper.selectById(anyLong())).thenAnswer(call -> {
            var rows = jdbc.queryForList("SELECT * FROM payment_test WHERE id=?", call.getArgument(0, Long.class));
            if (rows.isEmpty()) return null;
            var row = rows.get(0); var payment = new PurchasePayment();
            payment.setPaymentId(((Number)row.get("ID")).longValue());
            payment.setApprovalStatus((String)row.get("APPROVAL"));
            payment.setPaymentStatus(PurchasePaymentStatusEnum.PENDING.getValue());
            return payment;
        });
        when(mapper.update(any(PurchasePayment.class), any())).thenAnswer(call -> {
            PurchasePayment p = call.getArgument(0);
            return jdbc.update("UPDATE payment_test SET approval=?,actor=?,opinion=? WHERE id=? AND approval=?", p.getApprovalStatus(), p.getApproverName(), p.getApprovalComment(), p.getPaymentId(), pending());
        });
        events = mock(EventPublisher.class);
        var target = mock(PurchasePaymentServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(target, "paymentMapper", mapper);
        ReflectionTestUtils.setField(target, "eventPublisher", events);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (IPurchasePaymentService) proxy.getProxy();
        actor = mockStatic(SecurityUtils.class);
        actor.when(SecurityUtils::getUserId).thenReturn(7L);
        actor.when(SecurityUtils::getDisplayName).thenReturn("真实审批员");
    }
    @AfterEach void close() { actor.close(); }
    String pending() { return PurchasePaymentApprovalStatusEnum.PENDING.getCode(); }
    PurchasePaymentApprovalDTO approve(long id) { return new PurchasePaymentApprovalDTO(id, PurchasePaymentApprovalStatusEnum.APPROVED.getCode(), "统一意见"); }
    String state(long id) { return jdbc.queryForObject("SELECT approval FROM payment_test WHERE id=?", String.class, id); }

    @Test void successCommitsEveryRowWithTrustedActor() {
        assertEquals(2, service.batchApprovePayments(List.of(approve(1), approve(2))));
        assertEquals(PurchasePaymentApprovalStatusEnum.APPROVED.getCode(), state(1));
        assertEquals(PurchasePaymentApprovalStatusEnum.APPROVED.getCode(), state(2));
        assertEquals("真实审批员", jdbc.queryForObject("SELECT actor FROM payment_test WHERE id=2", String.class));
        verify(events, times(2)).fire(eq("purchase.payment.approved"), anyMap());
    }
    @Test void secondMissingRowRollsBackFirstAndDoesNotPublish() {
        assertThrows(BusinessException.class, () -> service.batchApprovePayments(List.of(approve(1), approve(999))));
        assertEquals(pending(), state(1));
        assertNull(jdbc.queryForObject("SELECT actor FROM payment_test WHERE id=1", String.class));
        verify(mapper).update(any(PurchasePayment.class), any());
        verifyNoInteractions(events);
    }
    @Test void staleSecondDecisionRollsBackFirst() {
        jdbc.update("UPDATE payment_test SET approval=? WHERE id=2", PurchasePaymentApprovalStatusEnum.APPROVED.getCode());
        assertThrows(BusinessException.class, () -> service.batchApprovePayments(List.of(approve(1), approve(2))));
        assertEquals(pending(), state(1));
        verifyNoInteractions(events);
    }
    @Test void duplicateAndEmptyBatchesDoNotWrite() {
        assertThrows(BusinessException.class, () -> service.batchApprovePayments(List.of(approve(1), approve(1))));
        assertThrows(BusinessException.class, () -> service.batchApprovePayments(List.of()));
        assertEquals(pending(), state(1));
        verifyNoInteractions(mapper, events);
    }
    @Test void rejectionMustHaveOpinionAtSingleAndBatchEntry() {
        String rejected = PurchasePaymentApprovalStatusEnum.REJECTED.getCode();
        assertThrows(BusinessException.class, () -> service.approvePayment(1L, rejected, "  "));
        assertThrows(BusinessException.class, () -> service.batchApprovePayments(List.of(approve(1), new PurchasePaymentApprovalDTO(2L, rejected, null))));
        assertEquals(pending(), state(1));
        verifyNoInteractions(mapper, events);
        service.batchApprovePayments(List.of(new PurchasePaymentApprovalDTO(1L, rejected, "  金额依据不全  ")));
        assertEquals(rejected, state(1));
        assertEquals("金额依据不全", jdbc.queryForObject("SELECT opinion FROM payment_test WHERE id=1", String.class));
    }
    @Test void failedUpdateIsNotReportedAsSuccessfulBatch() {
        when(mapper.update(any(PurchasePayment.class), any())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.batchApprovePayments(List.of(approve(1))));
        assertEquals(pending(), state(1));
        verifyNoInteractions(events);
    }
}
