package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售发货明细（2026-09-21 dev-20260921-039）。
 *
 * <p>一张发货单可只发订单的部分数量：出库按本明细数量扣库存，发货单打印/详情/对账都按本明细展示，
 * 拒收回冲也按本明细回库。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_delivery_item")
public class SalesDeliveryItem extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long itemId;

    /** 发货单ID */
    private Long deliveryId;

    /** 销售订单明细ID（sales_order_product.id） */
    private Long orderProductId;

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 规格描述 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 本次发货数量 */
    private Integer quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 金额 */
    private BigDecimal amount;
}
