package com.jjx.quality.service.impl;

import com.jjx.quality.domain.entity.QualityNcrPieceDefect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 不良件级追溯的纯函数单测 —— dev-20260924-004。
 *
 * <p>覆盖两处口径（方案 §3/§5）：</p>
 * <ul>
 *   <li>逐件实测值：检验单 sample_values 按竖线（半角/全角）拆分，取第 seq 段；越界/为空返回 null（不阻断判定）</li>
 *   <li>缺陷分级严重度：CR 致命 &gt; MA 严重 &gt; MI 轻微 &gt; 兜底「其他」（null）</li>
 * </ul>
 */
class QualityPieceAttributionTest {

    @Test
    void splitsSampleValuesByPipe() {
        assertEquals("3.2", QualityNcrPieceServiceImpl.sampleSegment("3.1|3.2|3.3", 2));
        assertEquals("3.1", QualityNcrPieceServiceImpl.sampleSegment("3.1|3.2", 1));
    }

    @Test
    void supportsFullWidthPipe() {
        assertEquals("合格", QualityNcrPieceServiceImpl.sampleSegment("合格｜不合格", 1));
        assertEquals("不合格", QualityNcrPieceServiceImpl.sampleSegment("合格｜不合格", 2));
    }

    @Test
    void returnsNullWhenSegmentMissingOrBlank() {
        assertNull(QualityNcrPieceServiceImpl.sampleSegment("3.1", 2));
        assertNull(QualityNcrPieceServiceImpl.sampleSegment("3.1||3.3", 2));
        assertNull(QualityNcrPieceServiceImpl.sampleSegment(null, 1));
        assertNull(QualityNcrPieceServiceImpl.sampleSegment("  ", 1));
        assertNull(QualityNcrPieceServiceImpl.sampleSegment("3.1|3.2", 0));
    }

    @Test
    void severityOrderCrMaMiFallback() {
        assertEquals(3, QualityNcrPieceDefect.severity("CR"));
        assertEquals(2, QualityNcrPieceDefect.severity("ma"));
        assertEquals(1, QualityNcrPieceDefect.severity("Mi"));
        assertEquals(0, QualityNcrPieceDefect.severity(null));
        assertEquals(0, QualityNcrPieceDefect.severity("其他"));
    }
}
