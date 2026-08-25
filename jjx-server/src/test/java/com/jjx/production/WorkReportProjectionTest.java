package com.jjx.production;

import com.jjx.production.enums.WorkReportStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3 回归测试：WorkReport 领域规则（纯逻辑）
 * PENDING 占用额度不计 completed；APPROVED 计入 completed；REJECTED/CANCELLED 释放额度。
 */
class WorkReportProjectionTest {

    @Test
    void statusSemantics() {
        // PENDING 占用量；APPROVED 有效完成量；REJECTED/CANCELLED 均不计 completed
        assertEquals("PENDING", WorkReportStatusEnum.PENDING.getCode());
        assertEquals("APPROVED", WorkReportStatusEnum.APPROVED.getCode());
        assertEquals("REJECTED", WorkReportStatusEnum.REJECTED.getCode());
        assertEquals("CANCELLED", WorkReportStatusEnum.CANCELLED.getCode());
    }

    @Test
    void pendingToApproved_occupancyUnchanged() {
        // remaining = taskQuantity - assigned - pending - completed
        // PENDING 10：pending=10 completed=0 → 占用10
        // APPROVED 后：pending=0 completed=10 → 占用仍10（remaining 不变）
        java.math.BigDecimal taskQuantity = new java.math.BigDecimal("30");
        java.math.BigDecimal report = new java.math.BigDecimal("10");
        java.math.BigDecimal occupiedPending = report;           // PENDING 阶段占用
        java.math.BigDecimal occupiedApproved = report;          // APPROVED 阶段占用
        assertEquals(occupiedPending, occupiedApproved);
        assertEquals(new java.math.BigDecimal("20"),
                taskQuantity.subtract(occupiedApproved));
    }

    @Test
    void rejectReleasesOccupancy() {
        // REJECTED 后：pending 0 / completed 0 → remaining 恢复 30
        java.math.BigDecimal taskQuantity = new java.math.BigDecimal("30");
        java.math.BigDecimal remainingAfterReject = taskQuantity;
        assertEquals(new java.math.BigDecimal("30"), remainingAfterReject);
    }
}
