package com.jjx.production;

import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.enums.QualityInspectionTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-01 回归测试：质检类型/结果枚举映射
 */
class QualityInspectionEnumsTest {

    @Test
    void typeMappingIsCorrect() {
        assertEquals("IQC", QualityInspectionTypeEnum.IQC.getCode());
        assertEquals("来料检验", QualityInspectionTypeEnum.IQC.getLabel());
        assertEquals("IPQC", QualityInspectionTypeEnum.IPQC.getCode());
        assertEquals("过程检验", QualityInspectionTypeEnum.IPQC.getLabel());
        assertEquals("FQC", QualityInspectionTypeEnum.FQC.getCode());
        assertEquals("完工检验", QualityInspectionTypeEnum.FQC.getLabel());
        assertEquals("OQC", QualityInspectionTypeEnum.OQC.getCode());
        assertEquals("出货检验", QualityInspectionTypeEnum.OQC.getLabel());
    }

    @Test
    void typeLabelOfUnknownValueReturnsRawForCompat() {
        assertEquals("HISTORIC_TYPE", QualityInspectionTypeEnum.labelOf("HISTORIC_TYPE"));
        assertNull(QualityInspectionTypeEnum.fromCode("HISTORIC_TYPE"));
    }

    @Test
    void resultMappingIsCorrect() {
        assertEquals("pending", QualityInspectionResultEnum.PENDING.getCode());
        assertEquals("待检", QualityInspectionResultEnum.PENDING.getLabel());
        assertEquals("pass", QualityInspectionResultEnum.PASS.getCode());
        assertEquals("合格", QualityInspectionResultEnum.PASS.getLabel());
        assertEquals("fail", QualityInspectionResultEnum.FAIL.getCode());
        assertEquals("不合格", QualityInspectionResultEnum.FAIL.getLabel());
    }

    @Test
    void resultLabelOfUnknownValueReturnsRawForCompat() {
        assertEquals("HISTORIC_RESULT", QualityInspectionResultEnum.labelOf("HISTORIC_RESULT"));
        assertNull(QualityInspectionResultEnum.fromCode("HISTORIC_RESULT"));
    }
}
