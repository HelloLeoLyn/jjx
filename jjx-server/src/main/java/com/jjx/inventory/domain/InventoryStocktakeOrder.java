package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 盘点单表实体类
 * 对应表：inventory_stocktake_order
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_stocktake_order")
public class InventoryStocktakeOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 盘点单ID */
    @TableId(type = IdType.AUTO)
    private Long stocktakeId;

    /** 盘点单号，格式：ST+YYYYMMDD+流水号 */
    private String stocktakeNo;

    /** 盘点类型：full全盘/partial抽盘/cycle循环盘点 */
    private String stocktakeType;

    /** 盘点仓库ID */
    private Long warehouseId;

    /** 盘点库位范围，JSON数组 */
    private String locationIds;

    /** 盘点物料范围，JSON数组 */
    private String materialIds;

    /** 计划开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planStartTime;

    /** 计划结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planEndTime;

    /** 实际开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualStartTime;

    /** 实际结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndTime;

    /** 盘点人ID */
    private Long stocktakerId;

    /** 盘点人姓名 */
    private String stocktakerName;

    /** 监盘人ID */
    private Long supervisorId;

    /** 监盘人姓名 */
    private String supervisorName;

    /** 系统总数量 */
    private BigDecimal totalSystemQuantity;

    /** 实盘总数量 */
    private BigDecimal totalActualQuantity;

    /** 总差异数量 */
    private BigDecimal totalDiffQuantity;

    /** 总差异金额 */
    private BigDecimal totalDiffAmount;

    /** 订单状态：draft草稿/processing盘点中/closed已关闭/cancelled已取消 */
    private Integer orderStatus;

    /** 审批状态：pending待审批/approved已批准/rejected已驳回 */
    private Integer approveStatus;

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
