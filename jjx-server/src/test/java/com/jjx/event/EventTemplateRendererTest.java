package com.jjx.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知/任务模板渲染器单测（dev-20260921-012）。
 * 覆盖：平铺键、${} 写法、{键|备选} 兜底、取值缺失→空串并回报键名、特殊字符转义。
 */
class EventTemplateRendererTest {

    private Map<String, Object> payload(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("平铺键替换：{quotationNo} → 真实单号")
    void shouldReplacePlainKey() {
        String out = EventTemplateRenderer.render(
                "报价单【{quotationNo}】已发送给客户",
                payload("quotationNo", "QT2609210001"));
        assertEquals("报价单【QT2609210001】已发送给客户", out);
    }

    @Test
    @DisplayName("兼容 ${key} 写法")
    void shouldReplaceDollarForm() {
        String out = EventTemplateRenderer.render("订单【${orderNo}】已完成", payload("orderNo", "SO260921001"));
        assertEquals("订单【SO260921001】已完成", out);
    }

    @Test
    @DisplayName("{bizNo|bizId} 兜底：单号优先，没有单号用内部编号")
    void shouldFallbackToSecondKey() {
        assertEquals("报价单【QT001】已改单",
                EventTemplateRenderer.render("报价单【{bizNo|bizId}】已改单", payload("bizNo", "QT001", "bizId", 7L)));
        assertEquals("报价单【7】已改单",
                EventTemplateRenderer.render("报价单【{bizNo|bizId}】已改单", payload("bizId", 7L)));
    }

    @Test
    @DisplayName("取不到的键渲染成空串，并回报键名（不再原样吐花括号）")
    void shouldRenderMissingKeyAsEmpty() {
        EventTemplateRenderer.Result result = EventTemplateRenderer.renderWithMissing(
                "入库单【{inboundNo}】待来料检验", payload("bizId", 4L));
        assertEquals("入库单【】待来料检验", result.rendered());
        assertEquals(1, result.missingKeys().size());
        assertTrue(result.missingKeys().contains("inboundNo"));
    }

    @Test
    @DisplayName("备选全缺失时也回报原始表达式")
    void shouldReportWholeExpressionWhenBothMissing() {
        EventTemplateRenderer.Result result = EventTemplateRenderer.renderWithMissing(
                "【{bizNo|bizId}】", payload("other", "x"));
        assertEquals("【】", result.rendered());
        assertTrue(result.missingKeys().contains("bizNo|bizId"));
    }

    @Test
    @DisplayName("值里含 $ 与反斜杠时按字面输出")
    void shouldEscapeReplacementValue() {
        String out = EventTemplateRenderer.render("备注：{remark}", payload("remark", "$5 \\ 折"));
        assertEquals("备注：$5 \\ 折", out);
    }

    @Test
    @DisplayName("模板为 null 原样返回 null；payload 为空时不改动模板")
    void shouldHandleNullOrEmptyInputs() {
        assertNull(EventTemplateRenderer.render(null, payload("a", 1)));
        assertEquals("无占位符文本", EventTemplateRenderer.render("无占位符文本", new HashMap<>()));
    }
}
