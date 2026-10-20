package com.jjx.trace.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jjx.system.utils.OperLogDetailBuilder;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「查看流水」的读时归并：把一次业务动作顺带产生的证据附件并回该动作。
 *
 * <p>背景：一次业务动作如果同时落定附件，会写两条 sys_oper_log —— 附件引用在单据事务提交后写
 * 「附件新增」，业务动作本身在接口返回后写「客户确认报价」「审核报价单」「发送报价单」等，
 * 于是流水里一次操作看起来像两步。这里只在查询结果上做投影：日志表仍是一条日志一行（唯一真源），
 * 不改表结构、不改写入路径，sales_quotation_flow 也不变。</p>
 *
 * <p>归并规则（dev-20261020-002）：</p>
 * <ol>
 *   <li>证据优先按 refId 关联：附件行 detail.attachments[].refId 出现在后续动作行
 *       operParam.attachmentRefIds 中，且同 trace、同 biz_type + biz_id、附件行在动作行之前。</li>
 *   <li>没有 refId 可匹配时，允许同 trace、同 biz_type + biz_id、同操作人、附件行紧邻动作行，
 *       且间隔不超过 60 秒；跨动作或超过 60 秒不并。</li>
 *   <li>动作行不再限定 business_type 为 INSERT/UPDATE：客户确认/拒绝、审核通过/驳回、发送等
 *       业务动作都可以接收其证据附件。</li>
 *   <li>解析失败、refId 显式不匹配、找不到动作行时保留原附件行：宁可多一行，不丢信息、不猜内容。</li>
 *   <li>独立的附件解绑/删除、以及无后续动作的单独上传不并入任何动作。解绑/删除只在同一事务的
 *       2 秒窗口内归并，避免把几十秒前的独立解绑算到下一次保存头上。</li>
 * </ol>
 */
public final class TraceEventAggregator {

    private static final String ATTACHMENT_URL_PREFIX = "attachment.ref.";
    /** 无 refId 可匹配时，附件证据与动作行的最大间隔。 */
    private static final long ADJACENT_WINDOW_SECONDS = 60L;
    /** 附件解绑/删除只在同一事务的毫秒级窗口内归并。 */
    private static final long LEGACY_TRANSACTION_WINDOW_SECONDS = 2L;

    private TraceEventAggregator() {
    }

    /**
     * @param events 按时间、日志 ID 正序的流水事件（同一秒内必须靠 ID 保证「附件在前、动作在后」）
     * @return 归并后的流水事件；输入为 null 时返回空列表
     */
    public static List<UnifiedTraceEventVO> mergeAttachmentEvents(List<UnifiedTraceEventVO> events) {
        if (events == null || events.isEmpty()) return List.of();
        int size = events.size();
        Map<Integer, List<Integer>> evidenceByAction = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UnifiedTraceEventVO attachment = events.get(i);
            if (!isAttachmentEvent(attachment)) continue;
            int actionIndex = findActionByRefId(events, i);
            if (actionIndex < 0) actionIndex = findAdjacentAction(events, i);
            if (actionIndex < 0) continue;
            evidenceByAction.computeIfAbsent(actionIndex, key -> new ArrayList<>()).add(i);
        }

        boolean[] consumed = new boolean[size];
        for (Map.Entry<Integer, List<Integer>> entry : evidenceByAction.entrySet()) {
            UnifiedTraceEventVO action = events.get(entry.getKey());
            for (Integer attachmentIndex : entry.getValue()) {
                if (mergeInto(action, events.get(attachmentIndex))) consumed[attachmentIndex] = true;
            }
        }

        List<UnifiedTraceEventVO> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (!consumed[i]) result.add(events.get(i));
        }
        return result;
    }

    private static boolean isAttachmentEvent(UnifiedTraceEventVO event) {
        return event.getOperUrl() != null && event.getOperUrl().startsWith(ATTACHMENT_URL_PREFIX);
    }

    /** 附件解绑/删除：只允许在同一事务的短窗口内并入保存行。 */
    private static boolean isRemovalAttachment(UnifiedTraceEventVO event) {
        String action = event.getAction();
        String url = event.getOperUrl();
        return containsAny(action, "解绑", "删除", "移除")
                || containsAny(url, "解绑", "删除", "移除");
    }

    private static boolean containsAny(String value, String... keywords) {
        if (value == null || value.isBlank()) return false;
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }

    /** 规则 A：按 refId 找后续动作行；找不到返回 -1。 */
    private static int findActionByRefId(List<UnifiedTraceEventVO> events, int attachmentIndex) {
        UnifiedTraceEventVO attachment = events.get(attachmentIndex);
        if (isRemovalAttachment(attachment)) return -1;
        Set<String> refIds = attachmentRefIds(attachment);
        if (refIds.isEmpty()) return -1;
        for (int j = attachmentIndex + 1; j < events.size(); j++) {
            UnifiedTraceEventVO candidate = events.get(j);
            if (isAttachmentEvent(candidate)) continue;
            if (!sameTrace(attachment, candidate) || !sameBiz(attachment, candidate)) continue;
            Set<String> referenced = referencedRefIds(candidate);
            if (!referenced.isEmpty() && !Collections.disjoint(refIds, referenced)) return j;
        }
        return -1;
    }

    /** 规则 B：无 refId 可匹配时，按紧邻 + 同操作人 + 60 秒窗口找动作行。 */
    private static int findAdjacentAction(List<UnifiedTraceEventVO> events, int attachmentIndex) {
        UnifiedTraceEventVO attachment = events.get(attachmentIndex);
        int j = attachmentIndex + 1;
        while (j < events.size() && isAttachmentEvent(events.get(j))) j++;
        if (j >= events.size()) return -1;
        UnifiedTraceEventVO action = events.get(j);
        if (!sameTrace(attachment, action) || !sameBiz(attachment, action)
                || !sameOperator(attachment, action)) {
            return -1;
        }
        long window = isRemovalAttachment(attachment)
                ? LEGACY_TRANSACTION_WINDOW_SECONDS : ADJACENT_WINDOW_SECONDS;
        if (!withinWindow(attachment, action, window)) return -1;
        // 附件带 refId、动作行也明确声明了 refIds 却对不上：这是显式不匹配，保留两行；
        // 动作行没有声明 refIds 时才允许按紧邻规则归并。
        Set<String> refIds = attachmentRefIds(attachment);
        Set<String> referenced = referencedRefIds(action);
        if (!refIds.isEmpty() && !referenced.isEmpty() && Collections.disjoint(refIds, referenced)) {
            return -1;
        }
        return j;
    }

    private static boolean sameTrace(UnifiedTraceEventVO attachment, UnifiedTraceEventVO action) {
        String left = attachment.getTraceId();
        String right = action.getTraceId();
        return left == null || right == null || left.equals(right);
    }

    private static boolean sameBiz(UnifiedTraceEventVO attachment, UnifiedTraceEventVO action) {
        return attachment.getBizType() != null && attachment.getBizType().equals(action.getBizType())
                && attachment.getBizId() != null && attachment.getBizId().equals(action.getBizId());
    }

    private static boolean sameOperator(UnifiedTraceEventVO attachment, UnifiedTraceEventVO action) {
        String left = blankToNull(attachment.getOperatorName());
        String right = blankToNull(action.getOperatorName());
        return left == null ? right == null : left.equals(right);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean withinWindow(UnifiedTraceEventVO attachment, UnifiedTraceEventVO action,
                                        long seconds) {
        LocalDateTime attachmentTime = attachment.getTime();
        LocalDateTime actionTime = action.getTime();
        if (attachmentTime == null || actionTime == null) return false;
        return Math.abs(Duration.between(attachmentTime, actionTime).getSeconds()) <= seconds;
    }

    /** 附件行 detail.attachments[].refId；解析失败或没有附件明细时返回空集合。 */
    private static Set<String> attachmentRefIds(UnifiedTraceEventVO attachment) {
        JSONObject detail = parseDetail(attachment.getDetail());
        if (detail == null) return Set.of();
        return refIdsOf(detail.getJSONArray("attachments"));
    }

    /** 动作行 operParam.attachmentRefIds；解析失败或没有该字段时返回空集合。 */
    private static Set<String> referencedRefIds(UnifiedTraceEventVO action) {
        JSONObject param = parseDetail(action.getOperParam());
        if (param == null) return Set.of();
        return refIdsOf(param.getJSONArray("attachmentRefIds"));
    }

    private static Set<String> refIdsOf(JSONArray array) {
        if (array == null) return Set.of();
        Set<String> ids = new LinkedHashSet<>();
        for (Object item : array) {
            Object refId = readField(item, "refId");
            // detail.attachments 是对象数组，operParam.attachmentRefIds 是标量数组
            if (refId == null && !(item instanceof Map<?, ?>) && !(item instanceof JSONObject)) {
                refId = item;
            }
            if (refId != null) ids.add(String.valueOf(refId));
        }
        return ids;
    }

    /**
     * 把附件行的变更与附件清单并入动作行。
     *
     * @return 是否并入；detail 不可解析时返回 false，由调用方保留原行
     */
    private static boolean mergeInto(UnifiedTraceEventVO action, UnifiedTraceEventVO attachment) {
        JSONObject actionDetail = actionDetailOf(action.getDetail());
        JSONObject attachmentDetail = parseDetail(attachment.getDetail());
        if (actionDetail == null || attachmentDetail == null) return false;

        List<String> changes = new ArrayList<>(stringList(actionDetail.getJSONArray("changes")));
        changes.addAll(stringList(attachmentDetail.getJSONArray("changes")));
        List<Object> attachments = new ArrayList<>(objectList(actionDetail.getJSONArray("attachments")));
        for (Object item : objectList(attachmentDetail.getJSONArray("attachments"))) {
            if (attachments.stream().noneMatch(existing -> sameAttachment(existing, item))) {
                attachments.add(item);
            }
        }
        action.setDetail(OperLogDetailBuilder.build(changes, attachments));
        return true;
    }

    /** 动作行 detail 允许为空（例如新增单据没有变更明细），空串按空对象处理。 */
    private static JSONObject actionDetailOf(String detail) {
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
