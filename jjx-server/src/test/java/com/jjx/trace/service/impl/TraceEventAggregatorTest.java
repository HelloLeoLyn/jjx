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

/** 查看流水读时归并：一次业务动作 + 它的证据附件只显示一条主流水。 */
class TraceEventAggregatorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 20, 10, 10, 48);
    private static final String OPERATOR = "系统管理员";

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

    @Test
    void mergesCustomerConfirmEvidenceByRefIdBeyondLegacyWindow() {
        UnifiedTraceEventVO attachment = attachmentEvent("附件新增", T0,
                List.of("新增附件（整单）：「客户需求.jpg」"),
                List.of(attachmentRef(2, 239, "客户需求.jpg")));
        UnifiedTraceEventVO confirm = actionEvent("客户确认报价", T0.plusSeconds(5), 2, null,
                "{\"quotationId\":1,\"attachmentRefIds\":[2]}");

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(List.of(attachment, confirm));

        assertEquals(1, merged.size());
        assertEquals("客户确认报价", merged.get(0).getAction());
        JSONObject detail = JSONUtil.parseObj(merged.get(0).getDetail());
        assertTrue(detail.getJSONArray("changes").toString().contains("客户需求.jpg"));
        assertEquals(1, detail.getJSONArray("attachments").size());
        assertEquals("客户需求.jpg", detail.getJSONArray("attachments").getJSONObject(0).getStr("fileName"));
    }

    @Test
    void mergesReviewApproveEvidenceByRefId() {
        UnifiedTraceEventVO attachment = attachmentEvent("附件新增", T0,
                List.of("新增附件（整单）：「检验报告.pdf」"),
                List.of(attachmentRef(3, 240, "检验报告.pdf")));
        UnifiedTraceEventVO review = actionEvent("审核报价单", T0.plusSeconds(8), 6, null,
                "{\"approved\":true,\"quotationId\":1,\"attachmentRefIds\":[3]}");

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(List.of(attachment, review));

        assertEquals(1, merged.size());
        assertEquals("审核报价单", merged.get(0).getAction());
        JSONObject detail = JSONUtil.parseObj(merged.get(0).getDetail());
        assertEquals(1, detail.getJSONArray("attachments").size());
    }

    @Test
    void keepsBothRowsWhenRefIdsExplicitlyMismatch() {
        UnifiedTraceEventVO attachment = attachmentEvent("附件新增", T0,
                List.of("新增附件（整单）：「客户需求.jpg」"),
                List.of(attachmentRef(2, 239, "客户需求.jpg")));
        UnifiedTraceEventVO confirm = actionEvent("客户确认报价", T0.plusSeconds(5), 2, null,
                "{\"quotationId\":1,\"attachmentRefIds\":[7]}");

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(List.of(attachment, confirm));

        assertEquals(2, merged.size());
        assertEquals("附件新增", merged.get(0).getAction());
        assertEquals("客户确认报价", merged.get(1).getAction());
    }

    @Test
    void mergesAdjacentActionWithinSixtySecondsWithoutRefIds() {
        UnifiedTraceEventVO attachment = attachmentEvent("附件新增", T0,
                List.of("新增附件（整单）：「客户需求.jpg」"), List.of());
        UnifiedTraceEventVO send = actionEvent("发送报价单", T0.plusSeconds(30), 2, null, null);

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(List.of(attachment, send));

        assertEquals(1, merged.size());
        assertEquals("发送报价单", merged.get(0).getAction());
        JSONObject detail = JSONUtil.parseObj(merged.get(0).getDetail());
        assertTrue(detail.getJSONArray("changes").toString().contains("客户需求.jpg"));
    }

    @Test
    void doesNotMergeAdjacentActionBeyondSixtySeconds() {
        UnifiedTraceEventVO attachment = attachmentEvent("附件新增", T0,
                List.of("新增附件（整单）：「客户需求.jpg」"), List.of());
        UnifiedTraceEventVO send = actionEvent("发送报价单", T0.plusSeconds(61), 2, null, null);

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(List.of(attachment, send));

        assertEquals(2, merged.size());
    }

    @Test
    void doesNotMergeAdjacentActionFromDifferentOperator() {
        UnifiedTraceEventVO attachment = attachmentEvent("附件新增", T0,
                List.of("新增附件（整单）：「客户需求.jpg」"), List.of());
        UnifiedTraceEventVO send = actionEvent("发送报价单", T0.plusSeconds(5), 2, null, null);
        send.setOperatorName("李四");

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(List.of(attachment, send));

        assertEquals(2, merged.size());
    }

    @Test
    void keepsBothRowsWhenActionDetailIsBroken() {
        UnifiedTraceEventVO attachment = attachmentEvent("附件新增", T0,
                List.of("新增附件（整单）：「客户需求.jpg」"),
                List.of(attachmentRef(2, 239, "客户需求.jpg")));
        UnifiedTraceEventVO confirm = actionEvent("客户确认报价", T0.plusSeconds(5), 2,
                "坏掉的详情", "{\"quotationId\":1,\"attachmentRefIds\":[2]}");

        List<UnifiedTraceEventVO> merged = TraceEventAggregator.mergeAttachmentEvents(List.of(attachment, confirm));

        assertEquals(2, merged.size());
        assertEquals("坏掉的详情", merged.get(1).getDetail());
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
        event.setOperatorName(OPERATOR);
        event.setDetail(detail);
        return event;
    }

    private UnifiedTraceEventVO saveEvent(String action, LocalDateTime time, String detail) {
        return actionEvent(action, time, 2, detail, null);
    }

    private UnifiedTraceEventVO actionEvent(String action, LocalDateTime time, int businessType,
                                            String detail, String operParam) {
        UnifiedTraceEventVO event = new UnifiedTraceEventVO();
        event.setEventId("oper-" + action + time);
        event.setAction(action);
        event.setTime(time);
        event.setBusinessType(businessType);
        event.setOperUrl("/sales/quotation");
        event.setBizType("quotation");
        event.setBizId("1");
        event.setOperatorName(OPERATOR);
        event.setDetail(detail);
        event.setOperParam(operParam);
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
