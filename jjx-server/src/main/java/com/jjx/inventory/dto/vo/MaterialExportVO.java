package com.jjx.inventory.dto.vo;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料档案导出视图对象（dev-20260911-001）
 */
@Data
public class MaterialExportVO {

    @ExcelColumn(value = "物料编码", order = 1)
    private String materialCode;

    @ExcelColumn(value = "物料名称", order = 2)
    private String materialName;

    @ExcelColumn(value = "机种", order = 3)
    private String materialNameEn;

    @ExcelColumn(value = "物料类型", order = 4)
    private String materialTypeDesc;

    @ExcelColumn(value = "物料分类", order = 5)
    private String categoryName;

    @ExcelColumn(value = "规格型号", order = 6)
    private String specification;

    @ExcelColumn(value = "计量单位", order = 7)
    private String unit;

    @ExcelColumn(value = "安全库存", order = 8)
    private BigDecimal safeStock;

    @ExcelColumn(value = "最高库存", order = 9)
    private BigDecimal maxStock;

    @ExcelColumn(value = "再订货点", order = 10)
    private BigDecimal reorderPoint;

    @ExcelColumn(value = "标准采购单价", order = 11)
    private BigDecimal standardPrice;

    @ExcelColumn(value = "采购提前期(天)", order = 12)
    private Integer leadTime;

    @ExcelColumn(value = "主要供应商", order = 13)
    private String supplierName;

    @ExcelColumn(value = "默认仓库", order = 14)
    private String defaultWarehouseName;

    @ExcelColumn(value = "状态", order = 15)
    private String statusDesc;

    @ExcelColumn(value = "批次管理", order = 16)
    private String batchControlDesc;

    @ExcelColumn(value = "备注", order = 17)
    private String remark;

    @ExcelColumn(value = "创建时间", order = 18)
    private String createTime;
}
