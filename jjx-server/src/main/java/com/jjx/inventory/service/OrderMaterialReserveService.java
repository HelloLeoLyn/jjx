package com.jjx.inventory.service;

import java.util.Map;

/**
 * 订单材料预占服务（094定稿：确认前手动预占原料）
 */
public interface OrderMaterialReserveService {

    /**
     * 手动预占：按BOM展开原料写入预占表，订单标记已预占
     * @param orderId 订单ID
     * @param days 预占天数 1~7（默认3）
     */
    Map<String, Object> reserveForOrder(Long orderId, Integer days);

    /**
     * 延迟预占：每次+3天
     */
    void extendReserve(Long orderId);

    /**
     * 释放预占（取消/完成/超时/手动）
     */
    void releaseByOrder(Long orderId, String reason);

    /**
     * 释放指定订单指定物料的预占
     */
    void releaseByOrderAndMaterial(Long orderId, Long materialId, String reason);

    /**
     * 查询某物料所有订单的预占占用量（可用量口径 = 总量-预留-预占）
     */
    java.math.BigDecimal getReservedQtyByMaterial(Long materialId);

    /**
     * 定时任务：剩余1天提醒 + 到期自动释放
     */
    void processTimeout();

    /**
     * 查询订单预占信息
     */
    Map<String, Object> getOrderReserveInfo(Long orderId);
}
