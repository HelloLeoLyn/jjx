package com.jjx.product.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 制造 BOM 方案保存 DTO（dev-20261111-004）。
 * 一种产出、多行投入；产出身份 PRODUCT/MATERIAL 显式表达；不自动发布、不改父 BOM。
 */
@Data
public class ManufacturingBomSaveDTO {

    private Long bomId;

    @NotBlank(message = "BOM编码不能为空")
    private String bomCode;

    @NotBlank(message = "BOM名称不能为空")
    private String bomName;

    /** 为空时由服务端按同输出对象的历史版本递增。 */
    private String bomVersion;

    @NotBlank(message = "产出类型不能为空")
    private String outputType;

    @NotNull(message = "产出不能为空")
    private Long outputId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date effectiveDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date expiryDate;

    private String remark;

    @NotEmpty(message = "制造BOM至少需要一行投入材料")
    @Size(max = 500, message = "制造BOM投入行不能超过500行")
    @Valid
    private List<EngineeringBomItemDTO> items;
}
