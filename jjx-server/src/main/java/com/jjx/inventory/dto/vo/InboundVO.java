package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库单视图对象VO
 */
@Data
public class InboundVO {

    private Long inboundId;
    private String inboundNo;

    /** 链路追踪ID（DEV-568/569） */
    private String traceId;
    private String inboundType;
    /** 入库类型名称（采购入库/生产入库/退货入库/调拨入库/其他入库） */
    private String inboundTypeName;
    private Long warehouseId;
    private String warehouseName;
    private Long locationId;
    private String locationName;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private Long supplierId;
    private String supplierName;
    private LocalDate inboundDate;
    private Long inspectorId;
    private String inspectorName;
    private String inspectionResult;
    private LocalDateTime inspectionTime;
    private String inspectionRemark;
    private BigDecimal totalQuantity;
    /** 本单关联成品检验批去重后的原批数量；缺失关联时为 null。 */
    private BigDecimal sourceLotQuantity;
    private BigDecimal sourceRejectedQuantity;
    private List<InboundLotSummaryVO> sourceLots;
    private BigDecimal totalAmount;
    /** 允收量合计（按明细 accepted_quantity 汇总；dev-20260929-013） */
    private BigDecimal acceptedQuantity;
    /** 已入库量合计（按明细 posted_quantity 汇总；dev-20260929-013） */
    private BigDecimal postedQuantity;
    private String orderStatus;
    /** 状态码（与 orderStatus 同值，前端展示用） */
    private Integer status;
    /** 状态名称 */
    private String statusName;
    private String approveStatus;
    private String remark;
    private String createBy;
    private String createByName;
    private LocalDateTime createTime;
    private String updateBy;
    private String updateByName;
    private LocalDateTime updateTime;
    private List<InboundItemVO> items;
}
