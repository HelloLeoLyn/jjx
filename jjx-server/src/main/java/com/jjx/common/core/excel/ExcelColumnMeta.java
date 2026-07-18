package com.jjx.common.core.excel;

import lombok.AllArgsConstructor;
import lombok.Data;



/**
 * Excel 列元数据
 */
@Data
@AllArgsConstructor
public class ExcelColumnMeta {
    private String headerName;      // 表头名称
    private String fieldName;       // 字段名
    private int order;              // 排序
    private boolean required;       // 是否必填
    private String comment;         // 说明
    private Class<?> fieldType;     // 字段类型
}