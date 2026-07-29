package com.jjx.sales.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.common.annotation.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售订单添加DTO
 */
@Data
@Schema(description = "销售订单添加DTO")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesOrderAddDTO {

    @Schema(description = "报价单ID", example = "1001")
    private Long quotationId;

    @NotNull(message = "客户ID不能为空")
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long customerId;

    @NotNull(message = "订单编号不能为空")
    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "SO260526015")
    private String traceId;
    private String orderNo;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 100, message = "客户名称长度不能超过100个字符")
    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX科技有限公司")
    private String customerName;

    @Size(max = 50, message = "联系人长度不能超过50个字符")
    @Schema(description = "联系人", example = "张三")
    private String contactPerson;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    @Schema(description = "联系电话", example = "13800138000")
    private String contactPhone;

    @NotNull(message = "订单日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "订单日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-04-19")
    private Date orderDate;

    @NotNull(message = "客户要求交货日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "客户要求交货日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-05-19")
    private Date deliveryDate;

    @NotNull(message = "订单类型不能为空")
    @Schema(description = "订单类型", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"1", "2"}, example = "1")
    private Integer orderType;

    @Schema(description = "是否急单", allowableValues = {"0", "1"}, example = "0")
    private Integer isUrgent = 0;

    @Size(max = 200, message = "加急原因长度不能超过200个字符")
    @Schema(description = "加急原因", example = "客户项目紧急")
    private String urgentReason;

    @NotBlank(message = "币种不能为空")
    @Size(max = 10, message = "币种长度不能超过10个字符")
    @Schema(description = "币种", requiredMode = Schema.RequiredMode.REQUIRED, example = "CNY")
    private String currency;

    @NotNull(message = "汇率不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "汇率必须大于0")
    @Schema(description = "汇率", requiredMode = Schema.RequiredMode.REQUIRED, example = "1.0000")
    private BigDecimal exchangeRate;

    @Size(max = 100, message = "付款条件长度不能超过100个字符")
    @Schema(description = "付款条件", example = "30%预付款，70%发货前付清")
    private String paymentTerms;

    @Size(max = 100, message = "交货条件长度不能超过100个字符")
    @Schema(description = "交货条件", example = "FOB Shanghai")
    private String deliveryTerms;

    @Size(max = 200, message = "交货地址长度不能超过200个字符")
    @Schema(description = "交货地址", example = "上海市浦东新区XX路XX号")
    private String deliveryAddress;

    @NotNull(message = "总金额不能为空")
    @DecimalMin(value = "0", message = "总金额不能为负数")
    @Schema(description = "总金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "10000.00")
    private BigDecimal totalAmount;

    @DecimalMin(value = "0", message = "税率不能为负数")
    @DecimalMax(value = "100", message = "税率不能大于100")
    @Schema(description = "税率", example = "13")
    private BigDecimal taxRate = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "折扣率不能为负数")
    @DecimalMax(value = "1", message = "折扣率不能大于1")
    @Schema(description = "折扣率", example = "0.05")
    private BigDecimal discountRate = BigDecimal.ZERO;

    @NotNull(message = "总数量不能为空")
    @Min(value = 1, message = "总数量至少为1")
    @Schema(description = "总数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Integer totalQuantity;

    @NotNull(message = "销售负责人ID不能为空")
    @Schema(description = "销售负责人ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long salesManagerId;

    @NotBlank(message = "销售负责人姓名不能为空")
    @Size(max = 50, message = "销售负责人姓名长度不能超过50个字符")
    @Schema(description = "销售负责人姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
    private String salesManagerName;

    @Size(max = 500, message = "备注长度不能超过500个字符")
    @Schema(description = "备注", example = "客户要求加急处理")
    private String remark;

    @NotEmpty(message = "产品明细ID不能为空", groups = ValidationGroups.Add.class)
    private List<SalesOrderProductDTO> items;

}
