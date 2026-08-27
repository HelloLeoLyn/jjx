package com.jjx.sales.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新样品单 DTO（驳回后编辑：仅 CREATED 状态可编辑）
 *
 * 可编辑白名单：客户、联系人/电话、期望交样日期、技术要求、备注、产品明细。
 * 锁定字段（不可修改）：样品单号、来源报价单关联、状态、审核及工程字段、创建信息。
 * 明细采用事务内全量替换（至少保留一条，数量必须有效）。
 */
@Data
public class SampleOrderUpdateDTO {

    /** 客户ID（必填） */
    @NotNull(message = "客户不能为空")
    private Long customerId;

    /** 产品明细（必填，至少一条） */
    @Valid
    @NotNull(message = "产品明细不能为空")
    private List<Item> items;

    /** 期望交样日期 yyyy-MM-dd（可选） */
    private String deliveryDate;

    /** 联系人（可选） */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 技术要求（工程打样要求，写入工程备注） */
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
