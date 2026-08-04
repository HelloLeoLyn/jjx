package com.jjx.inventory.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 销售订单成品库存预留服务（DEV-578）
 */
public interface OrderStockReserveService {

    /**
     * 订单确认时执行成品库存检查+预留（幂等：先清旧预留再重建）
     * @return productId -> 缺货量（订单量-预留量，无库存档案按全量生产）
     */
    Map<Long, BigDecimal> reserveForOrder(Long orderId);

    /**
     * 订单取消时释放全部有效预留
     */
    void releaseByOrder(Long orderId);

    /**
     * 出库扣减时同步释放预留（DEV-580）：按物料释放最多 quantity 的预留
     */
    void releaseForOutbound(Long orderId, Long materialId, java.math.BigDecimal quantity);

    /**
     * 查询订单已预留数量：productId -> 预留量
     */
    Map<Long, BigDecimal> getReservedQty(Long orderId);

    /**
     * 查询订单缺货量：productId -> 缺货量（订单量-预留量，无预留记录=全量）
     */
    Map<Long, BigDecimal> getShortageQty(Long orderId);
}
