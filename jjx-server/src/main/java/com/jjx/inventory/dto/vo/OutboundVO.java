package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 出库单视图对象VO
 */
@Data
public class OutboundVO {

    private Long outboundId;
    private String outboundNo;

    /** 链路追踪ID（DEV-568/569） */
    private String traceId;
    private String outboundType;
    private String outboundTypeName;
    private Long warehouseId;
    private String warehouseName;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private Long customerId;
    private String customerName;
    private LocalDate outboundDate;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private String orderStatus;
    private Integer status;
    private String statusName;
    private String approveStatus;
    private String remark;

    // ==================== 补料留痕（dev-20260924-002 / dev-20260923-025） ====================

    /** 补料来源类型（PRODUCTION_OVERUSE/SCRAP_REPLENISHMENT/TRIAL_ADJUSTMENT/INCOMING_DEFECT；空=普通领料） */
    private String supplementReasonType;

    /** 补料来源类型中文名（派生展示） */
    private String supplementReasonTypeName;

    /** 补料原因说明 */
    private String supplementReason;

    /** 关联质量不良单ID */
    private Long supplementNcrId;

    /** 本次补料关联的补产成品数量 */
    private java.math.BigDecimal supplementProductionQuantity;
    private String createBy;
    private String createByName;
    private LocalDateTime createTime;
    private String updateBy;
    private String updateByName;
    private LocalDateTime updateTime;
    private List<OutboundItemVO> items;
}
