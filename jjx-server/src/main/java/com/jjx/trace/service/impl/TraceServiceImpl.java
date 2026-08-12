package com.jjx.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.trace.service.TraceService;
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
}
