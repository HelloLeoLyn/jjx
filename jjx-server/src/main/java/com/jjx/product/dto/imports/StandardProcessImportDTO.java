package com.jjx.product.dto.imports;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 标准工序导入DTO
 */
@Data
public class StandardProcessImportDTO {

    @ExcelColumn(value = "工序编码", order = 1, required = true, comment = "唯一编码，如 SP-001")
    private String processCode;

    @ExcelColumn(value = "工序名称", order = 2, required = true, comment = "如 丝印、模切、贴合")
    private String processName;

    @ExcelColumn(value = "工序类型", order = 3, comment = "MAIN_PAD面板/UP_LINE上线/DOWN_LINE下线/PRINTING印刷/CUTTING模切/LAMINATING贴合/TESTING测试/PACKAGING包装")
    private String processType;

    @ExcelColumn(value = "工序类别", order = 4, comment = "PREPARATION准备/MAIN主要/FINISHING后处理/QUALITY质量")
    private String processCategory;

    @ExcelColumn(value = "标准工时", order = 5, comment = "小时，数字")
    private String standardLaborHours;

    @ExcelColumn(value = "标准机时", order = 6, comment = "小时，数字")
    private String standardMachineHours;

    @ExcelColumn(value = "工艺参数模板", order = 7, comment = "如 温度:xxx℃;压力:xxx")
    private String processParamTemplate;

    @ExcelColumn(value = "技能要求", order = 8)
    private String skillRequirement;

    @ExcelColumn(value = "设备类型", order = 9)
    private String equipmentType;

    @ExcelColumn(value = "质量标准", order = 10)
    private String qualityStandard;

    @ExcelColumn(value = "描述", order = 11)
    private String description;

    @ExcelColumn(value = "排序", order = 12, comment = "数字，越小越靠前")
    private String displayOrder;

    @ExcelColumn(value = "启用", order = 13, comment = "1启用/0停用，默认1")
    private String isEnabled;
}
