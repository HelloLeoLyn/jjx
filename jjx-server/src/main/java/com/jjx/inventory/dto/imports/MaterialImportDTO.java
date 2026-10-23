package com.jjx.inventory.dto.imports;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 物料导入DTO
 */
@Data
public class MaterialImportDTO {

    @ExcelColumn(value = "物料名称", order = 1, comment = "填写物料名称；名称规则见填写说明，旧模板材料列仍可作名称")
    private String materialName;

    @ExcelColumn(value = "规格", order = 2, comment = "规格描述；是否必填按品类矩阵，尺寸可另列填写")
    private String specification;

    @ExcelColumn(value = "供应商", order = 3, comment = "主要供应商名称，可空；未匹配档案不阻断导入")
    private String supplierName;

    @ExcelColumn(value = "备注", order = 4, comment = "备注信息")
    private String remark;

    @ExcelColumn(value = "机种", order = 5,comment = "机种名称")
    private String materialNameEn;

    @ExcelColumn(value = "数量", order = 5,comment = "历史兼容列；本入口不写入库存数量，请通过库存业务办理")
    private String currentStock;

    @ExcelColumn(value = "单位", order = 6,comment = "计量单位，如PCS、卷，可空")
    private String unit;

    @ExcelColumn(value = "项目", order = 6,comment = "工序项目编码或中文名称；先按编码匹配，再按中文名称匹配，空值或未匹配时归为其他类型物料（O）")
    private String processGroup;

    @ExcelColumn(value = "材料类型", order = 6,comment = "历史兼容列；最终材料类型由品类带出，缺省品类时用于其他原料/辅料兜底")
    private String materialType;

    @ExcelColumn(value = "材料标签", order = 7, comment = "多个用斜杠分隔；支持 大类*明细 两级，自动建档")
    private String tagTexts;
    @ExcelColumn(value = "品类", order = 8, comment = "品类编码或完整名称；建议明确填写，缺省时系统按名称推断")
    private String categoryName;
    @ExcelColumn(value = "材料", order = 9, comment = "结构化材料，参考23词词表；按品类条件必填，旧模板此列仍可为物料名称")
    private String materialGrade;
    @ExcelColumn(value = "型号", order = 10, comment = "材料型号；按品类条件必填")
    private String model;
    @ExcelColumn(value = "颜色", order = 11, comment = "参考13色词表；按品类条件必填")
    private String color;
    @ExcelColumn(value = "品牌", order = 12, comment = "品牌名称；按品类条件必填")
    private String brand;
    @ExcelColumn(value = "尺寸", order = 13, comment = "宽×长/厚，缺省mm，可写cm/m/inch")
    private String size;
}
