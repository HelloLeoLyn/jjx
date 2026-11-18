package com.jjx.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.jjx.purchase.domain.dto.PurchasePaymentDTO;
import com.jjx.purchase.domain.enums.PurchasePaymentApprovalStatusEnum;
import com.jjx.purchase.domain.enums.PurchasePaymentStatusEnum;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.mapper.PurchasePaymentMapper;
import com.jjx.purchase.service.impl.PurchasePaymentServiceImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 执行真实Mapper聚合SQL（隔离H2），不把模拟库结果当生产MySQL验收。 */
class PurchasePaymentStatisticsTest {
    SqlSession session;
    JdbcTemplate jdbc;
    PurchasePaymentServiceImpl service;
    LocalDate today = LocalDate.now(), start = today.withDayOfMonth(1);
    String approved = PurchasePaymentApprovalStatusEnum.APPROVED.getCode();
    int paid = PurchasePaymentStatusEnum.COMPLETED.getValue();
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE purchase_payment(payment_id BIGINT PRIMARY KEY, order_id BIGINT, payment_no VARCHAR(50), payment_method VARCHAR(20), approval_status VARCHAR(30), payment_status INT, payment_date DATE, actual_payment_date DATE, payment_amount DECIMAL(20,2))");
        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        config.addMapper(PurchasePaymentMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);
        service = new PurchasePaymentServiceImpl(session.getMapper(PurchasePaymentMapper.class), mock(com.jjx.event.EventPublisher.class), mock(PurchaseOrderMapper.class), mock(com.jjx.system.service.AttachmentRefService.class));
        add(1, "bank", PurchasePaymentApprovalStatusEnum.PENDING.getCode(), PurchasePaymentStatusEnum.PENDING.getValue(), start, null, "100.01");
        add(2, "bank", approved, paid, start.minusDays(1), today, "200.02");
        add(3, "cash", approved, PurchasePaymentStatusEnum.PENDING.getValue(), start.plusMonths(1), null, "300.03");
        add(4, "cash", PurchasePaymentApprovalStatusEnum.REJECTED.getCode(), PurchasePaymentStatusEnum.PENDING.getValue(), start, null, "400.04");
        add(5, "bank", approved, PurchasePaymentStatusEnum.PENDING.getValue(), start.minusDays(1), null, "50.05");
        add(6, "bank", approved, paid, start, start.minusDays(1), "70.07");
    }
    void add(long id, String method, String approval, int status, LocalDate plan, LocalDate actual, String amount) {
        jdbc.update("INSERT INTO purchase_payment VALUES(?,?,?,?,?,?,?,?,?)", id, 1L, "PAY" + id, method, approval, status, plan, actual, new BigDecimal(amount));
    }
    void amount(Map<String,Object> stats, String key, String expected) { assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(stats.get(key).toString())), key); }
    @AfterEach void close() { session.close(); }
    @Test void calendarAndApprovalBoundariesUseExactAmounts() {
        var stats = service.getPaymentStatistics(null);
        amount(stats, "monthDueAmount", "170.08");
        amount(stats, "monthPaidAmount", "200.02");
        amount(stats, "overdueAmount", today.equals(start) ? "50.05" : "150.06");
        assertEquals(6L, ((Number) stats.get("totalCount")).longValue());
    }
    @Test void filtersApplyToAllCardsAndPaginationDoesNotLimitAggregation() {
        var dto = new PurchasePaymentDTO(); dto.setPaymentMethod("bank"); dto.setPaymentStatus(paid); dto.setPageNum(999); dto.setPageSize(1);
        var stats = service.getPaymentStatistics(dto);
        amount(stats, "totalAmount", "270.09"); amount(stats, "monthDueAmount", "70.07"); amount(stats, "monthPaidAmount", "200.02"); amount(stats, "overdueAmount", "0");
        assertEquals(2L, ((Number)stats.get("totalCount")).longValue());
    }
    @Test void noMatchesReturnZerosAndLiteralSearchIsBound() {
        var dto = new PurchasePaymentDTO(); dto.setPaymentNo("' OR 1=1 --");
        var stats = service.getPaymentStatistics(dto);
        amount(stats, "totalAmount", "0"); amount(stats, "monthDueAmount", "0"); amount(stats, "monthPaidAmount", "0"); amount(stats, "overdueAmount", "0");
        assertEquals(0L, ((Number)stats.get("totalCount")).longValue());
    }
}
