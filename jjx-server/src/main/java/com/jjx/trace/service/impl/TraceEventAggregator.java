package com.jjx.trace.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jjx.system.utils.OperLogDetailBuilder;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 「查看流水」的读时归并：把同一次单据保存顺带产生的附件变动并回那条保存记录。
 *
 * <p>背景（dev-20261018-004）：一次保存如果同时落定附件，会写两条 sys_oper_log ——
 * 附件引用在单据事务提交后写「附件新增」，单据本身在接口返回后写「修改报价单」，
 * 于是流水里一次保存看起来像两步。这里只在查询结果上做投影：日志表仍是一条日志一行（唯一真源），
 * 不改表结构、不改写入路径。</p>
 *
 * <p>归并规则：</p>
 * <ol>
 *   <li>附件行 = {@code oper_url} 以 {@code attachment.ref.} 开头；保存行 = {@code business_type}
 *       为 INSERT(1)/UPDATE(2) 且不是附件行。</li>
 *   <li>只归并「一串连续的附件行」中紧贴保存行、且与该保存行相差不超过 2 秒的部分
 *       （同一事务内两条日志是毫秒级先后写入；隔得更久的是独立操作，例如单独解绑）。</li>
 *   <li>两行必须属于同一业务对象（biz_type + biz_id 相同）。</li>
 *   <li>detail 解析失败或附件行没有 detail 时保持原样：宁可多一行，也不丢信息、不猜内容。</li>
 * </ol>
 */
public final class TraceEventAggregator {

    private static final String ATTACHMENT_URL_PREFIX = "attachment.ref.";
    private static final long MERGE_WINDOW_SECONDS = 2L;

    private TraceEventAggregator() {
    }

    /**
     * @param events 按时间、日志 ID 正序的流水事件（同一秒内必须靠 ID 保证「附件在前、保存在后」）
     * @return 归并后的流水事件；输入为 null 时返回空列表
     */
    public static List<UnifiedTraceEventVO> mergeAttachmentEvents(List<UnifiedTraceEventVO> events) {
        if (events == null || events.isEmpty()) return List.of();
        List<UnifiedTraceEventVO> result = new ArrayList<>(events.size());
        List<UnifiedTraceEventVO> pending = new ArrayList<>();
        for (UnifiedTraceEventVO event : events) {
            if (isAttachmentEvent(event)) {
                pending.add(event);
                continue;
            }
            if (isDocumentSave(event) && !pending.isEmpty()
                    && sameBiz(pending.get(pending.size() - 1), event)) {
                int mergeFrom = pending.size();
                while (mergeFrom > 0 && withinWindow(pending.get(mergeFrom - 1), event)) mergeFrom--;
                result.addAll(pending.subList(0, mergeFrom));
                for (UnifiedTraceEventVO attachment : pending.subList(mergeFrom, pending.size())) {
                    if (!mergeInto(event, attachment)) result.add(attachment);
                }
                pending.clear();
                result.add(event);
                continue;
            }
            result.addAll(pending);
            pending.clear();
            result.add(event);
        }
        result.addAll(pending);
        return result;
    }

    private static boolean isAttachmentEvent(UnifiedTraceEventVO event) {
        return event.getOperUrl() != null && event.getOperUrl().startsWith(ATTACHMENT_URL_PREFIX);
    }

    private static boolean isDocumentSave(UnifiedTraceEventVO event) {
        Integer type = event.getBusinessType();
        return type != null && (type == 1 || type == 2) && !isAttachmentEvent(event);
    }

    private static boolean sameBiz(UnifiedTraceEventVO attachment, UnifiedTraceEventVO save) {
        return attachment.getBizType() != null && attachment.getBizType().equals(save.getBizType())
                && attachment.getBizId() != null && attachment.getBizId().equals(save.getBizId());
    }

    private static boolean withinWindow(UnifiedTraceEventVO attachment, UnifiedTraceEventVO save) {
        LocalDateTime attachmentTime = attachment.getTime();
        LocalDateTime saveTime = save.getTime();
        if (attachmentTime == null || saveTime == null) return false;
        return Math.abs(Duration.between(attachmentTime, saveTime).getSeconds()) <= MERGE_WINDOW_SECONDS;
    }

    /**
     * 把附件行的变更与附件清单并入保存行。
     *
     * @return 是否并入；detail 不可解析时返回 false，由调用方保留原行
     */
    private static boolean mergeInto(UnifiedTraceEventVO save, UnifiedTraceEventVO attachment) {
        JSONObject saveDetail = saveDetailOf(save.getDetail());
        JSONObject attachmentDetail = parseDetail(attachment.getDetail());
        if (saveDetail == null || attachmentDetail == null) return false;

        List<String> changes = new ArrayList<>(stringList(saveDetail.getJSONArray("changes")));
        changes.addAll(stringList(attachmentDetail.getJSONArray("changes")));
        List<Object> attachments = new ArrayList<>(objectList(saveDetail.getJSONArray("attachments")));
        for (Object item : objectList(attachmentDetail.getJSONArray("attachments"))) {
            if (attachments.stream().noneMatch(existing -> sameAttachment(existing, item))) {
                attachments.add(item);
            }
        }
        save.setDetail(OperLogDetailBuilder.build(changes, attachments));
        return true;
    }

    /** 保存行 detail 允许为空（例如新增单据没有变更明细），空串按空对象处理。 */
    private static JSONObject saveDetailOf(String detail) {
        return detail == null || detail.isBlank() ? new JSONObject() : parseDetail(detail);
    }

    private static JSONObject parseDetail(String detail) {
        if (detail == null || detail.isBlank()) return null;
        try {
            return JSONUtil.parseObj(detail);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> stringList(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) return values;
        for (Object item : array) {
            if (item != null) values.add(String.valueOf(item));
        }
        return values;
    }

    private static List<Object> objectList(JSONArray array) {
        List<Object> values = new ArrayList<>();
        if (array == null) return values;
        for (Object item : array) {
            if (item != null) values.add(item);
        }
        return values;
    }

    private static boolean sameAttachment(Object left, Object right) {
        String leftKey = attachmentKey(left);
        String rightKey = attachmentKey(right);
        return leftKey != null && leftKey.equals(rightKey);
    }

    private static String attachmentKey(Object item) {
        Object refId = readField(item, "refId");
        if (refId != null) return "ref:" + refId;
        Object id = readField(item, "id");
        return id == null ? null : "file:" + id;
    }

    private static Object readField(Object item, String key) {
        if (item instanceof JSONObject json) return json.get(key);
        if (item instanceof Map<?, ?> map) return map.get(key);
        return null;
    }
}
