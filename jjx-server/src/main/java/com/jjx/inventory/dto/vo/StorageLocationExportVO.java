package com.jjx.inventory.dto.vo;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库位导出视图对象（dev-20260911-001）
 */
@Data
public class StorageLocationExportVO {

    @ExcelColumn(value = "库位编码", order = 1)
    private String locationCode;

    @ExcelColumn(value = "库位名称", order = 2)
    private String locationName;

    @ExcelColumn(value = "所属仓库", order = 3)
    private String warehouseName;

    @ExcelColumn(value = "库位类型", order = 4)
    private String locationTypeDesc;

    @ExcelColumn(value = "最大容量", order = 5)
    private BigDecimal capacity;

    @ExcelColumn(value = "已用容量", order = 6)
    private BigDecimal usedCapacity;

    @ExcelColumn(value = "宽(cm)", order = 7)
    private BigDecimal width;

    @ExcelColumn(value = "高(cm)", order = 8)
    private BigDecimal height;

    @ExcelColumn(value = "深(cm)", order = 9)
    private BigDecimal depth;

    @ExcelColumn(value = "排序号", order = 10)
    private Integer sortOrder;

    @ExcelColumn(value = "状态", order = 11)
    private String statusDesc;

    @ExcelColumn(value = "备注", order = 12)
    private String remark;

    @ExcelColumn(value = "创建时间", order = 13)
    private String createTime;
}
