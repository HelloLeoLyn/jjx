package com.jjx.product.domain.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * BOM 批量关联物料入参（dev-20261024-020）。
 * 只做「审核中 BOM 的未关联行 → 已有物料档案」的受控关联，不自动审核通过。
 */
@Data
public class BomMaterialBatchLinkDTO {

    @NotNull(message = "BOM不能为空")
    private Long bomId;

    /** 逐行确认的关联项；同一明细不得重复出现 */
    private List<Link> links;

    @Data
    public static class Link {
        private Long itemId;
        private Long materialId;
    }
}
