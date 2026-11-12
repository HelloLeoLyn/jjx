package com.jjx.product.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * BOM 明细行级审核操作（dev-20261020-029）。
 * action：APPROVE 行通过 / REJECT 行驳回 / CONFIRM_REMOVE 确认待移除 /
 *         LINK_MATERIAL 关联已有物料。
 */
@Data
public class BomItemReviewDTO {

    @NotNull(message = "明细ID不能为空")
    private Long itemId;

    @NotBlank(message = "审核动作不能为空")
    private String action;

    /** 驳回原因（REJECT 必填） */
    private String remark;

    /** 关联物料ID（LINK_MATERIAL 必填） */
    private Long materialId;

    /**
     * 行审核时由详情带回的服务端内容指纹（dev-20261111-018）。
     * 加载后被保存改动的行判失败返回 409，不用锁前旧实体覆盖新内容。
     */
    private String expectedContentRevision;
}
