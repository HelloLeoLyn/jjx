package com.jjx.production.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单换版执行区间展示（dev-20261011-013）
 */
@Data
@Schema(description = "工单换版执行区间")
public class WorkSpecUsageVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "生产工单ID")
    private Long workOrderId;

    @Schema(description = "作业规范发布版本ID")
    private Long specVersionId;

    @Schema(description = "作业规范版本号，如 V1.0")
    private String versionNo;

    @Schema(description = "版本状态 PUBLISHED/RETIRED")
    private String versionStatus;

    @Schema(description = "生效数量区间-起（含）")
    private BigDecimal qtyFrom;

    @Schema(description = "生效数量区间-止（含）")
    private BigDecimal qtyTo;

    @Schema(description = "生效开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "生效结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "换版原因")
    private String changeReason;

    @Schema(description = "批准人")
    private String approvedBy;

    @Schema(description = "批准时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    @Schema(description = "登记人账号")
    private String createBy;

    @Schema(description = "登记时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
