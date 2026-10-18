package com.jjx.sales.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.common.annotation.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单产品明细添加DTO
 */
@Data
@Schema(description = "订单产品明细添加DTO")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesOrderProductDTO {
    /** 仅新产品草稿使用，随订单同事务建档；不得用于修改已有产品档案。 */
    @jakarta.validation.Valid
    private com.jjx.product.domain.dto.ProductDTO productDraft;
    /** 编辑时的原明细ID，仅用于保留采用版本。 */
    private Long id;
    @NotNull(message = "产品数量不能为空")
    @Min(value = 1, message = "产品数量至少为1")
    @Schema(description = "产品数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer quantity;

    @NotNull(message = "产品金额不能为空")
    @DecimalMin(value = "0", message = "产品金额不能为负数")
    @Schema(description = "产品金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000.00")
    private BigDecimal amount;

    @Schema(description = "订单ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull(message = "订单ID不能为空", groups = ValidationGroups.Update.class)
    private Long orderId;

    @Schema(description = "产品ID；为空按编码匹配或按订单客户新建开发中产品", example = "2001")
    private Long productId;

    @Schema(description = "单位", example = "PCS")
    @NotNull(message = "单位不能为空")
    private String unit;

    @DecimalMin(value = "0", message = "单价不能为负数")
    @Schema(description = "单价", example = "100.00")
    @NotNull(message = "单价不能为空")
    private BigDecimal unitPrice;

    @Size(max = 50, message = "产品编码长度不能超过50个字符")
    @Schema(description = "产品编码；已有ID可缺省，传值必须匹配档案，新产品必填", example = "P2024001")
    private String productCode;

    @Size(max = 20, message = "产品名称长度不能超过20个字符")
    @Schema(description = "产品名称；已有产品以档案为准，新产品必填", example = "薄膜开关")
    private String productName;

    @Schema(description = "备注")
    private String remark;

    @Size(max = 500, message = "规格描述长度不能超过500个字符")
    @Schema(description = "规格描述")
    private String specification;

    @Size(max = 100, message = "客户物料号长度不能超过100个字符")
    @Schema(description = "客户物料号")
    private String customerMaterialNo;

    @Size(max = 500, message = "行备注长度不能超过500个字符")
    @Schema(description = "行备注")
    private String lineRemark;

    // 与报价建档相同的编码参数，只写新产品 spec_json，不属于订单行持久字段。
    private String serialNo;
    private String panelType;
    private String panelFeature;
    private String circuitType;
    private String circuitFeature;

}
