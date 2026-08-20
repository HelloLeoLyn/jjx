package com.jjx.production.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * P4-B：生产订单履历（订单头 + 时间线事件）
 */
@Data
public class OrderTraceVO {

    /** 订单头（复用 ProductionOrderVO 完整展示） */
    private ProductionOrderVO orderHeader;

    /** 时间线事件（按 eventTime ASC → sourceRank ASC → sourceId ASC 排序） */
    private List<TraceEventVO> events;
}
