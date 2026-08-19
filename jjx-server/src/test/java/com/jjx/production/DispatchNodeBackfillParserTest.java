package com.jjx.production;

import com.jjx.production.migration.DispatchNodeBackfillParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-A 回归测试：legacy operators JSON → Node 责任链 解析器
 * 纯转换函数测试，不写任何正式业务数据。
 */
class DispatchNodeBackfillParserTest {

    @Test
    void nullOrEmptyOperatorsYieldsEmptyChain() {
        assertTrue(DispatchNodeBackfillParser.parseChain(null).isEmpty());
        assertTrue(DispatchNodeBackfillParser.parseChain("").isEmpty());
        assertTrue(DispatchNodeBackfillParser.parseChain("   ").isEmpty());
        assertTrue(DispatchNodeBackfillParser.parseChain("[]").isEmpty());
    }

    @Test
    void singleOperatorYieldsOneDraft() {
        List<DispatchNodeBackfillParser.NodeDraft> drafts =
                DispatchNodeBackfillParser.parseChain("[{\"userId\":96,\"userName\":\"冲型车间主任\",\"level\":1}]");
        assertEquals(1, drafts.size());
        assertEquals(96L, drafts.get(0).assigneeId);
        assertEquals("冲型车间主任", drafts.get(0).assigneeName);
        assertFalse(drafts.get(0).ambiguous);
    }

    @Test
    void multiLevelChainKeepsArrayOrder() {
        String json = "[{\"userId\":96,\"userName\":\"车间主任\",\"level\":1},"
                + "{\"userId\":98,\"userName\":\"组长\",\"level\":2},"
                + "{\"userId\":104,\"userName\":\"工人\",\"level\":3}]";
        List<DispatchNodeBackfillParser.NodeDraft> drafts = DispatchNodeBackfillParser.parseChain(json);
        assertEquals(3, drafts.size());
        assertEquals(96L, drafts.get(0).assigneeId);
        assertEquals(98L, drafts.get(1).assigneeId);
        assertEquals(104L, drafts.get(2).assigneeId);
        assertFalse(drafts.get(0).ambiguous);
        assertFalse(drafts.get(1).ambiguous);
        assertFalse(drafts.get(2).ambiguous);
    }

    @Test
    void sameLevelMultiplePeopleMarksAmbiguityButKeepsArrayOrder() {
        // dispatch 3 案例：同 level:1 两人（组长+工人），按 JSON 数组顺序串链，标记歧义
        String json = "[{\"userId\":98,\"userName\":\"印刷一组组长\",\"level\":1},"
                + "{\"userId\":104,\"userName\":\"印刷一组工人\",\"level\":1}]";
        List<DispatchNodeBackfillParser.NodeDraft> drafts = DispatchNodeBackfillParser.parseChain(json);
        assertEquals(2, drafts.size());
        assertEquals(98L, drafts.get(0).assigneeId);
        assertEquals(104L, drafts.get(1).assigneeId);
        assertTrue(drafts.get(0).ambiguous);
        assertTrue(drafts.get(1).ambiguous);
    }

    @Test
    void differentLevelsWithMultiplePeopleOnlyMarksAmbiguousLevel() {
        String json = "[{\"userId\":96,\"userName\":\"车间主任\",\"level\":1},"
                + "{\"userId\":98,\"userName\":\"组长A\",\"level\":2},"
                + "{\"userId\":99,\"userName\":\"组长B\",\"level\":2}]";
        List<DispatchNodeBackfillParser.NodeDraft> drafts = DispatchNodeBackfillParser.parseChain(json);
        assertEquals(3, drafts.size());
        assertFalse(drafts.get(0).ambiguous); // level 1 只有一人
        assertTrue(drafts.get(1).ambiguous);  // level 2 两人
        assertTrue(drafts.get(2).ambiguous);
    }

    @Test
    void missingLevelDefaultsToLevelOne() {
        String json = "[{\"userId\":96,\"userName\":\"车间主任\"}]";
        List<DispatchNodeBackfillParser.NodeDraft> drafts = DispatchNodeBackfillParser.parseChain(json);
        assertEquals(1, drafts.size());
        assertEquals(96L, drafts.get(0).assigneeId);
    }

    @Test
    void malformedJsonThrowsBackfillParseException() {
        assertThrows(DispatchNodeBackfillParser.BackfillParseException.class,
                () -> DispatchNodeBackfillParser.parseChain("not-a-json"));
        assertThrows(DispatchNodeBackfillParser.BackfillParseException.class,
                () -> DispatchNodeBackfillParser.parseChain("[{\"userId\":}"));
        // 对象而非数组：按异常处理（保守，不产出链）
        assertThrows(DispatchNodeBackfillParser.BackfillParseException.class,
                () -> DispatchNodeBackfillParser.parseChain("{\"userId\":1}"));
    }

    @Test
    void entriesWithoutUserIdAreSkipped() {
        String json = "[{\"userName\":\"无ID\",\"level\":1},"
                + "{\"userId\":104,\"userName\":\"印刷一组工人\",\"level\":1}]";
        List<DispatchNodeBackfillParser.NodeDraft> drafts = DispatchNodeBackfillParser.parseChain(json);
        // 无 userId 项被跳过，仅保留有效项
        assertEquals(1, drafts.size());
        assertEquals(104L, drafts.get(0).assigneeId);
    }

    @Test
    void markersAreStableConstants() {
        assertEquals("LEGACY_BACKFILL", DispatchNodeBackfillParser.MARKER_LEGACY_BACKFILL);
        assertEquals("LEGACY_AMBIGUOUS_ORDER", DispatchNodeBackfillParser.MARKER_AMBIGUOUS_ORDER);
        assertEquals("ORG_RECONSTRUCTED", DispatchNodeBackfillParser.MARKER_ORG_RECONSTRUCTED);
    }
}
