package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售订单成品库存预留记录
 * 对应表：sales_order_stock_reserve
 */
@Data
@TableName("sales_order_stock_reserve")
public class SalesOrderStockReserve implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 销售订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 产品ID */
    private Long productId;

    /** 成品物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 预留数量 */
    private BigDecimal reserveQuantity;

    /** 状态：0=有效 1=已释放 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
