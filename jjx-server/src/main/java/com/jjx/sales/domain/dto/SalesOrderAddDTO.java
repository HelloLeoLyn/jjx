package com.jjx.sales.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.common.annotation.ValidationGroups;
import com.jjx.common.validation.ValidContactPhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

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

    @Schema(description = "链路追踪ID（可选，2026-08-11 修正：原误标必填）")
    private String traceId;

    @Schema(description = "订单编号（可空：为空时后端提交落库自动生成，2026-09-07）", example = "SO260907004")
    private String orderNo;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 100, message = "客户名称长度不能超过100个字符")
    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX科技有限公司")
    private String customerName;

    @Size(max = 50, message = "联系人长度不能超过50个字符")
    @Schema(description = "联系人", example = "张三")
    private String contactPerson;

    // dev-20261018-026：可选、≤50 字符、字符白名单（不做手机号/座机格式判断），规则仅见 ContactPhoneRule
    @ValidContactPhone
    @Schema(description = "联系电话（可选，最长50字符，可含数字、+、-、空格、括号、#）", example = "+86 13800000000")
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

    /** 运费；NULL 表示历史金额组成尚未确认，禁止推算补齐。 */
    @DecimalMin(value = "0", message = "运费不能为负数")
    private BigDecimal shippingFee;

    @DecimalMin(value = "0", message = "税率不能为负数")
    @DecimalMax(value = "100", message = "税率不能大于100")
    @Schema(description = "税率", example = "13")
    private BigDecimal taxRate = BigDecimal.ZERO;
    private BigDecimal taxAmount;

    @DecimalMin(value = "0", message = "折扣金额不能为负数")
    @Schema(description = "折扣金额", example = "100.00")
    private BigDecimal discountAmount;

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

    /**
     * 随订单保存提交的附件（dev-20261017-001，照报价侧 dev-20261016-064 范式）。
     * 仅承载入参，落定（建引用 / 草稿行转正）由保存流程在同一事务内处理，DTO 不落库。
     */
    @Schema(description = "订单附件（可选，支持整单附件及产品附件）")
    private List<OrderAttachmentDTO> attachments;

    /**
     * 随单附件入参载体（dev-20261017-001）：复用 {@link AttachmentBindDTO} 的
     * attachmentId / documentType（必填），另带可选的 productId / productCode 作为归属关联键。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class OrderAttachmentDTO extends AttachmentBindDTO {
        @Schema(description = "产品ID（可选，附件归属关联键）")
        private Long productId;

        @Schema(description = "产品编码（可选，未建档产品的上下文信息）")
        private String productCode;
    }

}
