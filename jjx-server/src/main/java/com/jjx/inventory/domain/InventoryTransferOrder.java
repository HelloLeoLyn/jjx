package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调拨单表实体类
 * 对应表：inventory_transfer_order
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_transfer_order")
public class InventoryTransferOrder extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 调拨单ID */
    @TableId(type = IdType.AUTO)
    private Long transferId;

    /** 调拨单号，格式：TR+YYYYMMDD+流水号 */
    private String transferNo;

    /** 调拨类型：normal普通调拨/urgent紧急调拨 */
    private String transferType;

    /** 调出仓库ID */
    private Long fromWarehouseId;

    /** 调出库位ID */
    private Long fromLocationId;

    /** 调入仓库ID */
    private Long toWarehouseId;

    /** 调入库位ID */
    private Long toLocationId;

    /** 调拨日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transferDate;

    /** 预计到达日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectedDate;

    /** 实际到达日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate actualDate;

    /** 调拨总数量 */
    private BigDecimal totalQuantity;

    /** 调拨总金额 */
    private BigDecimal totalAmount;

    /** 订单状态：draft草稿/approved已批准/out_confirm已出库/in_confirm已入库/closed已关闭/cancelled已取消 */
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

    /** 出库操作人 */
    private String outOperator;

    /** 出库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime outTime;

    /** 入库操作人 */
    private String inOperator;

    /** 入库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inTime;

}
