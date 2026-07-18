package com.jjx.purchase.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购票据数据传输对象
 */
@Data
public class PurchaseDocumentDTO {

    /**
     * 票据ID（更新时使用）
     */
    private Long documentId;

    /**
     * 票据编号
     */
    @NotBlank(message = "票据编号不能为空")
    @Size(max = 50, message = "票据编号长度不能超过50个字符")
    private String documentNo;

    /**
     * 票据类型（invoice发票/receipt收据/contract合同/quotation报价单/delivery_note送货单/other其他）
     */
    @NotBlank(message = "票据类型不能为空")
    @Pattern(regexp = "^(invoice|receipt|contract|quotation|delivery_note|other)$",
             message = "票据类型不正确")
    private String documentType;

    /**
     * 关联的采购订单
     */
    @NotNull(message = "采购订单ID不能为空")
    private Long orderId;

    /**
     * 供应商ID
     */
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    /**
     * 票据日期
     */
    @NotNull(message = "票据日期不能为空")
    private LocalDate documentDate;

    /**
     * 票据金额
     */
    @NotNull(message = "票据金额不能为空")
    private BigDecimal documentAmount;

    /**
     * 币种
     */
    @NotBlank(message = "币种不能为空")
    @Size(max = 10, message = "币种长度不能超过10个字符")
    private String currency;

    /**
     * 票据状态（pending待处理/verified已核验/archived已归档）
     */
    @Pattern(regexp = "^(pending|verified|archived)$", message = "票据状态不正确")
    private String documentStatus;

    /**
     * 核验日期
     */
    private LocalDate verificationDate;

    /**
     * 文件名称
     */
    @Size(max = 200, message = "文件名称长度不能超过200个字符")
    private String fileName;

    /**
     * 文件URL
     */
    @Size(max = 500, message = "文件URL长度不能超过500个字符")
    private String fileUrl;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
