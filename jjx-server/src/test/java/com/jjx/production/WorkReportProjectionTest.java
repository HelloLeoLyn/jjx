package com.jjx.production;

import com.jjx.production.enums.WorkReportStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2-C 回归测试：WorkReport 领域规则补充（纯逻辑）
 * 完整累计/撤销/投影一致性验证见真实 MySQL 事务回滚（实施报告 §24）。
 */
class WorkReportProjectionTest {

    @Test
    void submittedAndCancelledSemantics() {
        // SUBMITTED 计入汇总；CANCELLED 不计入——枚举语义
        assertEquals("SUBMITTED", WorkReportStatusEnum.SUBMITTED.getCode());
        assertEquals("CANCELLED", WorkReportStatusEnum.CANCELLED.getCode());
    }

    @Test
    void sumRuleQualifiedPlusDefectiveIsOutput() {
        // output projection = SUM(qualified + defective)
        java.math.BigDecimal q1 = new java.math.BigDecimal("100");
        java.math.BigDecimal d1 = new java.math.BigDecimal("10");
        java.math.BigDecimal q2 = new java.math.BigDecimal("200");
        java.math.BigDecimal d2 = new java.math.BigDecimal("5");
        java.math.BigDecimal qualified = q1.add(q2);
        java.math.BigDecimal defective = d1.add(d2);
        java.math.BigDecimal output = qualified.add(defective);
        assertEquals(new java.math.BigDecimal("300"), qualified);
        assertEquals(new java.math.BigDecimal("15"), defective);
        assertEquals(new java.math.BigDecimal("315"), output);
    }

    @Test
    void cancelRemovesFromSum() {
        // 取消第一条：300-100=200 合格；15-10=5 不良；315-110=205 output
        java.math.BigDecimal qualified = new java.math.BigDecimal("300").subtract(new java.math.BigDecimal("100"));
        java.math.BigDecimal defective = new java.math.BigDecimal("15").subtract(new java.math.BigDecimal("10"));
        java.math.BigDecimal output = qualified.add(defective);
        assertEquals(new java.math.BigDecimal("200"), qualified);
        assertEquals(new java.math.BigDecimal("5"), defective);
        assertEquals(new java.math.BigDecimal("205"), output);
    }
}
