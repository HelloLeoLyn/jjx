package com.jjx.trace.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesQuotationFlow;
import com.jjx.sales.mapper.QuotationFlowMapper;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.ReviewFlowMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.trace.domain.vo.TraceReviewVO;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;
import com.jjx.trace.service.TraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceServiceImpl implements TraceService {

    private final SysOperLogMapper sysOperLogMapper;
    private final ReviewFlowMapper reviewFlowMapper;
    private final QuotationFlowMapper quotationFlowMapper;
    private final com.jjx.system.service.AttachmentEvidenceService attachmentEvidence;

    @Override
    public PageResult<UnifiedTraceEventVO> getEvents(String traceId, int pageNum, int pageSize) {
        if (traceId == null || traceId.isBlank()) {
            return PageResult.build(Collections.emptyList(), 0);
        }
        // 先取整条 trace 的日志再归并、最后按归并后的条数分页：否则一对「附件 + 保存」会被页码切开，
        // 总数也会和页面上看到的事件数不一致。一条 trace 只对应一次业务操作链，量级有限。
        List<SysOperLog> logs = sysOperLogMapper.selectList(
                Wrappers.<SysOperLog>lambdaQuery()
                        .eq(SysOperLog::getTraceId, traceId)
                        // 工程主数据归产品；读时排除历史误挂记录，保留 NULL 类型的旧日志。
                        .and(w -> w.isNull(SysOperLog::getBizType)
                                .or().notIn(SysOperLog::getBizType, List.of("bom", "routing")))
                        .orderByAsc(SysOperLog::getCreateTime)
                        .orderByAsc(SysOperLog::getId));
        List<UnifiedTraceEventVO> events = TraceEventAggregator.mergeAttachmentEvents(
                logs.stream().map(this::fromLog).toList());
        int size = Math.min(Math.max(pageSize, 1), 100);
        int num = Math.max(pageNum, 1);
        int from = Math.min((num - 1) * size, events.size());
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
    public PageResult<UnifiedTraceEventVO> getEventsByBiz(String bizType, Long bizId, int pageNum, int pageSize) {
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
        query.orderByAsc(SysOperLog::getCreateTime).orderByAsc(SysOperLog::getId);
        IPage<SysOperLog> page = sysOperLogMapper.selectPage(
                new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100)), query);
        List<UnifiedTraceEventVO> records = page.getRecords().stream().map(this::fromLog).toList();
        return PageResult.of(page, records);
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
        event.setAction(log.getAction());
        event.setOperatorName(log.getRealName() != null && !log.getRealName().isBlank()
                ? log.getRealName() : log.getUsername());
        event.setResult(log.getStatus());
        event.setTraceId(log.getTraceId());
        event.setModule(log.getModule());
        event.setBizType(log.getBizType());
        event.setBizId(log.getBizId());
        event.setBusinessType(log.getBusinessType());
        event.setOperUrl(log.getOperUrl());
        event.setOperParam(log.getOperParam());
        event.setDetail(log.getDetail());
        return event;
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
