package com.jjx.system.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperLogDetailBuilderTest {

    @Test
    void buildsFixedSchemaWithChangesAndAttachments() {
        JSONObject detail = JSONUtil.parseObj(OperLogDetailBuilder.build(
                List.of("订单状态：审核中 → 已驳回"),
                List.of(Map.of("id", 7, "fileName", "驳回说明.pdf"))));

        assertEquals(List.of("订单状态：审核中 → 已驳回"), detail.getJSONArray("changes").toList(String.class));
        assertEquals(7, detail.getJSONArray("attachments").getJSONObject(0).getInt("id"));
    }

    @Test
    void normalizesNullAndEmptySidesToArrays() {
        JSONObject changesOnly = JSONUtil.parseObj(OperLogDetailBuilder.changes(List.of("字段变更")));
        JSONObject attachmentsOnly = JSONUtil.parseObj(OperLogDetailBuilder.attachments(List.of(Map.of("id", 8))));
        JSONObject empty = JSONUtil.parseObj(OperLogDetailBuilder.build(null, null));

        assertTrue(changesOnly.getJSONArray("attachments").isEmpty());
        assertTrue(attachmentsOnly.getJSONArray("changes").isEmpty());
        assertTrue(empty.getJSONArray("changes").isEmpty());
        assertTrue(empty.getJSONArray("attachments").isEmpty());
    }
}
