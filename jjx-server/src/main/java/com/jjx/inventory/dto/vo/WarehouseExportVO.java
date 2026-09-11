package com.jjx.inventory.dto.vo;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 仓库档案导出视图对象（dev-20260911-001）
 */
@Data
public class WarehouseExportVO {

    @ExcelColumn(value = "仓库编码", order = 1)
    private String warehouseCode;

    @ExcelColumn(value = "仓库名称", order = 2)
    private String warehouseName;

    @ExcelColumn(value = "仓库类型", order = 3)
    private String warehouseTypeDesc;

    @ExcelColumn(value = "仓库位置", order = 4)
    private String location;

    @ExcelColumn(value = "负责人", order = 5)
    private String manager;

    @ExcelColumn(value = "联系电话", order = 6)
    private String contactPhone;

    @ExcelColumn(value = "排序号", order = 7)
    private Integer sortOrder;

    @ExcelColumn(value = "状态", order = 8)
    private String statusDesc;

    @ExcelColumn(value = "备注", order = 9)
    private String remark;

    @ExcelColumn(value = "创建时间", order = 10)
    private String createTime;
}
