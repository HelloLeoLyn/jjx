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

    @Schema(description = "大类：ASSEMBLY冲型组装/PRINT印刷（2026-08-12）")
    private String majorCategory;

    @Schema(description = "工序名称")
    private String processName;

    @Schema(description = "计划工艺参数JSON（2026-08-12）")
    private String customProcessParams;

    @Schema(description = "工序编码")
    private String processCode;

    @Schema(description = "工序图标（2026-08-11 展示用）")
    private String icon;

    @Schema(description = "是否有下标（2026-08-11 展示用）")
    private Integer hasIndex;

    @Schema(description = "下标数字（2026-08-11 展示用）")
    private Integer indexNumber;

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

    @Schema(description = "待完成数量（= 任务数量 - 已完成，下限0；P3 任务分配用）")
    private BigDecimal remainingQuantity;

    @Schema(description = "是否已建立任务树根节点（P3 派工管理）")
    private Boolean hasTaskRoot;

    @Schema(description = "任务树根节点持有人ID（P3 派工管理）")
    private Long taskRootAssigneeId;

    @Schema(description = "任务树根节点持有人姓名（P3 派工管理）")
    private String taskRootAssigneeName;

    @Schema(description = "任务树节点总数（含根；0=未建立）（P3 派工管理）")
    private Integer taskNodeCount;

    @Schema(description = "任务链摘要（未分配 / 根节点持有人 → 后续节点摘要）（P3 派工管理）")
    private String taskChainText;

    @Schema(description = "当前用户可继续分配的任务节点ID（本人持有且可分配>0；无 root 时为 null）（P3 派工管理）")
    private Long myAssignableNodeId;

    @Schema(description = "当前用户可继续分配数量（本人持有节点，availableToAssign）（P3 派工管理）")
    private BigDecimal myAssignableQuantity;

    @Schema(description = "我的任务：当前用户在该工序持有的有效 TaskNode.taskQuantity 合计（无节点为 0）（派工列表投影）")
    private BigDecimal myTaskQuantity;

    @Schema(description = "已分给下级：当前用户节点的直接有效子节点 effective 合计（无节点为 0）（派工列表投影）")
    private BigDecimal myChildOccupied;

    @Schema(description = "我自己剩余：当前用户节点 selfRemaining = effective - childOccupied - selfReported（下限0，无节点为 0）（派工列表投影）")
    private BigDecimal myOwnHeld;

    @Schema(description = "不良原因")
    private String defectiveReason;

    @Schema(description = "实际工艺参数（JSON格式）")
    private String actualProcessParams;

    @Schema(description = "质量检查结果（JSON格式）")
    private String qualityCheckResult;

    @Schema(description = "执行状态：PENDING待执行/PROCESSING执行中/COMPLETED已完成/SKIPPED已跳过")
    private Integer executionStatus;

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
