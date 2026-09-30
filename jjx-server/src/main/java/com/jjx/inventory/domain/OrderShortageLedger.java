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
import java.util.Date;

/**
 * 订单缺料欠交台账（齐套 P4d，dev-20260930-026）
 * 对应表：order_shortage_ledger
 */
@Data
@TableName("order_shortage_ledger")
public class OrderShortageLedger implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long ledgerId;

    private Long orderId;
    private String orderNo;
    private Long productId;
    private Long materialId;
    private String materialCode;
    private String materialName;

    /** 该单该料毛需求 */
    private BigDecimal requiredQty;
    /** 当前缺口 */
    private BigDecimal shortageQty;
    /** 已满足量 */
    private BigDecimal fulfilledQty;

    /** 0=未满足 1=部分满足 2=已满足(闭环) 3=已关闭(取消) */
    private Integer status;

    private Date dueDate;

    private LocalDateTime lastCheckedAt;

    private String remark;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
