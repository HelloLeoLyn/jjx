package com.jjx.production.domain.vo;

import com.jjx.product.domain.vo.ProductStandardProcessVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工序执行VO
 */
@Data
@Schema(description = "生产工序执行VO")
public class ProductionOperationExecutionVO {

    @Schema(description = "执行ID")
    private Long executionId;

    @Schema(description = "生产订单ID")
    private Long orderId;

    @Schema(description = "生产订单编号")
    private String orderNo;

    @Schema(description = "标准工序ID")
    private Long processId;

    @Schema(description = "工序编码")
    private String processCode;

    @Schema(description = "工序名称")
    private String processName;

    @Schema(description = "工序顺序")
    private Integer processOrder;

    @Schema(description = "工序顺序文本")
    private String processOrderText;

    @Schema(description = "计划开始时间")
    private LocalDateTime plannedStartTime;

    @Schema(description = "计划结束时间")
    private LocalDateTime plannedEndTime;

    @Schema(description = "实际开始时间")
    private LocalDateTime actualStartTime;

    @Schema(description = "实际结束时间")
    private LocalDateTime actualEndTime;

    @Schema(description = "实际人工工时")
    private BigDecimal actualLaborHours;

    @Schema(description = "实际机器工时")
    private BigDecimal actualMachineHours;

    @Schema(description = "总实际工时")
    private BigDecimal totalActualHours;

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

    @Schema(description = "执行状态描述")
    private String executionStatusDesc;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    // ============ 关联信息 ============

    @Schema(description = "生产订单信息")
    private ProductionOrderVO productionOrder;

    @Schema(description = "标准工序信息")
    private ProductStandardProcessVO standardProcess;

    // ============ 计算字段 ============

    @Schema(description = "是否已开始")
    private Boolean hasStarted;

    @Schema(description = "是否已结束")
    private Boolean hasEnded;

    @Schema(description = "是否已超期")
    private Boolean isOverdue;

    @Schema(description = "是否为待执行状态")
    private Boolean isPending;

    @Schema(description = "是否为执行中状态")
    private Boolean isProcessing;

    @Schema(description = "是否为已完成状态")
    private Boolean isCompleted;

    @Schema(description = "是否为已跳过状态")
    private Boolean isSkipped;

    @Schema(description = "计划工时")
    private BigDecimal plannedHours;

    @Schema(description = "实际工时")
    private BigDecimal actualHours;

    @Schema(description = "合格率(%)")
    private BigDecimal qualifiedRate;

    @Schema(description = "不良率(%)")
    private BigDecimal defectiveRate;

    @Schema(description = "是否可以开始执行")
    private Boolean canStart;

    @Schema(description = "是否可以完成")
    private Boolean canComplete;

    // ============ 扩展字段 ============

    @Schema(description = "生产记录列表")
    private java.util.List<ProductionOperationRecordVO> operationRecords;

    @Schema(description = "延迟小时数")
    private BigDecimal delayHours;

    @Schema(description = "效率百分比(%)")
    private BigDecimal efficiencyPercentage;

    @Schema(description = "质量评分")
    private Integer qualityScore;

    @Schema(description = "设备利用率(%)")
    private BigDecimal equipmentUtilization;
}
