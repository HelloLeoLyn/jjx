package com.jjx.product.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.product.domain.group.BomGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * BOM新增/编辑DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductBomDTO {

    /** BOM ID（编辑时必填，新增时不填） */
    private Long bomId;

    @NotBlank(message = "BOM编码不能为空")
    private String bomCode;

    /** BOM名称 */
    @NotBlank(message = "BOM名称不能为空")
    private String bomName;

    /** BOM版本 */
    @NotBlank(message = "BOM版本不能为空",groups = BomGroup.Version.class)
    private String bomVersion;

    /** 产品ID */
    @NotNull(message = "产品不能为空")
    private Long productId;

    /** 产品编码（冗余） */
    private String productCode;

    /** 产品名称（冗余） */
    private String productName;

    /** 审核状态 */
    private Integer approveStatus;

    /** 是否当前版本 */
    private Boolean isCurrent;

    /** 生效日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date effectiveDate;

    /** 失效日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date expiryDate;

    /** 备注 */
    private String remark;

    /** BOM明细列表 */
    @NotNull(message = "BOM明细不能为空")
    @Size(min = 1, message = "BOM明细至少需要一条")
    @Valid
    private List<ProductBomItemDTO> items;
}
