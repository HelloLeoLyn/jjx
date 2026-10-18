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
}
