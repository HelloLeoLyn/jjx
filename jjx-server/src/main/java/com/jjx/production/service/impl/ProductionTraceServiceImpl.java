package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.TraceQueryDTO;
import com.jjx.production.domain.entity.ProductionTraceLog;
import com.jjx.production.domain.vo.TraceVO;
import com.jjx.production.mapper.ProductionTraceLogMapper;
import com.jjx.production.service.ProductionTraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionTraceServiceImpl implements ProductionTraceService {

    private final ProductionTraceLogMapper traceLogMapper;

    @Override
    public PageResult<TraceVO> page(TraceQueryDTO query) {
        LambdaQueryWrapper<ProductionTraceLog> wrapper = buildQueryWrapper(query);
        wrapper.orderByDesc(ProductionTraceLog::getCreateTime);

        Page<ProductionTraceLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<ProductionTraceLog> result = traceLogMapper.selectPage(page, wrapper);

        List<TraceVO> voList = result.getRecords().stream()
            .map(this::toVO)
            .collect(Collectors.toList());

        return PageResult.build(voList, result.getTotal());
    }

    @Override
    public List<TraceVO> traceForward(String traceCode) {
        // 正追溯：按编码查询所有关联记录
        LambdaQueryWrapper<ProductionTraceLog> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ProductionTraceLog::getTraceCode, traceCode)
               .orderByAsc(ProductionTraceLog::getOperateTime);
        return traceLogMapper.selectList(wrapper).stream()
            .map(this::toVO)
            .collect(Collectors.toList());
    }

    @Override
    public List<TraceVO> traceBackward(String traceCode) {
        // 反追溯：按编码及关联工单/物料反向查询
        List<TraceVO> results = new ArrayList<>();

        // 1. 查匹配的记录
        LambdaQueryWrapper<ProductionTraceLog> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ProductionTraceLog::getTraceCode, traceCode)
               .orderByDesc(ProductionTraceLog::getOperateTime);
        results.addAll(traceLogMapper.selectList(wrapper).stream()
            .map(this::toVO).collect(Collectors.toList()));

        // 2. 如果有 orderId，查该工单的所有记录
        ProductionTraceLog first = traceLogMapper.selectOne(wrapper.last("LIMIT 1"));
        if (first != null && first.getOrderId() != null) {
            LambdaQueryWrapper<ProductionTraceLog> orderWrapper = Wrappers.lambdaQuery();
            orderWrapper.eq(ProductionTraceLog::getOrderId, first.getOrderId())
                        .orderByDesc(ProductionTraceLog::getOperateTime);
            List<TraceVO> orderTraces = traceLogMapper.selectList(orderWrapper).stream()
                .map(this::toVO).collect(Collectors.toList());
            // 合并去重
            for (TraceVO t : orderTraces) {
                if (results.stream().noneMatch(r -> r.getTraceId().equals(t.getTraceId()))) {
                    results.add(t);
                }
            }
        }

        return results;
    }

    private LambdaQueryWrapper<ProductionTraceLog> buildQueryWrapper(TraceQueryDTO query) {
        LambdaQueryWrapper<ProductionTraceLog> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(query.getTraceCode())) {
            wrapper.like(ProductionTraceLog::getTraceCode, query.getTraceCode());
        }
        if (StringUtils.isNotBlank(query.getTraceType())) {
            wrapper.eq(ProductionTraceLog::getTraceType, query.getTraceType());
        }
        if (StringUtils.isNotBlank(query.getBatchNo())) {
            wrapper.like(ProductionTraceLog::getBatchNo, query.getBatchNo());
        }
        if (query.getOrderId() != null) {
            wrapper.eq(ProductionTraceLog::getOrderId, query.getOrderId());
        }
        return wrapper;
    }

    private TraceVO toVO(ProductionTraceLog entity) {
        if (entity == null) return null;
        TraceVO vo = new TraceVO();
        vo.setTraceId(entity.getTraceId());
        vo.setTraceType(entity.getTraceType());
        vo.setTraceTypeName(getTypeName(entity.getTraceType()));
        vo.setTraceCode(entity.getTraceCode());
        vo.setBatchNo(entity.getBatchNo());
        vo.setOrderId(entity.getOrderId());
        vo.setOperation(entity.getOperation());
        vo.setOperationName(getOperationName(entity.getOperation()));
        vo.setOperator(entity.getOperator());
        vo.setOperateTime(entity.getOperateTime());
        vo.setDetail(entity.getDetail());
        vo.setCreateBy(entity.getCreateBy());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private String getTypeName(String type) {
        if ("MATERIAL".equals(type)) return "原料追溯";
        if ("ORDER".equals(type)) return "工单追溯";
        if ("PRODUCT".equals(type)) return "产品追溯";
        return type;
    }

    private String getOperationName(String op) {
        if ("inbound".equals(op)) return "入库";
        if ("outbound".equals(op)) return "出库";
        if ("start".equals(op)) return "开工";
        if ("complete".equals(op)) return "完工";
        if ("inspect".equals(op)) return "质检";
        return op;
    }
}
