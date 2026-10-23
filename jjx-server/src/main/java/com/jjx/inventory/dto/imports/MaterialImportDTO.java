package com.jjx.inventory.dto.imports;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 物料导入DTO（dev-20261023-019：对齐新主数据规则）。
 *
 * 说明：
 * 1）历史「数量」列已从模板删除——物料导入不写库存数量，库存变动必须走库存业务单据
 *    （InventoryStockMutationService），避免绕过流水直接改库存。
 * 2）别名/标准单价/采购周期/安全-最高-再订货点/保质期/预警天数/批次控制均已接入导入。
 */
@Data
public class MaterialImportDTO {

    @ExcelColumn(value = "物料名称", order = 1, comment = "填写名称；留空时按 材料+型号+颜色（品牌）自动生成；旧模板材料列仍可作名称")
    private String materialName;

    @ExcelColumn(value = "规格", order = 2, comment = "工艺/材质描述（砂面、克重、离型力等）；是否必填按品类矩阵，尺寸请填【尺寸】列")
    private String specification;

    @ExcelColumn(value = "供应商", order = 3, comment = "主要供应商名称，可空；未匹配档案不阻断导入")
    private String supplierName;

    @ExcelColumn(value = "备注", order = 4, comment = "备注信息")
    private String remark;

    @ExcelColumn(value = "机种", order = 5, comment = "机种名称")
    private String materialNameEn;

    @ExcelColumn(value = "单位", order = 6, comment = "计量单位，如PCS、卷，可空")
    private String unit;

    @ExcelColumn(value = "项目", order = 7, comment = "工序项目编码或中文名称；先按编码匹配，再按中文名称匹配，空值或未匹配时归为其他类型物料（O）")
    private String processGroup;

    @ExcelColumn(value = "材料类型", order = 8, comment = "历史兼容列；最终材料类型由品类带出，缺省品类时用于其他原料/辅料兜底")
    private String materialType;

    @ExcelColumn(value = "材料标签", order = 9, comment = "多个用斜杠分隔；支持 大类*明细 两级，自动建档")
    private String tagTexts;

    @ExcelColumn(value = "品类", order = 10, comment = "品类编码或完整名称；必填，缺省时系统按名称推断")
    private String categoryName;

    @ExcelColumn(value = "材料", order = 11, comment = "结构化材料，参考23词词表；按品类条件必填，旧模板此列仍可为物料名称")
    private String materialGrade;

    @ExcelColumn(value = "型号", order = 12, comment = "材料型号；按品类条件必填")
    private String model;

    @ExcelColumn(value = "颜色", order = 13, comment = "参考13色词表；按品类条件必填")
    private String color;

    @ExcelColumn(value = "品牌", order = 14, comment = "品牌名称；按品类条件必填")
    private String brand;

    @ExcelColumn(value = "尺寸", order = 15, comment = "宽×长/厚，缺省mm，可写cm/m/inch；也参与重复判定")
    private String size;

    @ExcelColumn(value = "别名", order = 16, comment = "常用叫法/旧料号，参与搜索")
    private String searchKeywords;

    @ExcelColumn(value = "标准单价", order = 17, comment = "数字，可空；负数不允许")
    private String standardPrice;

    @ExcelColumn(value = "采购周期(天)", order = 18, comment = "整数，可空")
    private String leadTime;

    @ExcelColumn(value = "安全库存", order = 19, comment = "数字，可空，默认0")
    private String safeStock;

    @ExcelColumn(value = "最高库存", order = 20, comment = "数字，可空，默认0")
    private String maxStock;

    @ExcelColumn(value = "再订货点", order = 21, comment = "数字，可空，默认0")
    private String reorderPoint;

    @ExcelColumn(value = "保质期(天)", order = 22, comment = "整数，可空")
    private String shelfLife;

    @ExcelColumn(value = "预警提前天数", order = 23, comment = "整数，可空，默认30")
    private String expiryAlertDays;

    @ExcelColumn(value = "批次控制", order = 24, comment = "是/否（也接受 true/false/1/0/启用/禁用），可空默认否")
    private String batchControl;
}
