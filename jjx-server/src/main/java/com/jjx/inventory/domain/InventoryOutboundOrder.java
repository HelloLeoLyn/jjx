package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 出库单表实体类
 * 对应表：inventory_outbound_order
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_outbound_order")
public class InventoryOutboundOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 出库单ID */
    @TableId(type = IdType.AUTO)
    private Long outboundId;

    /** 出库单号，格式：OUT+YYYYMMDD+流水号 */
    private String outboundNo;

    /** 出库类型：production生产领料/sales销售出库/return退货出库/scrap报废出库/transfer调拨出库/adjust盘亏出库 */
    private String outboundType;

    /** 来源类型：work_order/sales_order/purchase_return */
    private String sourceType;

    /** 来源单据ID */
    private Long sourceId;

    /** 来源单号 */
    private String sourceNo;

    /** 出库仓库ID */
    private Long warehouseId;

    /** 客户ID（销售出库时使用） */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 出库日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate outboundDate;

    /** 总数量 */
    private BigDecimal totalQuantity;

    /** 总金额 */
    private BigDecimal totalAmount;

    /** 订单状态：draft草稿/confirmed已确认/closed已关闭/cancelled已取消 */
    private String orderStatus;

    /** 审批状态：pending待审批/approved已批准/rejected已驳回 */
    private String approveStatus;

    /** 审批人ID */
    private Long approverId;

    /** 审批人姓名 */
    private String approverName;

    /** 审批时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approveTime;

    /** 审批意见 */
    private String approveRemark;

}
