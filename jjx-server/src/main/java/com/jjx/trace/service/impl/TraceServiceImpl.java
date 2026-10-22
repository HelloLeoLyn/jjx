package com.jjx.trace.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesQuotationFlow;
import com.jjx.sales.mapper.QuotationFlowMapper;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.ReviewFlowMapper;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.trace.domain.vo.TraceReviewVO;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;
import com.jjx.trace.service.TraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceServiceImpl implements TraceService {

    private final SysOperLogMapper sysOperLogMapper;
    private final ReviewFlowMapper reviewFlowMapper;
    private final QuotationFlowMapper quotationFlowMapper;
    private final com.jjx.system.service.AttachmentEvidenceService attachmentEvidence;
    /** 历史流水补文件名专用；单测构造时不注入，读时降级为「附件 ID」。 */
    @Autowired(required = false)
    private SysAttachmentMapper attachmentMapper;

    @Override
    public PageResult<UnifiedTraceEventVO> getEvents(String traceId, String scope, int pageNum, int pageSize) {
        if (traceId == null || traceId.isBlank()) {
            return PageResult.build(Collections.emptyList(), 0);
        }
        // 整条 trace 分组后再分页，保证主事件与子项不会被页码切开。
        LambdaQueryWrapper<SysOperLog> query = Wrappers.<SysOperLog>lambdaQuery()
                .eq(SysOperLog::getTraceId, traceId)
                .orderByAsc(SysOperLog::getCreateTime)
                .orderByAsc(SysOperLog::getId);
        // trace 查询按用户口径全显示，scope 不截断跨来源动作。
        List<SysOperLog> logs = sysOperLogMapper.selectList(query);
        List<UnifiedTraceEventVO> events = enrichWorkSpecHistory(TraceEventAggregator.mergeAttachmentEvents(
                logs.stream().map(this::fromLog).toList()));
        int size = Math.min(Math.max(pageSize, 1), 100);
        int num = Math.max(pageNum, 1);
        int from = (int) Math.min((long) (num - 1) * size, events.size());
        int to = Math.min(from + size, events.size());
        PageResult<UnifiedTraceEventVO> result = new PageResult<>();
        result.setRecords(new ArrayList<>(events.subList(from, to)));
        result.setTotal(events.size());
        result.setPageNum(num);
        result.setPageSize(size);
        result.setTotalPages((events.size() + size - 1) / size);
        return result;
    }

    /**
     * 按业务对象查询操作日志（产品/BOM/工艺路线等主数据无 trace_id，按 bizType+bizId 聚合）
     */
    @Override
    public PageResult<UnifiedTraceEventVO> getEventsByBiz(String bizType, Long bizId, String scope, int pageNum, int pageSize) {
        if (bizType == null || bizType.isBlank() || bizId == null) {
            return PageResult.build(Collections.emptyList(), 0);
        }
        LambdaQueryWrapper<SysOperLog> query = Wrappers.<SysOperLog>lambdaQuery();
        if ("product".equals(bizType)) {
            // 一次只读查询覆盖全部版本（含历史 trace 上的工程日志），先排序后在数据库分页。
            // biz_id 是字符串，子查询显式转字符串，避免 MySQL 数值比较误匹配非数字业务 ID。
            query.and(w -> w.eq(SysOperLog::getBizType, "product").eq(SysOperLog::getBizId, bizId.toString())
                    .or(bom -> bom.eq(SysOperLog::getBizType, "bom")
                            .apply("biz_id IN (SELECT CONCAT('', bom_id) FROM engineering_bom WHERE product_id = {0})", bizId))
                    .or(routing -> routing.eq(SysOperLog::getBizType, "routing")
                            .apply("biz_id IN (SELECT CONCAT('', routing_id) FROM engineering_routing WHERE product_id = {0})", bizId)));
        } else {
            query.eq(SysOperLog::getBizType, bizType).eq(SysOperLog::getBizId, bizId.toString());
        }
        applyScope(query, scope);
        query.orderByAsc(SysOperLog::getCreateTime).orderByAsc(SysOperLog::getId);
        var records = enrichWorkSpecHistory(TraceEventAggregator.mergeAttachmentEvents(
                sysOperLogMapper.selectList(query).stream().map(this::fromLog).toList()));
        int size = Math.min(Math.max(pageSize, 1), 100);
        int num = Math.max(pageNum, 1);
        int from = (int) Math.min((long) (num - 1) * size, records.size());
        var result = new PageResult<UnifiedTraceEventVO>();
        result.setRecords(new ArrayList<>(records.subList(from, Math.min(from + size, records.size()))));
        result.setTotal(records.size());
        result.setPageNum(num);
        result.setPageSize(size);
        result.setTotalPages((records.size() + size - 1) / size);
        return result;
    }

    @Override
    public List<TraceReviewVO> reviewList(String bizType, Long bizId) {
        if (bizType == null || bizType.isBlank() || bizId == null) {
            return Collections.emptyList();
        }
        // 报价审核流水独立存储（sales_quotation_flow，020 决策报价不接入 review_flow）
        if ("quotation".equals(bizType)) {
            return quotationReviews(bizId);
        }
        String reviewBizType = reviewBizType(bizType);
        if (reviewBizType == null) {
            return Collections.emptyList();
        }
        List<ReviewFlow> flows = reviewFlowMapper.selectList(Wrappers.<ReviewFlow>lambdaQuery()
                .eq(ReviewFlow::getBizType, reviewBizType)
                .eq(ReviewFlow::getBizId, bizId)
                .orderByAsc(ReviewFlow::getCreateTime));
        return flows.stream().map(this::fromReviewFlow).toList();
    }

    /** 报价审核流水：轮次按动作序列归一化（驳回后再次提交轮次+1） */
    private List<TraceReviewVO> quotationReviews(Long quotationId) {
        List<SalesQuotationFlow> sorted = new ArrayList<>(
                safe(quotationFlowMapper.selectByQuotationId(quotationId)));
        sorted.sort(Comparator.comparing(SalesQuotationFlow::getCreateTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<TraceReviewVO> result = new ArrayList<>();
        int round = 1;
        boolean previousRejected = false;
        for (SalesQuotationFlow flow : sorted) {
            if (previousRejected && "SUBMIT".equals(semantic(flow.getActionCode()))) round++;
            TraceReviewVO vo = new TraceReviewVO();
            vo.setFlowId("qf-" + flow.getFlowId());
            vo.setRoundNo(round);
            vo.setActionCode(flow.getActionCode());
            vo.setActionName(flow.getActionName());
            vo.setFromStatus(asString(flow.getFromStatus()));
            vo.setToStatus(asString(flow.getToStatus()));
            vo.setOperatorName(flow.getOperatorName());
            vo.setComment(flow.getRemark());
            vo.setAttachmentRefs(attachmentEvidence.listSnapshot(flow.getAttachmentRefIds(), "quotation", quotationId));
            vo.setCreateTime(flow.getCreateTime());
            result.add(vo);
            previousRejected = "REJECT".equals(semantic(flow.getActionCode()));
        }
        return result;
    }

    private TraceReviewVO fromReviewFlow(ReviewFlow flow) {
        TraceReviewVO vo = new TraceReviewVO();
        vo.setFlowId("rf-" + flow.getFlowId());
        vo.setRoundNo(flow.getRoundNo() == null ? 1 : flow.getRoundNo());
        vo.setActionCode(flow.getActionCode());
        vo.setActionName(flow.getActionName());
        vo.setFromStatus(flow.getFromStatus());
        vo.setToStatus(flow.getToStatus());
        vo.setOperatorName(flow.getOperatorName());
        vo.setComment(flow.getComment());
        vo.setAttachmentRefs(attachmentEvidence.listSnapshot(flow.getAttachmentRefIds(), flow.getBizType(), flow.getBizId()));
        vo.setCreateTime(flow.getCreateTime());
        return vo;
    }

    private UnifiedTraceEventVO fromLog(SysOperLog log) {
        UnifiedTraceEventVO event = new UnifiedTraceEventVO();
        event.setEventId("oper-" + log.getId());
        event.setTime(log.getCreateTime());
        event.setBizStatus(log.getBizStatus());
        event.setAction(actionOf(log));
        event.setOperatorName(log.getRealName() != null && !log.getRealName().isBlank()
                ? log.getRealName() : log.getUsername());
        event.setResult(log.getStatus());
        event.setTraceId(log.getTraceId());
        event.setModule(moduleOf(log.getModule()));
        event.setBizType(log.getBizType());
        event.setBizId(log.getBizId());
        event.setGroupId(log.getOperGroupId());
        event.setBusinessType(log.getBusinessType());
        event.setOperUrl(log.getOperUrl());
        event.setOperParam(log.getOperParam());
        event.setDetail(log.getDetail());
        return event;
    }


    /** 统一范围只加模块条件：同一查询入口、同一 VO、同一分页规则。 */
    private static final List<String> WORK_SPEC_MODULES = List.of("产品作业规范", "作业规范版本");
    private static final String WORK_SPEC_MODULE = "产品作业规范";

    private void applyScope(LambdaQueryWrapper<SysOperLog> query, String scope) {
        if (com.jjx.trace.service.TraceService.SCOPE_WORK_SPEC.equals(scope)) {
            query.in(SysOperLog::getModule, WORK_SPEC_MODULES);
        }
    }

    /** 历史模块名只做读时映射，不改日志行。 */
    private String moduleOf(String module) {
        return "作业规范版本".equals(module) ? WORK_SPEC_MODULE : module;
    }

    /** 写入端未给 action 的历史行：能按固定入口判定就补明确动作，否则标明未命名。 */
    private String actionOf(SysOperLog log) {
        String action = log.getAction();
        if (action != null && !action.isBlank()) return action;
        String module = moduleOf(log.getModule());
        if (WORK_SPEC_MODULE.equals(module)) {
            String url = log.getOperUrl();
            if (url != null && url.contains("/work-spec/publish")) return "发布规范版本";
            return "未命名操作";
        }
        return null;
    }

    /**
     * 历史作业规范流水只读补全：detail.attachments 缺文件名时按附件 ID 回填；
     * 早期「保存草稿」只有 added/removed ID、没有 changes 时补成人话。不改库。
     */
    private List<UnifiedTraceEventVO> enrichWorkSpecHistory(List<UnifiedTraceEventVO> events) {
        if (events == null || events.isEmpty()) return events;
        List<UnifiedTraceEventVO> targets = events.stream()
                .filter(event -> WORK_SPEC_MODULE.equals(event.getModule()))
                .toList();
        if (targets.isEmpty()) return events;
        Set<Long> ids = new LinkedHashSet<>();
        for (UnifiedTraceEventVO event : targets) collectAttachmentIds(event.getDetail(), ids);
        Map<Long, String> names = attachmentNames(ids);
        for (UnifiedTraceEventVO event : targets) {
            JSONObject detail = parseEventDetail(event.getDetail());
            if (detail == null) continue;
            boolean changed = fillAttachmentNames(detail, names);
            changed |= fillLegacyChanges(detail, names);
            if (changed) event.setDetail(detail.toString());
        }
        return events;
    }

    private void collectAttachmentIds(String detailText, Set<Long> ids) {
        JSONObject detail = parseEventDetail(detailText);
        if (detail == null) return;
        JSONArray attachments = detail.getJSONArray("attachments");
        if (attachments != null) {
            for (Object item : attachments) {
                Long id = number(read(item, "id"));
                if (id != null) ids.add(id);
            }
        }
        for (String key : List.of("added", "removed")) {
            JSONArray array = detail.getJSONArray(key);
            if (array == null) continue;
            for (Object item : array) {
                Long id = number(item);
                if (id != null) ids.add(id);
            }
        }
    }

    private Map<Long, String> attachmentNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty() || attachmentMapper == null) return Collections.emptyMap();
        try {
            List<SysAttachment> files = attachmentMapper.selectBatchIds(ids);
            if (files == null || files.isEmpty()) return Collections.emptyMap();
            Map<Long, String> names = new java.util.HashMap<>();
            for (SysAttachment file : files) {
                if (file.getId() != null && file.getFileName() != null && !file.getFileName().isBlank()) {
                    names.put(file.getId(), file.getFileName());
                }
            }
            return names;
        } catch (Exception e) {
            log.warn("历史流水补文件名失败，按附件 ID 展示: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private boolean fillAttachmentNames(JSONObject detail, Map<Long, String> names) {
        JSONArray attachments = detail.getJSONArray("attachments");
        if (attachments == null || attachments.isEmpty()) return false;
        boolean changed = false;
        for (Object item : attachments) {
            if (!(item instanceof JSONObject attachment)) continue;
            String fileName = attachment.getStr("fileName");
            if (fileName != null && !fileName.isBlank()) continue;
            Long id = number(attachment.get("id"));
            if (id == null) continue;
            String resolved = names.get(id);
            attachment.set("fileName", resolved != null ? resolved : "附件 " + id + "（历史记录）");
            changed = true;
        }
        return changed;
    }

    private boolean fillLegacyChanges(JSONObject detail, Map<Long, String> names) {
        JSONArray changes = detail.getJSONArray("changes");
        if (changes != null && !changes.isEmpty()) return false;
        List<String> added = legacyFileLabels(detail.getJSONArray("added"), names);
        List<String> removed = legacyFileLabels(detail.getJSONArray("removed"), names);
        if (added.isEmpty() && removed.isEmpty()) return false;
        List<String> lines = new ArrayList<>();
        if (!added.isEmpty()) lines.add("新增附件：" + String.join("、", added));
        if (!removed.isEmpty()) lines.add("移除附件：" + String.join("、", removed));
        detail.set("changes", lines);
        return true;
    }

    private List<String> legacyFileLabels(JSONArray ids, Map<Long, String> names) {
        List<String> labels = new ArrayList<>();
        if (ids == null) return labels;
        for (Object item : ids) {
            Long id = number(item);
            if (id == null) continue;
            String resolved = names.get(id);
            labels.add(resolved != null ? "「" + resolved + "」" : "附件 " + id + "（历史记录）");
        }
        return labels;
    }

    private JSONObject parseEventDetail(String detail) {
        if (detail == null || detail.isBlank()) return null;
        try {
            return JSONUtil.parseObj(detail);
        } catch (Exception e) {
            return null;
        }
    }

    private Object read(Object item, String key) {
        if (item instanceof JSONObject json) return json.get(key);
        if (item instanceof Map<?, ?> map) return map.get(key);
        return null;
    }

    private Long number(Object value) {
        if (value == null) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 前端统一标识 → review_flow 存储的 bizType */
    private String reviewBizType(String bizType) {
        return switch (bizType) {
            case "order", "sales_order" -> "sales_order";
            case "sample", "sample_order" -> "sample";
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

    private String asString(Object value) { return value == null ? null : String.valueOf(value); }

    private <T> List<T> safe(List<T> list) { return list == null ? Collections.emptyList() : list; }
}
