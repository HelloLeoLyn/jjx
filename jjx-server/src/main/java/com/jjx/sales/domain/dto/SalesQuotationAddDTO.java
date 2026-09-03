package com.jjx.sales.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.sales.domain.entity.SalesQuotationItem;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesQuotationAddDTO {

   
    /** 链路追踪ID */
    @Schema(description = "链路追踪ID（可选，2026-08-11 修正：原误标必填）")
    private String traceId;

    /** 来源询价单号（非表字段，查询时按 traceId 关联填充） */
    @Schema(description = "来源询价单号（非表字段，查询)")
    private String sourceInquiryNo;

    /** 查询参数：来源询价单号（非表字段，按 traceId 子查询过滤） */
    @Schema(description = "查询参数：来源询价单号（非表字段，按 traceId 子查询过滤）")
    private String inquiryNo;

    /**
     * 报价单编号
     */
    @Schema(description = "报价单编号", example = "Q260526001")
    // @NotBlank(message = "报价单编号不能为空")
    @Size(max = 50, message = "报价单编号长度不能超过50个字符")
    private String quotationNo;

    /** 报价单类型: 1标准品 2样品 */
    @Schema(description = "报价单类型: 1标准品 2样品")
    @NotNull(message = "报价单类型不能为空")
    private Integer quotationType;

    /**
     * 客户ID
     */
    @Schema(description = "客户ID")
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    /**
     * 客户名称
     */
    @Schema(description = "客户名称")
    @NotBlank(message = "客户名称不能为空")
    @Size(max = 100, message = "客户名称长度不能超过100个字符")
    private String customerName;

    /**
     * 联系人
     */
    @Schema(description = "联系人")
    @Size(max = 50, message = "联系人长度不能超过50个字符")
    private String contactPerson;

    /**
     * 联系电话
     */
    @Schema(description = "联系电话")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    private String contactPhone;

    /**
     * 报价日期
     */
    @Schema(description = "报价日期")
    @NotNull(message = "报价日期不能为空")
    private LocalDate quotationDate;

    /**
     * 有效期至
     */
    @Schema(description = "有效期至")
    @NotNull(message = "有效期至不能为空")
    private LocalDate validUntil;

    /**
     * 币种
     */
    @Schema(description = "币种")
    @NotBlank(message = "币种不能为空")
    @Size(max = 10, message = "币种长度不能超过10个字符")
    private String currency;

    /**
     * 汇率
     */
    @Schema(description = "汇率")
    @NotNull(message = "汇率不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "汇率必须大于0")
    private BigDecimal exchangeRate;

    /**
     * 报价状态: 0草稿,1已发送,2已确认,3已拒绝,4已过期,5待审核,6已审核,8改单,9已完成
     */
    @Schema(description = "报价状态: 0草稿,1已发送,2已确认,3已拒绝,4已过期,5待审核,6已审核,8改单,9已完成")
    private Integer quotationStatus;

    /**
     * 小计金额
     */
    @Schema(description = "小计金额")
    private BigDecimal subtotalAmount;

    /**
     * 税率
     */
    @Schema(description = "税率")
    @DecimalMin(value = "0", message = "税率不能为负数")
    private BigDecimal taxRate;

    /**
     * 税额
     */
    @Schema(description = "税额")
    @DecimalMax(value = "100", message = "税率不能大于100")
    private BigDecimal taxAmount;

    /**
     * 总金额
     */
    @Schema(description = "总金额")
    @NotNull(message = "总金额不能为空")
    @DecimalMin(value = "0", message = "总金额不能为负数")
    private BigDecimal totalAmount;

    /**
     * 折扣金额
     */
    @Schema(description = "折扣金额")
    private BigDecimal discountAmount;

    /**
     * 最终金额
     */
    @Schema(description = "最终金额")
    private BigDecimal finalAmount;

    /**
     * 备注
     */
    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;


    /** 报价单明细（非表字段，保存/查询时处理） */
    @TableField(exist = false)
    @Schema(description = "报价单明细（非表字段，保存/查询时处理）")
    private List<SalesQuotationItem> items;

   
}
