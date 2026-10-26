package com.jjx.trace.service.impl;

import com.jjx.trace.domain.vo.UnifiedTraceEventVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按明确的操作组投影主事件与子项，不改日志；无 groupId 的历史行逐条保留。
 * 取代原附件邻近归并，避免同一行被两套规则重复消费。
 */
public final class TraceEventAggregator {

    private TraceEventAggregator() {
    }

    /**
     * @param events 按时间、日志 ID 正序的流水事件（同一秒内必须靠 ID 保证「附件在前、动作在后」）
     * @return 归并后的流水事件；输入为 null 时返回空列表
     */
    public static List<UnifiedTraceEventVO> mergeAttachmentEvents(List<UnifiedTraceEventVO> events) {
        if (events == null || events.isEmpty()) return List.of();
        // groupId 是一次操作的明确边界；不再对这些行叠加附件邻近归并。
        Map<String, List<UnifiedTraceEventVO>> groups = new java.util.LinkedHashMap<>();
        for (var event : events) {
            if (event.getGroupId() != null && !event.getGroupId().isBlank()) {
                String key = event.getTraceId() + ":" + event.getGroupId();
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(event);
            }
        }
        List<UnifiedTraceEventVO> result = new ArrayList<>();
        Set<String> emitted = new java.util.HashSet<>();
        for (var event : events) {
            if (event.getGroupId() == null || event.getGroupId().isBlank()) {
                result.add(event); // 无 groupId 的历史行保持逐条展示。
                continue;
            }
            String key = event.getTraceId() + ":" + event.getGroupId();
            if (!emitted.add(key)) continue;
            var members = groups.get(key);
            var primary = members.stream().min(java.util.Comparator
                    .comparingInt(TraceEventAggregator::primaryPriority)
                    // 同组审核失败后重试成功，成功动作作主项，失败记录仍保留为子项。
                    .thenComparingInt(item -> java.util.Objects.equals(item.getResult(),
                            com.jjx.common.enums.YesNoEnum.YES.getCode()) ? 0 : 1)
                    .thenComparing(UnifiedTraceEventVO::getTime,
                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))).orElseThrow();
            primary.setChildren(members.stream().filter(item -> item != primary).toList());
            result.add(primary);
        }
        return result;
    }

    private static int primaryPriority(UnifiedTraceEventVO event) {
        if ("报价转订单".equals(event.getAction())) return 0;
        if (isAttachmentEvent(event) || event.getAction() == null || event.getAction().isBlank()
                || event.getOperUrl() != null && event.getOperUrl().endsWith("order.create")) return 2;
        return 1;
    }

    private static boolean isAttachmentEvent(UnifiedTraceEventVO event) {
        return event.getOperUrl() != null && event.getOperUrl().startsWith("attachment.ref.");
    }
}
