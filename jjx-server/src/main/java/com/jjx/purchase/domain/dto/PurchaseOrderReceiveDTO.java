package com.jjx.purchase.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购订单收货数据传输对象
 * 支持批量收货，一个订单可同时收货多个明细项
 */
@Data
public class PurchaseOrderReceiveDTO {

    /**
     * 采购订单ID
     * 注：由接口路径 @PathVariable 注入（POST /purchase/order/{orderId}/receive），
     * 请求体无需携带；@NotNull 移除（否则 @Valid 校验早于 setOrderId 必失败），
     * 空值兜底由 service 层校验。
     */
    private Long orderId;

    /**
     * 收货明细列表
     */
    @NotEmpty(message = "收货明细不能为空")
    @Valid
    private List<ReceiveItemDTO> items;

    /**
     * 收货明细项
     */
    @Data
    public static class ReceiveItemDTO {

        /**
         * 订单明细ID
         */
        @NotNull(message = "明细ID不能为空")
        private Long itemId;

        /**
         * 本次收货数量
         */
        @NotNull(message = "收货数量不能为空")
        @Positive(message = "收货数量必须大于0")
        private BigDecimal receivedQuantity;

    }
}
