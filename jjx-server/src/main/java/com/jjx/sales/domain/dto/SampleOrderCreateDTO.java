package com.jjx.sales.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 新增样品单 DTO（直接选客户+产品，报价单为可选来源）
 */
@Data
public class SampleOrderCreateDTO {

    /** 客户ID（必填） */
    @NotNull(message = "客户不能为空")
    private Long customerId;

    /** 来源报价单ID（可选，提供则带出明细并回写报价单状态） */
    private Long quotationId;

    /** 产品明细（可选；带报价单且为空时从报价单复制） */
    @Valid
    private List<Item> items;

    /** 期望交样日期 yyyy-MM-dd（可选，默认继承报价单交期） */
    private String deliveryDate;

    /** 联系人（可选，默认带出客户/报价单联系人） */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 技术要求（工程打样要求，传承打样工作台） */
    private String techRequirement;

    /** 备注 */
    private String remark;

    @Data
    public static class Item {
        private Long productId;

        @NotBlank(message = "产品编码不能为空")
        private String productCode;

        @NotBlank(message = "产品名称不能为空")
        private String productName;

        @NotNull(message = "产品数量不能为空")
        private Integer quantity;

        private String unit;
    }
}
