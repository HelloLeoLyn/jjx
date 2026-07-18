package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工序记录VO
 */
@Data
@Schema(description = "生产工序记录VO")
public class ProductionOperationRecordVO {

    @Schema(description = "记录ID")
    private Long recordId;

    @Schema(description = "工序执行ID")
    private Long executionId;

    @Schema(description = "记录类型：START开始/PAUSE暂停/RESUME恢复/COMPLETE完成/QUALITY质量检查/ISSUE问题记录/PARAM参数调整/STATUS状态变更")
    private String recordType;

    @Schema(description = "记录类型描述")
    private String recordTypeText;

    @Schema(description = "记录图标")
    private String recordIcon;

    @Schema(description = "记录颜色")
    private String recordColor;

    @Schema(description = "记录时间")
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

    // ============ 关联信息 ============

    @Schema(description = "工序执行信息")
    private ProductionOperationExecutionVO execution;

    // ============ 计算字段 ============

    @Schema(description = "是否为开始记录")
    private Boolean isStartRecord;

    @Schema(description = "是否为暂停记录")
    private Boolean isPauseRecord;

    @Schema(description = "是否为恢复记录")
    private Boolean isResumeRecord;

    @Schema(description = "是否为完成记录")
    private Boolean isCompleteRecord;

    @Schema(description = "是否为质量检查记录")
    private Boolean isQualityRecord;

    @Schema(description = "是否为问题记录")
    private Boolean isIssueRecord;

    @Schema(description = "是否为参数调整记录")
    private Boolean isParamRecord;

    @Schema(description = "是否为状态变更记录")
    private Boolean isStatusRecord;

    @Schema(description = "是否为操作类记录")
    private Boolean isOperationRecord;

    @Schema(description = "是否为数据类记录")
    private Boolean isDataRecord;

    @Schema(description = "是否有附件")
    private Boolean hasAttachment;

    @Schema(description = "是否有质量问题")
    private Boolean hasQualityIssue;

    @Schema(description = "是否有参数调整")
    private Boolean hasParameterAdjustment;

    // ============ 扩展字段 ============

    @Schema(description = "记录摘要")
    private String summary;

    @Schema(description = "格式化时间")
    private String formattedTime;

    @Schema(description = "参数解析结果")
    private Object parsedParameters;

    @Schema(description = "质量数据解析结果")
    private Object parsedQualityData;

    @Schema(description = "操作员头像")
    private String operatorAvatar;

    @Schema(description = "操作员部门")
    private String operatorDepartment;
}
