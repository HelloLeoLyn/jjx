package com.jjx.product.domain.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** BOM 批量关联物料结果（dev-20261024-020）：逐行成功/失败，部分失败可定位重试。 */
@Data
public class BomMaterialBatchResultVO {

    private int total;
    private int successCount;
    private int failedCount;
    private List<Row> rows = new ArrayList<>();

    @Data
    public static class Row {
        private Long itemId;
        private boolean success;
        private String message;
        private Long materialId;
        private String materialCode;
    }
}
