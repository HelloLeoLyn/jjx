package com.jjx.sales.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.common.core.domain.BaseEditDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 询价单修改DTO
 * 前端整单提交可编辑字段，后端以数据库旧值为基准做权威字段级变更对比
 * （对齐订单模块 saveOrderUpdateChangeLog，2026-08-18 L3 模式）
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesInquiryEditDTO extends BaseEditDTO {

    @NotNull(message = "询价单ID不能为空")
    @Schema(description = "询价单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long inquiryId;

    @NotNull(message = "客户不能为空")
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "联系人")
    private String contactPerson;

    @Schema(description = "联系电话")
    private String contactPhone;

    @NotNull(message = "询价日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "询价日期", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate inquiryDate;

    @Schema(description = "期望数量")
    private Integer expectedQuantity;

    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @Schema(description = "询价类型")
    private Integer inquiryType;

    @Schema(description = "产品ID")
    private Long productId;

    @Schema(description = "产品编码")
    private String productCode;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "产品描述")
    private String productDescription;

    @Schema(description = "特殊要求")
    private String specialRequirements;

    @Schema(description = "是否有图纸")
    private Integer hasDrawing;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "销售员ID")
    private Long salesPersonId;

    @Schema(description = "销售员名称")
    private String salesPersonName;

    @Schema(description = "本次修改会话内上传的附件ID")
    private List<Long> attachmentIds;
}
