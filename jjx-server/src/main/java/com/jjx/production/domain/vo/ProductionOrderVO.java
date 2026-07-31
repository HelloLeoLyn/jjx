package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产订单VO
 */
@Data
@Schema(description = "生产订单VO")
public class ProductionOrderVO {

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "订单类型：PLAN生产计划/WORK_ORDER生产工单")
    private String orderType;

    @Schema(description = "订单类型描述")
    private String orderTypeDesc;

    @Schema(description = "父订单ID（计划生成工单时使用）")
    private Long parentOrderId;

    @Schema(description = "父订单编号")
    private String parentOrderNo;

    @Schema(description = "销售订单ID")
    private Long salesOrderId;

    @Schema(description = "销售订单编号")
    private String salesOrderNo;

    @Schema(description = "产品ID")
    private Long productId;

    @Schema(description = "产品编码")
    private String productCode;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "产品规格")
    private String productSpec;

    @Schema(description = "产品单位")
    private String productUnit;

    @Schema(description = "使用的工艺路线ID")
    private Long routingId;

    @Schema(description = "工艺路线编码")
    private String routingCode;

    @Schema(description = "工艺路线名称")
    private String routingName;

    @Schema(description = "计划数量")
    private BigDecimal plannedQuantity;

    @Schema(description = "已完成数量")
    private BigDecimal completedQuantity;

    @Schema(description = "剩余数量")
    private BigDecimal remainingQuantity;

    @Schema(description = "完成百分比")
    private BigDecimal completionPercentage;

    @Schema(description = "计划开始日期")
    private LocalDate planStartDate;

    @Schema(description = "计划结束日期")
    private LocalDate planEndDate;

    @Schema(description = "实际开始时间")
    private LocalDateTime actualStartTime;

    @Schema(description = "实际结束时间")
    private LocalDateTime actualEndTime;

    @Schema(description = "订单状态：0草稿/1待审核/2已审核/3已驳回/4已计划/5待开始/6进行中/7已暂停/8已完成/9已取消/10已关闭/11已超期")
    private Integer orderStatus;

    @Schema(description = "订单状态描述")
    private String orderStatusDesc;

    @Schema(description = "审批状态：PENDING待审批/APPROVED已批准/REJECTED已拒绝/CANCELLED已取消")
    private Integer approvalStatus;

    @Schema(description = "审批状态描述")
    private String approvalStatusDesc;

    @Schema(description = "审批人ID")
    private Long approverId;

    @Schema(description = "审批人姓名")
    private String approverName;

    @Schema(description = "审批时间")
    private LocalDateTime approvalTime;

    @Schema(description = "审批备注")
    private String approvalRemark;

    @Schema(description = "优先级：LOW低/MEDIUM中/HIGH高/URGENT紧急")
    private String priority;

    @Schema(description = "优先级描述")
    private String priorityDesc;

    @Schema(description = "生产部门ID")
    private Long departmentId;

    @Schema(description = "生产部门名称")
    private String departmentName;

    @Schema(description = "材料成本")
    private BigDecimal materialCost;

    @Schema(description = "人工成本")
    private BigDecimal laborCost;

    @Schema(description = "总成本")
    private BigDecimal totalCost;

    @Schema(description = "创建者")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新者")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "备注")
    private String remark;

    // ============ 计算字段 ============

    @Schema(description = "是否已开始")
    private Boolean hasStarted;

    @Schema(description = "是否已结束")
    private Boolean hasEnded;

    @Schema(description = "是否已超期")
    private Boolean isOverdue;

    @Schema(description = "是否为生产计划")
    private Boolean isPlan;

    @Schema(description = "是否为生产工单")
    private Boolean isWorkOrder;

    @Schema(description = "是否可以开始")
    private Boolean canStart;

    @Schema(description = "是否可以完成")
    private Boolean canComplete;

    // ============ 扩展字段 ============

    @Schema(description = "工序执行列表")
    private java.util.List<ProductionOperationExecutionVO> operationExecutions;

    @Schema(description = "生产记录列表")
    private java.util.List<ProductionOperationRecordVO> operationRecords;

    @Schema(description = "预计完成日期")
    private LocalDate estimatedCompletionDate;

    @Schema(description = "延迟天数")
    private Integer delayDays;

    @Schema(description = "生产效率(%)")
    private BigDecimal productionEfficiency;

    @Schema(description = "质量合格率(%)")
    private BigDecimal qualityQualifiedRate;
}
