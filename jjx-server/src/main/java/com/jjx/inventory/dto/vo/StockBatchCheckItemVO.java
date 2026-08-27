package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 库存批量校验结果项（DEV-697：模式③批量校验导入）
 */
@Data
public class StockBatchCheckItemVO {

    /** 行号（对应请求的 rowIndex） */
    private Integer rowIndex;

    /** 校验状态：ok / error */
    private String status;

    /** 物料ID（查到物料时回填） */
    private Long materialId;

    /** 物料编码（查到物料时回填） */
    private String materialCode;

    /** 字段级错误列表（status=error 时填充） */
    private List<FieldError> errors = new ArrayList<>();

    /** 错误类型快捷标记：NOT_FOUND(物料未建档) / WAREHOUSE_NOT_FOUND / INVALID / MISSING_REQUIRED */
    private String errorType;

    @Data
    public static class FieldError {
        /** 字段名：materialName / specification / quantity / warehouseName / locationDesc */
        private String field;
        /** 错误类型 */
        private String type;
        /** 错误描述 */
        private String message;
    }
}
