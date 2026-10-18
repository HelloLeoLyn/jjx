package com.jjx.sales;

import com.jjx.sales.domain.entity.SalesSampleProcess;
import com.jjx.sales.mapper.SalesSampleProcessMapper;
import com.jjx.sales.service.impl.SampleOrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * dev-20261018-036：印刷名称历史联想只取 majorCategory=PRINT 的工序列。
 *
 * 说明：本测试 mock mapper 直接验证 processHistory() 的取数口径（不连真实业务库），
 * 只覆盖字段过滤/去重/trim，不覆盖印刷行保存与页面交互。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SamplePrintHistoryTest {

    @Mock SalesSampleProcessMapper processes;

    /** 其余构造依赖由 Mockito 注入 null；本测试只走 processHistory() */
    @InjectMocks SampleOrderServiceImpl service;

    private SalesSampleProcess row(String majorCategory, String processName, String params) {
        SalesSampleProcess p = new SalesSampleProcess();
        p.setMajorCategory(majorCategory);
        p.setProcessName(processName);
        p.setCustomProcessParams(params);
        return p;
    }

    private void given(List<SalesSampleProcess> rows) {
        when(processes.selectList(any())).thenReturn(rows);
    }

    @Test
    void printNamesOnlyFromPrintCategory() {
        given(new ArrayList<>(List.of(
                row("PRINT", "丝印", null),
                row("ASSEMBLY", "冲型", null),
                row("ASSEMBLY", "贴合", null),
                row("PRINT", "移印", null))));

        Map<String, List<String>> history = service.processHistory();

        assertEquals(List.of("丝印", "移印"), history.get("printNames"));
        assertFalse(history.get("printNames").contains("冲型"));
        assertFalse(history.get("printNames").contains("贴合"));
    }

    @Test
    void printNamesTrimmedAndDeduplicatedInOrder() {
        given(new ArrayList<>(List.of(
                row("PRINT", " 丝印 ", null),
                row("PRINT", "丝印", null),
                row("PRINT", "   ", null),
                row("PRINT", null, null),
                row(null, "无大类", null),
                row("PRINT", "网印", null))));

        Map<String, List<String>> history = service.processHistory();

        assertEquals(List.of("丝印", "网印"), history.get("printNames"));
        assertFalse(history.get("printNames").contains("无大类"));
    }

    @Test
    void emptyHistoryReturnsEmptyPrintNames() {
        given(new ArrayList<>());

        Map<String, List<String>> history = service.processHistory();

        assertNotNull(history.get("printNames"));
        assertTrue(history.get("printNames").isEmpty());
    }

    @Test
    void colorAndInkHistoryUnchanged() {
        given(new ArrayList<>(List.of(
                row("PRINT", "丝印", "{\"colorNo\":\" PANTONE 186C \",\"inkNo\":\" INK-01 \"}"),
                row("PRINT", "移印", "{\"colorNo\":\"PANTONE 186C\"}"))));

        Map<String, List<String>> history = service.processHistory();

        // 其他历史字段口径不变（仍从 customProcessParams 的 colorNo/inkNo 提取，去重/trim）
        assertEquals(List.of("PANTONE 186C"), history.get("colorNos"));
        assertEquals(List.of("INK-01"), history.get("inkNos"));
    }
}
