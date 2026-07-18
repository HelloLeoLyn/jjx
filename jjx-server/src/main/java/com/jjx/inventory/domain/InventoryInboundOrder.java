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
 * 入库单表实体类
 * 对应表：inventory_inbound_order
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_inbound_order")
public class InventoryInboundOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 入库单ID */
    @TableId(type = IdType.AUTO)
    private Long inboundId;

    /** 入库单号，格式：IN+YYYYMMDD+流水号 */
    private String inboundNo;

    /** 入库类型：purchase采购入库/production生产入库/return退货入库/transfer调拨入库/adjust盘盈入库 */
    private String inboundType;

    /** 来源类型：purchase_order/work_order/sales_return */
    private String sourceType;

    /** 来源单据ID */
    private Long sourceId;

    /** 来源单号 */
    private String sourceNo;

    /** 入库仓库ID */
    private Long warehouseId;

    /** 建议库位ID */
    private Long locationId;

    /** 供应商ID（采购入库时使用） */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 入库日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate inboundDate;

    /** 总数量 */
    private BigDecimal totalQuantity;

    /** 总金额 */
    private BigDecimal totalAmount;

    /** 检验员ID */
    private Long inspectorId;

    /** 检验员姓名 */
    private String inspectorName;

    /** 检验结果：pass合格/fail不合格/partial部分合格 */
    private String inspectionResult;

    /** 检验时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inspectionTime;

    /** 检验备注 */
    private String inspectionRemark;

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
