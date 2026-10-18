package com.jjx.sales.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 批量打样仅提交来源明细与数量，客户和产品资料由服务端带出。 */
@Data
public class SalesOrderBatchSampleDTO {
    @NotBlank(message = "请刷新订单打样资料后重试")
    private String sourceVersion;
    private LocalDate deliveryDate;
    @Size(max = 500, message = "打样要求不能超过500字")
    private String techRequirement;
    @Valid
    @NotEmpty(message = "请至少勾选一个打样产品")
    private List<@NotNull Item> items;

    @Data
    public static class Item {
        @NotNull @Positive
        private Long itemId;
        @NotNull(message = "请填写打样数量")
        @DecimalMin(value = "1", message = "打样数量必须为正整数")
        @DecimalMax(value = "2147483647", message = "打样数量超出允许范围")
        @Digits(integer = 10, fraction = 0, message = "打样数量必须为正整数")
        private BigDecimal quantity;
        @Size(max = 500, message = "产品备注不能超过500字")
        private String remark;
    }
}
