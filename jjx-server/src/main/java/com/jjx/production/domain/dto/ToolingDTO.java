package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 工装模具新增/修改入参
 * 设计（2026-08-12）：编号/名称/类型/参数/设计寿命/存放位置/客户/责任人/启用日期/备注
 */
@Data
@Schema(description = "工装模具入参")
public class ToolingDTO {

    @Schema(description = "主键（修改时必填）")
    private Long toolingId;

    @Schema(description = "工装编号（唯一，可点生成编号）")
    @NotBlank(message = "工装编号不能为空")
    private String toolingNo;

    @Schema(description = "名称")
    @NotBlank(message = "工装名称不能为空")
    private String toolingName;

    @Schema(description = "类型：SCREEN=网框 DIE=刀模")
    @NotBlank(message = "工装类型不能为空")
    private String toolingType;

    @Schema(description = "参数（如：材质：xxx\n尺寸：xxx，长度512）")
    @Size(max = 512, message = "参数长度不能超过512个字符")
    private String spec;

    @Schema(description = "设计冲切寿命上限(次)，刀模用")
    private Integer lifeLimit;

    @Schema(description = "已冲切次数")
    private Integer currentCount;

    @Schema(description = "状态：0=在库 1=使用中 2=清洗/保养中 3=维修中 4=报废")
    @NotNull(message = "工装状态不能为空")
    private Integer status;
    @Schema(description = "存放位置")
    private String location;
    @Schema(description = "使用部门")
    private String department;
    @Schema(description = "责任人")
    private String responsible;
    @Schema(description = "客户（定制工装所属客户）")
    private String customer;
    @Schema(description = "启用日期")
    private LocalDate enableDate;
    @Schema(description = "备注")
    private String remark;
}
