package com.jjx.production;

import com.jjx.production.enums.WorkReportStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3 回归测试：报工状态枚举（PENDING/APPROVED/REJECTED/CANCELLED；已删除 SUBMITTED）
 */
class WorkReportStatusEnumTest {

    @Test
    void mappingIsCorrect() {
        assertEquals("PENDING", WorkReportStatusEnum.PENDING.getCode());
        assertEquals("待审批", WorkReportStatusEnum.PENDING.getLabel());
        assertEquals("APPROVED", WorkReportStatusEnum.APPROVED.getCode());
        assertEquals("已通过", WorkReportStatusEnum.APPROVED.getLabel());
        assertEquals("REJECTED", WorkReportStatusEnum.REJECTED.getCode());
        assertEquals("已驳回", WorkReportStatusEnum.REJECTED.getLabel());
        assertEquals("CANCELLED", WorkReportStatusEnum.CANCELLED.getCode());
        assertEquals("已撤销", WorkReportStatusEnum.CANCELLED.getLabel());
    }

    @Test
    void fromCodeAndLabelOf() {
        assertEquals(WorkReportStatusEnum.PENDING, WorkReportStatusEnum.fromCode("PENDING"));
        assertEquals(WorkReportStatusEnum.APPROVED, WorkReportStatusEnum.fromCode("APPROVED"));
        assertEquals(WorkReportStatusEnum.REJECTED, WorkReportStatusEnum.fromCode("REJECTED"));
        assertEquals(WorkReportStatusEnum.CANCELLED, WorkReportStatusEnum.fromCode("CANCELLED"));
        assertNull(WorkReportStatusEnum.fromCode("DRAFT"));
        assertNull(WorkReportStatusEnum.fromCode("SUBMITTED"));
        // 未知值兼容
        assertEquals("HISTORIC_STATUS", WorkReportStatusEnum.labelOf("HISTORIC_STATUS"));
        assertNull(WorkReportStatusEnum.labelOf(null));
    }

    @Test
    void onlyFourStatuses() {
        // P3 正式状态只有 PENDING/APPROVED/REJECTED/CANCELLED（无 SUBMITTED/DRAFT）
        assertEquals(4, WorkReportStatusEnum.values().length);
    }
}
