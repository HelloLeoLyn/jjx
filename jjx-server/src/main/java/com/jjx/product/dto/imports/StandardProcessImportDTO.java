package com.jjx.product.dto.imports;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 标准工序导入DTO
 */
@Data
public class StandardProcessImportDTO {

    @ExcelColumn(value = "工序编码", order = 1, required = false, comment = "可留空：系统按工序类别自动生成 SP-<段位><序号>（段位 1面板/2上线/3下线/4其他）；填写时必须符合同一规则且段位与类别一致")
    private String processCode;

    @ExcelColumn(value = "工序名称", order = 2, required = true, comment = "如 丝印、模切、贴合")
    private String processName;

    @ExcelColumn(value = "工序类型", order = 3, comment = "见字典 process_type：PRINTING印刷/PUNCH_HOLE冲孔/PUNCH_SHAPE冲型/LAMINATING贴合/CUTTING裁切/GASKET垫片/PROTECTIVE_FILM保护膜/SPACER隔片/CLEANING清洁/FILM_APPLY贴膜/FILM_REMOVE撕膜/RESISTOR电阻/CONNECTOR连接器/QC品检/PANEL面板/UP_LINE上线/DOWN_LINE下线/OTHER其他")
    private String processType;

    @ExcelColumn(value = "工序类别", order = 4, comment = "PANEL面板/UP_LINE上线/DOWN_LINE下线/OTHER其他（决定工序编码段位）")
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
