package com.jjx.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.mapper.ReviewFlowMapper;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.sales.domain.entity.SalesQuotationFlow;
import com.jjx.sales.mapper.QuotationFlowMapper;
import com.jjx.common.core.page.PageResult;
import com.jjx.trace.domain.vo.TraceAttachmentVO;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;
import com.jjx.trace.domain.vo.TraceReviewHistoryVO;
import com.jjx.trace.service.TraceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceServiceImpl implements TraceService {

    private final SysOperLogMapper sysOperLogMapper;
    private final ReviewFlowMapper reviewFlowMapper;
    private final QuotationFlowMapper quotationFlowMapper;
    private final SysAttachmentMapper sysAttachmentMapper;
    private final ObjectMapper objectMapper;

    private static final long REVIEW_MERGE_SECONDS = 5L;

    @Override
    public List<Map<String, Object>> getTraceByTraceId(String traceId) {
        LambdaQueryWrapper<SysOperLog> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysOperLog::getTraceId, traceId);
        wrapper.orderByAsc(SysOperLog::getCreateTime);
        List<SysOperLog> logs = sysOperLogMapper.selectList(wrapper);
        if (logs.isEmpty()) return Collections.emptyList();

        Map<String, List<SysOperLog>> grouped = logs.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getModule() != null ? l.getModule() : "其他",
                        LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map.Entry<String, List<SysOperLog>> e : grouped.entrySet()) {
            List<SysOperLog> moduleLogs = e.getValue();
            SysOperLog first = moduleLogs.get(0);
            SysOperLog last = moduleLogs.get(moduleLogs.size() - 1);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("module", e.getKey());
            node.put("bizType", first.getBizType());
            node.put("bizId", first.getBizId());
            node.put("startTime", first.getCreateTime());
            node.put("endTime", last.getCreateTime());
            node.put("totalOps", moduleLogs.size());
            node.put("status", moduleLogs.stream().allMatch(l -> l.getStatus() != null && l.getStatus() == 1) ? "success" : "partial");

            List<Map<String, Object>> ops = moduleLogs.stream().map(l -> {
                Map<String, Object> op = new LinkedHashMap<>();
                op.put("id", l.getId());
                op.put("action", l.getOperUrl());
                op.put("operator", l.getRealName() != null ? l.getRealName() : l.getUsername());
                op.put("time", l.getCreateTime());
                op.put("status", l.getStatus());
                op.put("bizId", l.getBizId());
                op.put("bizType", l.getBizType());
                op.put("bizStatus", l.getBizStatus());
                op.put("businessType", l.getBusinessType());
                // 2026-08-18：透出 detail（变更JSON）/operParam（摘要），前端展示修改内容
                op.put("detail", l.getDetail());
                op.put("operParam", l.getOperParam());
                return op;
            }).collect(Collectors.toList());
            node.put("operations", ops);
            nodes.add(node);
        }
        return nodes;
    }

    @Override
    public List<Map<String, Object>> searchTrace(String keyword) {
        LambdaQueryWrapper<SysOperLog> wrapper = Wrappers.lambdaQuery();
        wrapper.like(SysOperLog::getBizId, keyword);
        wrapper.select(SysOperLog::getTraceId);
        wrapper.isNotNull(SysOperLog::getTraceId);
        wrapper.last("LIMIT 20");
        List<SysOperLog> logs = sysOperLogMapper.selectList(wrapper);
        Set<String> traceIds = logs.stream()
                .map(SysOperLog::getTraceId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Map<String, Object>> results = new ArrayList<>();
        for (String tid : traceIds) {
            List<Map<String, Object>> trace = getTraceByTraceId(tid);
            if (!trace.isEmpty()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("traceId", tid);
                item.put("nodes", trace);
                results.add(item);
            }
        }
        return results;
    }

    @Override
    public PageResult<UnifiedTraceEventVO> getEvents(String bizType, Long bizId, int pageNum, int pageSize) {
        if (bizType == null || bizType.isBlank() || bizId == null) {
            return page(Collections.emptyList(), pageNum, pageSize);
        }
        List<SysOperLog> logs = sysOperLogMapper.selectList(Wrappers.<SysOperLog>lambdaQuery()
                .eq(SysOperLog::getBizType, bizType)
                .eq(SysOperLog::getBizId, String.valueOf(bizId))
                .orderByAsc(SysOperLog::getCreateTime));

        List<ReviewFlow> reviews = Collections.emptyList();
        List<SalesQuotationFlow> quotationFlows = Collections.emptyList();
        String reviewBizType = reviewBizType(bizType);
        if ("quotation".equals(bizType)) {
            quotationFlows = quotationFlowMapper.selectByQuotationId(bizId);
        } else if (reviewBizType != null) {
            reviews = reviewFlowMapper.selectList(Wrappers.<ReviewFlow>lambdaQuery()
                    .eq(ReviewFlow::getBizType, reviewBizType)
                    .eq(ReviewFlow::getBizId, bizId)
                    .orderByAsc(ReviewFlow::getCreateTime));
        }
        List<SysAttachment> attachments = sysAttachmentMapper.selectList(Wrappers.<SysAttachment>lambdaQuery()
                .eq(SysAttachment::getBizType, bizType)
                .eq(SysAttachment::getBizId, bizId)
                .orderByAsc(SysAttachment::getCreateTime));
        return page(aggregateEvents(bizType, logs, reviews, quotationFlows, attachments), pageNum, pageSize);
    }

    List<UnifiedTraceEventVO> aggregateEvents(String bizType, List<SysOperLog> logs,
                                       List<ReviewFlow> reviews,
                                       List<SalesQuotationFlow> quotationFlows,
                                       List<SysAttachment> attachments) {
        List<ReviewRecord> reviewRecords = normalizeReviews(bizType, reviews, quotationFlows);
        Set<String> matchedReviews = new HashSet<>();
        Set<Long> referencedAttachmentIds = new HashSet<>();
        List<UnifiedTraceEventVO> events = new ArrayList<>();

        for (SysOperLog log : safe(logs)) {
            UnifiedTraceEventVO event = fromLog(log);
            addDetail(event, log.getDetail(), referencedAttachmentIds);
            ReviewRecord matched = findReview(log, reviewRecords, matchedReviews);
            if (matched != null) {
                matchedReviews.add(matched.id());
                attachReviewRound(event, matched, reviewRecords, referencedAttachmentIds, attachments);
            }
            events.add(event);
        }

        for (ReviewRecord review : reviewRecords) {
            if (matchedReviews.contains(review.id())) continue;
            UnifiedTraceEventVO event = fromReview(review, bizType);
            attachReviewRound(event, review, reviewRecords, referencedAttachmentIds, attachments);
            events.add(event);
        }

        for (SysAttachment attachment : safe(attachments)) {
            if (attachment.getId() == null || referencedAttachmentIds.contains(attachment.getId())) continue;
            UnifiedTraceEventVO event = new UnifiedTraceEventVO();
            event.setEventId("attachment-" + attachment.getId());
            event.setTime(attachment.getCreateTime());
            event.setActionTitle("上传附件");
            event.setOperatorName(attachment.getCreateBy());
            event.setResult(1);
            event.setBizType(bizType);
            event.setModule("附件");
            event.getAttachments().add(new TraceAttachmentVO(attachment.getId(), attachment.getFileName()));
            events.add(event);
        }
        events.sort(Comparator.comparing(UnifiedTraceEventVO::getTime,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(UnifiedTraceEventVO::getEventId));
        return events;
    }

    private UnifiedTraceEventVO fromLog(SysOperLog log) {
        UnifiedTraceEventVO event = new UnifiedTraceEventVO();
        event.setEventId("oper-" + log.getId());
        event.setTime(log.getCreateTime());
        event.setBizStatus(log.getBizStatus());
        event.setActionTitle(actionTitle(log.getOperUrl(), log.getOperParam(), log.getBusinessType()));
        event.setOperatorName(log.getRealName() != null && !log.getRealName().isBlank()
                ? log.getRealName() : log.getUsername());
        event.setResult(log.getStatus());
        event.setTraceId(log.getTraceId());
        event.setModule(log.getModule());
        event.setBizType(log.getBizType());
        event.setBusinessType(log.getBusinessType());
        return event;
    }

    private UnifiedTraceEventVO fromReview(ReviewRecord review, String bizType) {
        UnifiedTraceEventVO event = new UnifiedTraceEventVO();
        event.setEventId("review-" + review.id());
        event.setTime(review.createTime());
        event.setBizStatus(parseStatus(review.toStatus()));
        event.setActionTitle(review.actionName() == null || review.actionName().isBlank()
                ? reviewActionTitle(review.actionCode()) : review.actionName());
        event.setOperatorName(review.operatorName());
        event.setResult(1);
        event.setRoundNo(review.roundNo());
        event.setBizType(bizType);
        event.setModule("审核");
        event.setActionCode(review.actionCode());
        event.setComment(review.comment());
        return event;
    }

    private void attachReviewRound(UnifiedTraceEventVO event, ReviewRecord matched, List<ReviewRecord> all,
                                   Set<Long> referencedAttachmentIds, List<SysAttachment> attachments) {
        // 只挂匹配的那条审核记录（该操作自身）：轮次/动作/意见/附件；
        // 不展开同轮完整流程（时间线本身已按动作逐行展示）
        event.setRoundNo(matched.roundNo());
        event.setActionCode(matched.actionCode());
        event.setActionTitle(reviewActionTitle(matched.actionCode()));
        event.setBizStatus(parseStatus(matched.toStatus()));
        event.setComment(matched.comment());
        for (Long id : parseAttachmentIds(matched.attachmentIds())) {
            addAttachment(event, id, attachments, referencedAttachmentIds);
        }
    }

    private ReviewRecord findReview(SysOperLog log, List<ReviewRecord> reviews, Set<String> matched) {
        String logSemantic = semantic(log.getOperUrl() + " " + log.getOperParam());
        if (logSemantic == null || log.getCreateTime() == null) return null;
        return reviews.stream()
                .filter(r -> !matched.contains(r.id()))
                .filter(r -> Objects.equals(logSemantic, semantic(r.actionCode())))
                .filter(r -> r.createTime() != null
                        && Math.abs(java.time.Duration.between(log.getCreateTime(), r.createTime()).getSeconds())
                        <= REVIEW_MERGE_SECONDS)
                .min(Comparator.comparingLong(r -> Math.abs(
                        java.time.Duration.between(log.getCreateTime(), r.createTime()).toMillis())))
                .orElse(null);
    }

    private List<ReviewRecord> normalizeReviews(String bizType, List<ReviewFlow> reviews,
                                                 List<SalesQuotationFlow> quotationFlows) {
        List<ReviewRecord> result = new ArrayList<>();
        for (ReviewFlow flow : safe(reviews)) {
            result.add(new ReviewRecord("rf-" + flow.getFlowId(), flow.getRoundNo() == null ? 1 : flow.getRoundNo(),
                    flow.getActionCode(), flow.getActionName(), flow.getFromStatus(), flow.getToStatus(),
                    flow.getOperatorName(), flow.getComment(), flow.getAttachmentIds(), flow.getCreateTime()));
        }
        List<SalesQuotationFlow> sorted = new ArrayList<>(safe(quotationFlows));
        sorted.sort(Comparator.comparing(SalesQuotationFlow::getCreateTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        int round = 1;
        boolean previousRejected = false;
        for (SalesQuotationFlow flow : sorted) {
            if (previousRejected && semantic(flow.getActionCode()) != null
                    && "SUBMIT".equals(semantic(flow.getActionCode()))) round++;
            result.add(new ReviewRecord("qf-" + flow.getFlowId(), round, flow.getActionCode(), flow.getActionName(),
                    asString(flow.getFromStatus()), asString(flow.getToStatus()), flow.getOperatorName(),
                    flow.getRemark(), flow.getAttachmentIds(), flow.getCreateTime()));
            previousRejected = "REJECT".equals(semantic(flow.getActionCode()));
        }
        result.sort(Comparator.comparing(ReviewRecord::createTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private void addDetail(UnifiedTraceEventVO event, String detail, Set<Long> referencedAttachmentIds) {
        if (detail == null || detail.isBlank()) return;
        try {
            JsonNode root = objectMapper.readTree(detail);
            JsonNode changes = root.path("changes");
            if (changes.isArray()) changes.forEach(n -> event.getChanges().add(n.asText()));
            JsonNode detailAttachments = root.path("attachments");
            if (detailAttachments.isArray()) {
                detailAttachments.forEach(n -> {
                    Long id = n.hasNonNull("id") ? n.get("id").asLong() : null;
                    if (id != null) {
                        event.getAttachments().add(new TraceAttachmentVO(id,
                                n.hasNonNull("fileName") ? n.get("fileName").asText() : "附件" + id));
                        referencedAttachmentIds.add(id);
                    }
                });
            }
        } catch (Exception ignored) {
            // 兼容历史非 JSON detail。
        }
    }

    private void addAttachment(UnifiedTraceEventVO event, Long id, List<SysAttachment> attachments,
                               Set<Long> referencedAttachmentIds) {
        if (id == null || event.getAttachments().stream().anyMatch(a -> id.equals(a.getId()))) return;
        SysAttachment found = safe(attachments).stream().filter(a -> id.equals(a.getId())).findFirst().orElse(null);
        event.getAttachments().add(new TraceAttachmentVO(id, found == null ? "附件" + id : found.getFileName()));
        referencedAttachmentIds.add(id);
    }

    private List<Long> parseAttachmentIds(String value) {
        if (value == null || value.isBlank()) return Collections.emptyList();
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node.isArray()) {
                List<Long> ids = new ArrayList<>();
                node.forEach(n -> ids.add(n.asLong()));
                return ids;
            }
        } catch (Exception ignored) {
        }
        List<Long> ids = new ArrayList<>();
        for (String part : value.split(",")) {
            try { ids.add(Long.valueOf(part.trim())); } catch (NumberFormatException ignored) { }
        }
        return ids;
    }

    private String reviewBizType(String bizType) {
        return switch (bizType) {
            case "order", "sales_order" -> "sales_order";
            case "purchase", "purchase_order" -> "purchase_order";
            case "bom" -> "engineering_bom";
            case "film" -> "engineering_film";
            default -> null;
        };
    }

    private String semantic(String value) {
        if (value == null) return null;
        String code = value.toUpperCase(Locale.ROOT);
        if (code.contains("REJECT") || code.contains("驳回") || code.contains("拒绝")) return "REJECT";
        if (code.contains("APPROVE") || code.contains("审核通过") || code.contains("审批通过")) return "APPROVE";
        if (code.contains("SUBMIT") || code.contains("提交审核")) return "SUBMIT";
        if (code.contains("CUSTOMER_CONFIRM") || code.contains("CONFIRM") || code.contains("客户确认")) return "CONFIRM";
        if (code.contains("SEND") || code.contains("发送报价")) return "SEND";
        if (code.contains("CANCEL") || code.contains("取消")) return "CANCEL";
        return null;
    }

    private String reviewActionTitle(String actionCode) {
        if (actionCode != null) {
            String exactCode = actionCode.toUpperCase(Locale.ROOT);
            if (exactCode.contains("CUSTOMER_REJECT")) return "客户拒绝报价";
            if (exactCode.contains("CUSTOMER_CONFIRM")) return "客户确认报价";
        }
        String semantic = semantic(actionCode);
        if (semantic == null) return actionCode == null ? "审核操作" : actionCode;
        return switch (semantic) {
            case "SUBMIT" -> "提交审核";
            case "APPROVE" -> "审核通过";
            case "REJECT" -> "审核驳回";
            case "SEND" -> "发送报价";
            case "CONFIRM" -> "客户确认报价";
            case "CANCEL" -> "取消";
            default -> "审核操作";
        };
    }

    private String actionTitle(String operUrl, String operParam, Integer businessType) {
        String semantic = semantic((operUrl == null ? "" : operUrl) + " " + (operParam == null ? "" : operParam));
        if (semantic != null) return reviewActionTitle(semantic);
        if (operParam != null && !operParam.isBlank() && operParam.length() <= 60) return operParam;
        return switch (businessType == null ? 9 : businessType) {
            case 1 -> "创建";
            case 2 -> "修改";
            case 3 -> "删除";
            case 4 -> "导出";
            case 5 -> "导入";
            case 6 -> "审批";
            case 11 -> "转换";
            default -> "业务操作";
        };
    }

    private Integer parseStatus(String value) {
        try { return value == null ? null : Integer.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String asString(Object value) { return value == null ? null : String.valueOf(value); }

    private <T> List<T> safe(List<T> list) { return list == null ? Collections.emptyList() : list; }

    PageResult<UnifiedTraceEventVO> page(List<UnifiedTraceEventVO> events, int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int from = Math.min((safePage - 1) * safeSize, events.size());
        int to = Math.min(from + safeSize, events.size());
        PageResult<UnifiedTraceEventVO> result = new PageResult<>();
        result.setTotal(events.size());
        result.setRecords(new ArrayList<>(events.subList(from, to)));
        result.setPageNum(safePage);
        result.setPageSize(safeSize);
        result.setTotalPages((events.size() + safeSize - 1) / safeSize);
        return result;
    }

    private record ReviewRecord(String id, Integer roundNo, String actionCode, String actionName,
                                String fromStatus, String toStatus, String operatorName, String comment,
                                String attachmentIds, java.time.LocalDateTime createTime) { }
}
