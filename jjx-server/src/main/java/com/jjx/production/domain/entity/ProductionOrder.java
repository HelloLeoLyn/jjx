package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.production.enums.OrderStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产订单实体类
 * 对应表：production_order
 * 合并了生产计划和生产工单，通过order_type字段区分
 */
@Getter
@Setter
@TableName("production_order")
@Schema(description = "生产订单")
public class ProductionOrder{

    @Schema(description = "订单ID")
    @TableId(type = IdType.AUTO)
    private Long orderId;

    /** 链路追踪ID */
    private String traceId;

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "订单类型：PLAN生产计划/WORK_ORDER生产工单")
    private String orderType;

    @Schema(description = "父订单ID（计划生成工单时使用）")
    private Long parentOrderId;

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

    @Schema(description = "使用的BOM ID（DEV-617：实体补字段，此前转换丢失导致落库为NULL）")
    private Long bomId;

    @Schema(description = "BOM编码")
    private String bomCode;

    @Schema(description = "计划数量")
    private BigDecimal plannedQuantity;

    @Schema(description = "已完成数量(工序合格汇总，仅作进度展示)")
    private BigDecimal completedQuantity;

    /** 成品完工数量（最后一道工序/完工检验合格数，052口径，用于完工判断/入库/订单回写） */
    @Schema(description = "成品完工数量(最后一道工序合格数)")
    private BigDecimal finishedQuantity;

    @Schema(description = "剩余数量")
    private BigDecimal remainingQuantity;

    @Schema(description = "计划开始日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate planStartDate;

    @Schema(description = "计划结束日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate planEndDate;

    @Schema(description = "实际开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualStartTime;

    @Schema(description = "实际结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndTime;

    @Schema(description = "订单状态：0草稿/1待审核/2已审核/3已驳回/4已计划/5待开始/6进行中/7已暂停/8已完成/9已取消/10已关闭/11已超期")
    private Integer orderStatus;

    @Schema(description = "审批状态：PENDING待审批/APPROVED已批准/REJECTED已拒绝/CANCELLED已取消")
    private Integer approvalStatus;

    @Schema(description = "审批人ID")
    private Long approverId;

    @Schema(description = "审批人姓名")
    private String approverName;

    @Schema(description = "审批时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvalTime;

    @Schema(description = "审批备注")
    private String approvalRemark;

    @Schema(description = "优先级：LOW低/MEDIUM中/HIGH高/URGENT紧急")
    private String priority;

    @Schema(description = "生产部门ID")
    private Long departmentId;

    @Schema(description = "生产部门名称")
    private String departmentName;

    @Schema(description = "材料成本")
    private BigDecimal materialCost;

    @Schema(description = "领料状态：0未领料/1待发料/2已领料")
    private Integer materialStatus;

    /** 返工标记：0正常 1质检FAIL待返工（053） */
    @Schema(description = "返工标记：0正常 1质检FAIL待返工")
    private Integer reworkFlag;

    /** 完工操作人（053留痕） */
    @Schema(description = "完工操作人")
    private String completedBy;

    /** 关联完工质检单ID（053留痕） */
    @Schema(description = "关联完工质检单ID")
    private Long qualityInspectionId;

    /** 入库待处理标记：0正常 1入库失败待重试（056） */
    @Schema(description = "入库待处理标记：0正常 1入库失败待重试")
    private Integer inboundPendingFlag;

    /** 入库失败原因（056） */
    @Schema(description = "入库失败原因")
    private String inboundPendingReason;

    @Schema(description = "人工成本")
    private BigDecimal laborCost;

    @Schema(description = "总成本")
    private BigDecimal totalCost;

    @Schema(description = "创建者")
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新者")
    @TableField(fill = FieldFill.UPDATE)
    private String updateBy;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Schema(description = "备注")
    private String remark;

    // ============ 业务方法 ============

    /**
     * 检查是否为生产计划
     */
    public boolean isPlan() {
        return "PLAN".equals(orderType);
    }

    /**
     * 检查是否为生产工单
     */
    public boolean isWorkOrder() {
        return "WORK_ORDER".equals(orderType);
    }

    /**
     * 检查是否为草稿状态
     */
    public boolean isDraft() {
        return OrderStatusEnum.DRAFT.getCode().equals(orderStatus);
    }

    /**
     * 检查是否为待审批状态
     */
    public boolean isPendingApproval() {
        return OrderStatusEnum.PENDING_APPROVAL.getCode().equals(orderStatus);
    }

    /**
     * 检查是否为已批准状态
     */
    public boolean isApproved() {
        return OrderStatusEnum.APPROVED.getCode().equals(orderStatus);
    }

    /**
     * 检查是否为已排程状态
     */
    public boolean isScheduled() {
        return OrderStatusEnum.PLANNED.getCode().equals(orderStatus);
    }

    /**
     * 检查是否为进行中状态
     */
    public boolean isInProgress() {
        return OrderStatusEnum.IN_PROGRESS.getCode().equals(orderStatus);
    }

    /**
     * 检查是否为已完成状态
     */
    public boolean isCompleted() {
        return OrderStatusEnum.COMPLETED.getCode().equals(orderStatus);
    }

    /**
     * 检查是否为已取消状态
     */
    public boolean isCancelled() {
        return OrderStatusEnum.CANCELLED.getCode().equals(orderStatus);
    }

    /**
     * 检查是否已开始
     */
    public boolean hasStarted() {
        return actualStartTime != null;
    }

    /**
     * 检查是否已结束
     */
    public boolean hasEnded() {
        return actualEndTime != null;
    }

    /**
     * 检查是否已超期
     */
    public boolean isOverdue() {
        if (planEndDate == null || actualEndTime != null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return today.isAfter(planEndDate);
    }

    /**
     * 计算完成百分比
     */
    public BigDecimal getCompletionPercentage() {
        if (plannedQuantity == null || plannedQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (completedQuantity == null) {
            return BigDecimal.ZERO;
        }
        return completedQuantity.divide(plannedQuantity, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 检查是否可以开始生产
     */
    public boolean canStart() {
        return isApproved() || isScheduled();
    }

    /**
     * 检查是否可以完成
     */
    public boolean canComplete() {
        return isInProgress() && completedQuantity != null &&
               plannedQuantity != null && completedQuantity.compareTo(plannedQuantity) >= 0;
    }

    /**
     * 验证订单数据
     */
    public void validate() {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new IllegalArgumentException("订单编号不能为空");
        }
        if (orderType == null || orderType.trim().isEmpty()) {
            throw new IllegalArgumentException("订单类型不能为空");
        }
        if (productId == null) {
            throw new IllegalArgumentException("产品ID不能为空");
        }
        if (productCode == null || productCode.trim().isEmpty()) {
            throw new IllegalArgumentException("产品编码不能为空");
        }
        if (productName == null || productName.trim().isEmpty()) {
            throw new IllegalArgumentException("产品名称不能为空");
        }
        if (plannedQuantity == null || plannedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("计划数量必须大于0");
        }
        if (planStartDate == null) {
            throw new IllegalArgumentException("计划开始日期不能为空");
        }
        if (planEndDate == null) {
            throw new IllegalArgumentException("计划结束日期不能为空");
        }
        if (planEndDate.isBefore(planStartDate)) {
            throw new IllegalArgumentException("计划结束日期不能早于计划开始日期");
        }
        if (orderStatus == null) {
            throw new IllegalArgumentException("订单状态不能为空");
        }
        if (priority == null || priority.trim().isEmpty()) {
            throw new IllegalArgumentException("优先级不能为空");
        }
    }

    /**
     * 更新完成数量
     */
    public void updateCompletedQuantity(BigDecimal newCompletedQuantity) {
        if (newCompletedQuantity == null) {
            newCompletedQuantity = BigDecimal.ZERO;
        }
        if (newCompletedQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("完成数量不能为负数");
        }
        if (plannedQuantity != null && newCompletedQuantity.compareTo(plannedQuantity) > 0) {
            throw new IllegalArgumentException("完成数量不能超过计划数量");
        }

        this.completedQuantity = newCompletedQuantity;
        if (plannedQuantity != null) {
            this.remainingQuantity = plannedQuantity.subtract(newCompletedQuantity);
        }
    }
}
