package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.product.domain.entity.ProductStandardProcess;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工序执行实体类
 * 对应表：production_operation_execution
 * 记录工单的工序实际执行情况
 */
@Getter
@Setter
@TableName("production_operation_execution")
@Schema(description = "生产工序执行")
public class ProductionOperationExecution {

    @Schema(description = "执行ID")
    @TableId(type = IdType.AUTO)
    private Long executionId;

    @Schema(description = "生产订单ID")
    private Long orderId;

    @Schema(description = "标准工序ID")
    private Long processId;

    @Schema(description = "工序顺序")
    private Integer processOrder;

    @Schema(description = "计划开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime plannedStartTime;

    @Schema(description = "计划结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime plannedEndTime;

    @Schema(description = "实际开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualStartTime;

    @Schema(description = "实际结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndTime;

    @Schema(description = "实际人工工时")
    private BigDecimal actualLaborHours;

    @Schema(description = "实际机器工时")
    private BigDecimal actualMachineHours;

    @Schema(description = "使用设备ID")
    private Long equipmentId;

    @Schema(description = "设备编号")
    private String equipmentCode;

    @Schema(description = "设备名称")
    private String equipmentName;

    @Schema(description = "操作员ID")
    private Long operatorId;

    @Schema(description = "操作员姓名")
    private String operatorName;

    @Schema(description = "投入数量")
    private BigDecimal inputQuantity;

    @Schema(description = "产出数量")
    private BigDecimal outputQuantity;

    @Schema(description = "合格数量")
    private BigDecimal qualifiedQuantity;

    @Schema(description = "不良数量")
    private BigDecimal defectiveQuantity;

    @Schema(description = "不良原因")
    private String defectiveReason;

    @Schema(description = "实际工艺参数（JSON格式）")
    private String actualProcessParams;

    @Schema(description = "质量检查结果（JSON格式）")
    private String qualityCheckResult;

    @Schema(description = "执行状态：PENDING待执行/PROCESSING执行中/COMPLETED已完成/SKIPPED已跳过")
    private String executionStatus;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ============ 关联实体（非数据库字段） ============

    @Schema(description = "生产订单信息")
    @TableField(exist = false)
    private ProductionOrder productionOrder;

    @Schema(description = "标准工序信息")
    @TableField(exist = false)
    private ProductStandardProcess standardProcess;

    // ============ 业务方法 ============

    /**
     * 检查是否为待执行状态
     */
    public boolean isPending() {
        return "PENDING".equals(executionStatus);
    }

    /**
     * 检查是否为执行中状态
     */
    public boolean isProcessing() {
        return "PROCESSING".equals(executionStatus);
    }

    /**
     * 检查是否为已完成状态
     */
    public boolean isCompleted() {
        return "COMPLETED".equals(executionStatus);
    }

    /**
     * 检查是否为已跳过状态
     */
    public boolean isSkipped() {
        return "SKIPPED".equals(executionStatus);
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
        if (plannedEndTime == null || actualEndTime != null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(plannedEndTime);
    }

    /**
     * 计算实际总工时（人工+机器）
     */
    public BigDecimal getTotalActualHours() {
        BigDecimal labor = actualLaborHours != null ? actualLaborHours : BigDecimal.ZERO;
        BigDecimal machine = actualMachineHours != null ? actualMachineHours : BigDecimal.ZERO;
        return labor.add(machine);
    }

    /**
     * 计算计划工时（如果计划时间存在）
     */
    public BigDecimal getPlannedHours() {
        if (plannedStartTime == null || plannedEndTime == null) {
            return BigDecimal.ZERO;
        }
        long seconds = java.time.Duration.between(plannedStartTime, plannedEndTime).getSeconds();
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 计算实际工时（如果实际时间存在）
     */
    public BigDecimal getActualHours() {
        if (actualStartTime == null || actualEndTime == null) {
            return BigDecimal.ZERO;
        }
        long seconds = java.time.Duration.between(actualStartTime, actualEndTime).getSeconds();
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 计算合格率
     */
    public BigDecimal getQualifiedRate() {
        if (outputQuantity == null || outputQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (qualifiedQuantity == null) {
            return BigDecimal.ZERO;
        }
        return qualifiedQuantity.divide(outputQuantity, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 计算不良率
     */
    public BigDecimal getDefectiveRate() {
        if (outputQuantity == null || outputQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (defectiveQuantity == null) {
            return BigDecimal.ZERO;
        }
        return defectiveQuantity.divide(outputQuantity, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 检查是否可以开始执行
     */
    public boolean canStart() {
        return isPending() && plannedStartTime != null;
    }

    /**
     * 检查是否可以完成
     */
    public boolean canComplete() {
        return isProcessing() && hasStarted() &&
               outputQuantity != null && outputQuantity.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 验证执行数据
     */
    public void validate() {
        if (orderId == null) {
            throw new IllegalArgumentException("生产订单ID不能为空");
        }
        if (processId == null) {
            throw new IllegalArgumentException("标准工序ID不能为空");
        }
        if (processOrder == null || processOrder < 1) {
            throw new IllegalArgumentException("工序顺序必须大于0");
        }
        if (executionStatus == null || executionStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("执行状态不能为空");
        }
        if (plannedStartTime != null && plannedEndTime != null &&
            plannedEndTime.isBefore(plannedStartTime)) {
            throw new IllegalArgumentException("计划结束时间不能早于计划开始时间");
        }
        if (actualStartTime != null && actualEndTime != null &&
            actualEndTime.isBefore(actualStartTime)) {
            throw new IllegalArgumentException("实际结束时间不能早于实际开始时间");
        }
        if (inputQuantity != null && inputQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("投入数量不能为负数");
        }
        if (outputQuantity != null && outputQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("产出数量不能为负数");
        }
        if (qualifiedQuantity != null && qualifiedQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("合格数量不能为负数");
        }
        if (defectiveQuantity != null && defectiveQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("不良数量不能为负数");
        }
        if (actualLaborHours != null && actualLaborHours.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("实际人工工时不能为负数");
        }
        if (actualMachineHours != null && actualMachineHours.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("实际机器工时不能为负数");
        }
    }

    /**
     * 更新产出数量
     */
    public void updateOutput(BigDecimal newOutputQuantity, BigDecimal newQualifiedQuantity, BigDecimal newDefectiveQuantity) {
        if (newOutputQuantity == null || newOutputQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("产出数量不能为空或负数");
        }
        if (newQualifiedQuantity == null || newQualifiedQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("合格数量不能为空或负数");
        }
        if (newDefectiveQuantity == null || newDefectiveQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("不良数量不能为空或负数");
        }

        // 检查合格数量+不良数量是否等于产出数量
        BigDecimal total = newQualifiedQuantity.add(newDefectiveQuantity);
        if (total.compareTo(newOutputQuantity) != 0) {
            throw new IllegalArgumentException("合格数量加不良数量必须等于产出数量");
        }

        outputQuantity = newOutputQuantity;
        qualifiedQuantity = newQualifiedQuantity;
        defectiveQuantity = newDefectiveQuantity;
    }
}
