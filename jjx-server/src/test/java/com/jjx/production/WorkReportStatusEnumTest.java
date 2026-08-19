package com.jjx.production;

import com.jjx.production.enums.WorkReportStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2-B 回归测试：报工状态枚举
 */
class WorkReportStatusEnumTest {

    @Test
    void mappingIsCorrect() {
        assertEquals("SUBMITTED", WorkReportStatusEnum.SUBMITTED.getCode());
        assertEquals("已提交", WorkReportStatusEnum.SUBMITTED.getLabel());
        assertEquals("CANCELLED", WorkReportStatusEnum.CANCELLED.getCode());
        assertEquals("已撤销", WorkReportStatusEnum.CANCELLED.getLabel());
    }

    @Test
    void fromCodeAndLabelOf() {
        assertEquals(WorkReportStatusEnum.SUBMITTED, WorkReportStatusEnum.fromCode("SUBMITTED"));
        assertEquals(WorkReportStatusEnum.CANCELLED, WorkReportStatusEnum.fromCode("CANCELLED"));
        assertNull(WorkReportStatusEnum.fromCode("DRAFT"));
        assertNull(WorkReportStatusEnum.fromCode("APPROVED"));
        assertNull(WorkReportStatusEnum.fromCode("REJECTED"));
        // 未知值兼容
        assertEquals("HISTORIC_STATUS", WorkReportStatusEnum.labelOf("HISTORIC_STATUS"));
        assertNull(WorkReportStatusEnum.labelOf(null));
    }

    @Test
    void onlyTwoStatuses() {
        // 正式状态只有 SUBMITTED/CANCELLED（无 DRAFT/APPROVED/REJECTED）
        assertEquals(2, WorkReportStatusEnum.values().length);
    }
}
