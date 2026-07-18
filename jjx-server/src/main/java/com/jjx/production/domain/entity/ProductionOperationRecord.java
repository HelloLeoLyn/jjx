package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工序记录实体类
 * 对应表：production_operation_record
 * 记录工序执行的详细操作历史
 */
@Getter
@Setter
@TableName("production_operation_record")
@Schema(description = "生产工序记录")
public class ProductionOperationRecord {

    @Schema(description = "记录ID")
    @TableId(type = IdType.AUTO)
    private Long recordId;

    @Schema(description = "工序执行ID")
    private Long executionId;

    @Schema(description = "记录类型：START开始/PAUSE暂停/RESUME恢复/COMPLETE完成/QUALITY质量检查/ISSUE问题记录/PARAM参数调整/STATUS状态变更")
    private String recordType;

    @Schema(description = "记录时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordTime;

    @Schema(description = "操作员ID")
    private Long operatorId;

    @Schema(description = "操作员姓名")
    private String operatorName;

    @Schema(description = "数量")
    private BigDecimal quantity;

    @Schema(description = "参数（JSON格式）")
    private String parameters;

    @Schema(description = "质量数据（JSON格式）")
    private String qualityData;

    @Schema(description = "问题描述")
    private String issueDescription;

    @Schema(description = "解决方案")
    private String issueSolution;

    @Schema(description = "附件URL")
    private String attachmentUrl;

    @Schema(description = "备注")
    private String remark;

    // ============ 关联实体（非数据库字段） ============

    @Schema(description = "工序执行信息")
    @TableField(exist = false)
    private ProductionOperationExecution execution;

    // ============ 业务方法 ============

    /**
     * 检查是否为开始记录
     */
    public boolean isStartRecord() {
        return "START".equals(recordType);
    }

    /**
     * 检查是否为暂停记录
     */
    public boolean isPauseRecord() {
        return "PAUSE".equals(recordType);
    }

    /**
     * 检查是否为恢复记录
     */
    public boolean isResumeRecord() {
        return "RESUME".equals(recordType);
    }

    /**
     * 检查是否为完成记录
     */
    public boolean isCompleteRecord() {
        return "COMPLETE".equals(recordType);
    }

    /**
     * 检查是否为质量检查记录
     */
    public boolean isQualityRecord() {
        return "QUALITY".equals(recordType);
    }

    /**
     * 检查是否为问题记录
     */
    public boolean isIssueRecord() {
        return "ISSUE".equals(recordType);
    }

    /**
     * 检查是否为参数调整记录
     */
    public boolean isParamRecord() {
        return "PARAM".equals(recordType);
    }

    /**
     * 检查是否为状态变更记录
     */
    public boolean isStatusRecord() {
        return "STATUS".equals(recordType);
    }

    /**
     * 检查是否为操作类记录（开始、暂停、恢复、完成）
     */
    public boolean isOperationRecord() {
        return isStartRecord() || isPauseRecord() || isResumeRecord() || isCompleteRecord();
    }

    /**
     * 检查是否为数据类记录（质量、问题、参数、状态）
     */
    public boolean isDataRecord() {
        return isQualityRecord() || isIssueRecord() || isParamRecord() || isStatusRecord();
    }

    /**
     * 检查是否有附件
     */
    public boolean hasAttachment() {
        return attachmentUrl != null && !attachmentUrl.trim().isEmpty();
    }

    /**
     * 检查是否有质量问题
     */
    public boolean hasQualityIssue() {
        return isIssueRecord() && issueDescription != null && !issueDescription.trim().isEmpty();
    }

    /**
     * 检查是否有参数调整
     */
    public boolean hasParameterAdjustment() {
        return isParamRecord() && parameters != null && !parameters.trim().isEmpty();
    }

    /**
     * 获取记录类型显示文本
     */
    public String getRecordTypeText() {
        switch (recordType) {
            case "START":
                return "开始执行";
            case "PAUSE":
                return "暂停执行";
            case "RESUME":
                return "恢复执行";
            case "COMPLETE":
                return "完成执行";
            case "QUALITY":
                return "质量检查";
            case "ISSUE":
                return "问题记录";
            case "PARAM":
                return "参数调整";
            case "STATUS":
                return "状态变更";
            default:
                return recordType;
        }
    }

    /**
     * 获取记录图标（用于前端显示）
     */
    public String getRecordIcon() {
        switch (recordType) {
            case "START":
                return "play-circle";
            case "PAUSE":
                return "pause-circle";
            case "RESUME":
                return "play-circle";
            case "COMPLETE":
                return "check-circle";
            case "QUALITY":
                return "quality-check";
            case "ISSUE":
                return "warning";
            case "PARAM":
                return "adjustments";
            case "STATUS":
                return "status-change";
            default:
                return "file-text";
        }
    }

    /**
     * 获取记录颜色（用于前端显示）
     */
    public String getRecordColor() {
        switch (recordType) {
            case "START":
                return "success";
            case "PAUSE":
                return "warning";
            case "RESUME":
                return "info";
            case "COMPLETE":
                return "success";
            case "QUALITY":
                return "primary";
            case "ISSUE":
                return "danger";
            case "PARAM":
                return "warning";
            case "STATUS":
                return "info";
            default:
                return "default";
        }
    }

    /**
     * 验证记录数据
     */
    public void validate() {
        if (executionId == null) {
            throw new IllegalArgumentException("工序执行ID不能为空");
        }
        if (recordType == null || recordType.trim().isEmpty()) {
            throw new IllegalArgumentException("记录类型不能为空");
        }
        if (recordTime == null) {
            throw new IllegalArgumentException("记录时间不能为空");
        }
        if (operatorId == null) {
            throw new IllegalArgumentException("操作员ID不能为空");
        }
        if (operatorName == null || operatorName.trim().isEmpty()) {
            throw new IllegalArgumentException("操作员姓名不能为空");
        }

        // 特定记录类型的验证
        if (isIssueRecord()) {
            if (issueDescription == null || issueDescription.trim().isEmpty()) {
                throw new IllegalArgumentException("问题记录必须有问题描述");
            }
        }

        if (isQualityRecord()) {
            if (qualityData == null || qualityData.trim().isEmpty()) {
                throw new IllegalArgumentException("质量检查记录必须有质量数据");
            }
        }

        if (isParamRecord()) {
            if (parameters == null || parameters.trim().isEmpty()) {
                throw new IllegalArgumentException("参数调整记录必须有参数数据");
            }
        }

        if (quantity != null && quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("数量不能为负数");
        }
    }

    /**
     * 创建开始记录
     */
    public static ProductionOperationRecord createStartRecord(Long executionId, Long operatorId, String operatorName) {
        ProductionOperationRecord record = new ProductionOperationRecord();
        record.setExecutionId(executionId);
        record.setRecordType("START");
        record.setRecordTime(LocalDateTime.now());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        return record;
    }

    /**
     * 创建完成记录
     */
    public static ProductionOperationRecord createCompleteRecord(Long executionId, Long operatorId, String operatorName, BigDecimal quantity) {
        ProductionOperationRecord record = new ProductionOperationRecord();
        record.setExecutionId(executionId);
        record.setRecordType("COMPLETE");
        record.setRecordTime(LocalDateTime.now());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setQuantity(quantity);
        return record;
    }

    /**
     * 创建质量检查记录
     */
    public static ProductionOperationRecord createQualityRecord(Long executionId, Long operatorId, String operatorName, String qualityData) {
        ProductionOperationRecord record = new ProductionOperationRecord();
        record.setExecutionId(executionId);
        record.setRecordType("QUALITY");
        record.setRecordTime(LocalDateTime.now());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setQualityData(qualityData);
        return record;
    }

    /**
     * 创建问题记录
     */
    public static ProductionOperationRecord createIssueRecord(Long executionId, Long operatorId, String operatorName,
                                                              String issueDescription, String issueSolution) {
        ProductionOperationRecord record = new ProductionOperationRecord();
        record.setExecutionId(executionId);
        record.setRecordType("ISSUE");
        record.setRecordTime(LocalDateTime.now());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setIssueDescription(issueDescription);
        record.setIssueSolution(issueSolution);
        return record;
    }

    /**
     * 创建参数调整记录
     */
    public static ProductionOperationRecord createParamRecord(Long executionId, Long operatorId, String operatorName,
                                                              String parameters) {
        ProductionOperationRecord record = new ProductionOperationRecord();
        record.setExecutionId(executionId);
        record.setRecordType("PARAM");
        record.setRecordTime(LocalDateTime.now());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setParameters(parameters);
        return record;
    }

    /**
     * 获取简化的记录信息（用于日志显示）
     */
    public String getSummary() {
        return String.format("[%s] %s - %s",
            recordTime != null ? recordTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) : "",
            operatorName,
            getRecordTypeText());
    }
}
