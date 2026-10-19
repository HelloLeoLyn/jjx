package com.jjx.sales.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 报价预填快照与订单表单；只在正式保存时执行转换。 */
@Data
public class QuotationOrderDraftDTO {
    @NotBlank private String sourceVersion;
    @Valid @NotNull private SalesOrderAddDTO order;

    /** 报价专用证据（目标来源不支持）未带入订单的数量（dev-20261019-001） */
    private Integer omittedAttachmentCount;
    /** 未带入的类型名称（去重） */
    private java.util.List<String> omittedAttachmentTypes;
}
