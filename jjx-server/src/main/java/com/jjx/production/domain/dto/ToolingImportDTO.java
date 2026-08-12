package com.jjx.production.domain.dto;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 工装模具导入DTO（Excel 导入/导出共用）
 * 列设计（2026-08-12）：
 * 编号 | 名称(*) | 类型(*) | 参数 | 设计寿命 | 存放位置 | 客户 | 责任人 | 启用日期 | 备注
 * 类型列：网框/刀模（或 SCREEN/DIE）
 * 注意：ExcelUtils 不支持 LocalDate，启用日期用 String(yyyy-MM-dd) 接收后手动解析
 */
@Data
public class ToolingImportDTO {

    @ExcelColumn(value = "编号", order = 1, comment = "留空则自动生成")
    private String toolingNo;

    @ExcelColumn(value = "名称", order = 2, required = true, comment = "工装名称")
    private String toolingName;

    @ExcelColumn(value = "类型", order = 3, required = true, comment = "网框/刀模")
    private String toolingType;

    @ExcelColumn(value = "参数", order = 4, comment = "如：材质：xxx\n尺寸：xxx，长度512")
    private String spec;

    @ExcelColumn(value = "设计寿命", order = 5, comment = "刀模：冲切次数上限")
    private Integer lifeLimit;

    @ExcelColumn(value = "存放位置", order = 6, comment = "货架/柜号")
    private String location;

    @ExcelColumn(value = "客户", order = 7, comment = "定制工装所属客户")
    private String customer;

    @ExcelColumn(value = "责任人", order = 8)
    private String responsible;

    @ExcelColumn(value = "启用日期", order = 9, comment = "格式 yyyy-MM-dd")
    private String enableDate;

    @ExcelColumn(value = "备注", order = 10)
    private String remark;
}
