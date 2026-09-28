package com.jjx.quality;

import com.jjx.production.enums.QualityInspectionResultEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityResultMatchTest {

    @Test
    void passMatchIsCaseInsensitiveAndNullSafe() {
        assertTrue(QualityInspectionResultEnum.isPass("pass"));
        assertTrue(QualityInspectionResultEnum.isPass("PASS"));
        assertFalse(QualityInspectionResultEnum.isPass(null));
        assertFalse(QualityInspectionResultEnum.isPass("pending"));
    }

    @Test
    void failMatchIsCaseInsensitiveAndNullSafe() {
        assertTrue(QualityInspectionResultEnum.isFail("fail"));
        assertTrue(QualityInspectionResultEnum.isFail("FAIL"));
        assertFalse(QualityInspectionResultEnum.isFail(null));
        assertFalse(QualityInspectionResultEnum.isFail("pending"));
    }
}
