package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.service.impl.WorkReportActionServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkReportActionServicePrecisionTest {

    @Test
    void submitRejectsQuantityWithThreeDecimalPlaces() {
        WorkReportActionServiceImpl service = new WorkReportActionServiceImpl(
                null, null, null, null, null, null, null, null, null, null, null);
        WorkReportSubmitDTO dto = new WorkReportSubmitDTO();
        dto.setTaskId(1L);
        dto.setExecutionId(2L);
        dto.setQualifiedQuantity(new BigDecimal("1.001"));
        dto.setDefectiveQuantity(BigDecimal.ZERO);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(dto, "worker", 3L));

        assertTrue(error.getMessage().contains("合格数量最多 2 位小数"));
    }
}
