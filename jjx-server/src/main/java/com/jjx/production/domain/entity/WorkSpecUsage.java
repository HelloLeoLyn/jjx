package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单换版执行区间记录（dev-20261011-013）
 * 一张工单分段执行多个发布版本时，逐段记录实际执行依据；
 * 当前执行版本仍是 production_order.work_spec_version_id，历史段落留本表。
 */
@Getter
@Setter
@TableName("work_spec_usage")
@Schema(description = "工单换版执行区间记录")
public class WorkSpecUsage {

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "生产工单ID")
    private Long workOrderId;

    @Schema(description = "该区间实际使用的作业规范发布版本ID")
    private Long specVersionId;

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

    @Schema(description = "批准人（显示名）")
    private String approvedBy;

    @Schema(description = "批准时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    @Schema(description = "登记人账号")
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private String createBy;

    @Schema(description = "登记时间")
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "逻辑删除：0正常/1删除")
    @TableLogic
    private Integer deleted;
}
