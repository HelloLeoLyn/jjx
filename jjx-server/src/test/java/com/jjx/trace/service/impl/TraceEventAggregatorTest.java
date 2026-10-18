package com.jjx.trace.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jjx.system.utils.OperLogDetailBuilder;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 查看流水读时归并（dev-20261018-004）：一次保存顺带落定的附件并入保存行。 */
class TraceEventAggregatorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 18, 11, 8, 11);

    @Test
    void mergesAttachmentBindingIntoFollowingSave() {
        UnifiedTraceEventVO attachment = attachmentEvent("附件新增", T0,
                List.of("新增附件（产品 YL001MEOO）：「工程图.jpg」"),
                List.of(attachmentRef(2, 180, "工程图.jpg")));
        UnifiedTraceEventVO save = saveEvent("修改报价单", T0,
                OperLogDetailBuilder.build(List.of("明细[YL001MEOO]数量:1→3"), List.of()));

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(List.of(attachment, save));

        assertEquals(1, merged.size());
        JSONObject detail = JSONUtil.parseObj(merged.get(0).getDetail());
        assertEquals(2, detail.getJSONArray("changes").size());
        assertTrue(detail.getJSONArray("changes").toString().contains("工程图.jpg"));
        assertEquals(1, detail.getJSONArray("attachments").size());
    }

    @Test
    void keepsStandaloneUnbindOutsideTheSaveWindow() {
        UnifiedTraceEventVO unbind = attachmentEvent("附件解绑", T0.minusSeconds(39),
                List.of("附件「YL1025-BOM - 副本.jpg」（产品 YL001MEOO）：解绑"), List.of());
        UnifiedTraceEventVO attachment = attachmentEvent("附件新增", T0,
                List.of("新增附件（产品 YL001MEOO）：「工程图.jpg」"),
                List.of(attachmentRef(2, 180, "工程图.jpg")));
        UnifiedTraceEventVO save = saveEvent("修改报价单", T0,
                OperLogDetailBuilder.build(List.of("明细[YL001MEOO]数量:1→3"), List.of()));

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(
                List.of(unbind, attachment, save));

        assertEquals(2, merged.size());
        assertEquals("附件解绑", merged.get(0).getAction());
        assertEquals("修改报价单", merged.get(1).getAction());
    }

    @Test
    void keepsAttachmentRowWhenNoSaveFollows() {
        UnifiedTraceEventVO attachment = attachmentEvent("附件新增", T0,
                List.of("新增附件（整单）：「产品作业规范.jpg」"),
                List.of(attachmentRef(4, 182, "产品作业规范.jpg")));

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(List.of(attachment));

        assertEquals(1, merged.size());
        assertEquals("附件新增", merged.get(0).getAction());
    }

    @Test
    void doesNotMergeAcrossDifferentBusinessObjects() {
        UnifiedTraceEventVO attachment = attachmentEvent("附件新增", T0,
                List.of("新增附件：「工程图.jpg」"), List.of(attachmentRef(2, 180, "工程图.jpg")));
        attachment.setBizId("1");
        UnifiedTraceEventVO save = saveEvent("修改报价单", T0,
                OperLogDetailBuilder.build(List.of(), List.of()));
        save.setBizId("2");

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(List.of(attachment, save));

        assertEquals(2, merged.size());
    }

    @Test
    void keepsBothRowsWhenAttachmentDetailIsNotJson() {
        UnifiedTraceEventVO attachment = attachmentEventWithRawDetail("附件新增", T0, "纯文本，不是 JSON");
        UnifiedTraceEventVO save = saveEvent("修改报价单", T0,
                OperLogDetailBuilder.build(List.of("明细[YL001MEOO]数量:1→3"), List.of()));

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(List.of(attachment, save));

        assertEquals(2, merged.size());
        assertEquals("纯文本，不是 JSON", merged.get(0).getDetail());
    }

    @Test
    void mergesSeveralAttachmentRowsAndDeduplicatesAttachments() {
        UnifiedTraceEventVO added = attachmentEvent("附件新增", T0,
                List.of("新增附件：「工程图.jpg」"),
                List.of(attachmentRef(2, 180, "工程图.jpg")));
        UnifiedTraceEventVO unbound = attachmentEvent("附件解绑", T0,
                List.of("附件「工程图.jpg」：解绑"),
                List.of(attachmentRef(2, 180, "工程图.jpg")));
        UnifiedTraceEventVO save = saveEvent("修改报价单", T0,
                OperLogDetailBuilder.build(List.of("明细[YL001MEOO]数量:1→3"), List.of()));

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(
                List.of(added, unbound, save));

        assertEquals(1, merged.size());
        JSONObject detail = JSONUtil.parseObj(merged.get(0).getDetail());
        assertEquals(3, detail.getJSONArray("changes").size());
        assertEquals(1, detail.getJSONArray("attachments").size());
    }

    @Test
    void fillsEmptySaveDetailWithAttachmentChanges() {
        UnifiedTraceEventVO attachment = attachmentEvent("附件新增", T0,
                List.of("新增附件（产品 YL001MEOO）：「YL1025-BOM - 副本.jpg」"),
                List.of(attachmentRef(1, 179, "YL1025-BOM - 副本.jpg")));
        UnifiedTraceEventVO save = saveEvent("新增报价单", T0, null);

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(List.of(attachment, save));

        assertEquals(1, merged.size());
        JSONObject detail = JSONUtil.parseObj(merged.get(0).getDetail());
        assertEquals(1, detail.getJSONArray("changes").size());
        assertEquals(1, detail.getJSONArray("attachments").size());
    }

    private UnifiedTraceEventVO attachmentEvent(String action, LocalDateTime time,
                                                List<String> changes, List<JSONObject> attachments) {
        return attachmentEventWithRawDetail(action, time, OperLogDetailBuilder.build(changes, attachments));
    }

    private UnifiedTraceEventVO attachmentEventWithRawDetail(String action, LocalDateTime time, String detail) {
        UnifiedTraceEventVO event = new UnifiedTraceEventVO();
        event.setEventId("oper-" + action + time);
        event.setAction(action);
        event.setTime(time);
        event.setBusinessType(1);
        event.setOperUrl("attachment.ref." + action);
        event.setBizType("quotation");
        event.setBizId("1");
        event.setDetail(detail);
        return event;
    }

    private UnifiedTraceEventVO saveEvent(String action, LocalDateTime time, String detail) {
        UnifiedTraceEventVO event = new UnifiedTraceEventVO();
        event.setEventId("oper-" + action + time);
        event.setAction(action);
        event.setTime(time);
        event.setBusinessType(2);
        event.setOperUrl("/sales/quotation");
        event.setBizType("quotation");
        event.setBizId("1");
        event.setDetail(detail);
        return event;
    }

    private JSONObject attachmentRef(long refId, long attachmentId, String fileName) {
        JSONObject item = new JSONObject();
        item.set("refId", refId);
        item.set("id", attachmentId);
        item.set("fileName", fileName);
        return item;
    }
}
