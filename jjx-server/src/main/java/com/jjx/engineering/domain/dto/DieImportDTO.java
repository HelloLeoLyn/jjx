package com.jjx.engineering.domain.dto;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 刀模导入 DTO（2026-09-21，刀模 Excel 导入功能）。
 *
 * <p>对应导入模板「刀模导入模板」的列；模板由 {@code ExcelUtils.downloadTemplate} 生成，
 * 表头即 {@link ExcelColumn#value()}。导入按刀模编号 upsert（存在则更新，不脱档）。</p>
 */
@Data
public class DieImportDTO {

    /** 刀模编号（唯一） */
    @ExcelColumn(value = "刀模编号", order = 1, required = true, comment = "如 JST-412，格式 字母-序号，重复则更新该刀模")
    private String dieNo;

    /** 刀模名称（空则取刀模编号） */
    @ExcelColumn(value = "刀模名称", order = 2, comment = "留空时自动用刀模编号填充")
    private String dieName;

    /** 用途 */
    @ExcelColumn(value = "用途", order = 3, comment = "如 面板外形刀 / 隔片刀 / 凹凸模")
    private String purpose;

    /** 规格参数 */
    @ExcelColumn(value = "规格参数", order = 4, comment = "如 300×200")
    private String specification;

    /** 版本 */
    @ExcelColumn(value = "版本", order = 5, comment = "如 V1，可留空")
    private String version;

    /** 数量（把） */
    @ExcelColumn(value = "数量(把)", order = 6, comment = "该刀模套装内刀模把数，可留空")
    private Integer quantity;

    /** 存放位置（多个库位用「；」分隔） */
    @ExcelColumn(value = "存放位置", order = 7, comment = "多个库位用「；」分隔，如 G-Fc2-004；G-Fc2-005")
    private String location;

    /** 入库日期 */
    @ExcelColumn(value = "入库日期", order = 8, comment = "格式 2013-10-16，可留空")
    private String stockInDate;

    /** 状态（可用/维护中/停用/报废/已重做，留空默认可用） */
    @ExcelColumn(value = "状态", order = 9, comment = "可用/维护中/停用/已重做/报废，留空默认 可用")
    private String status;

    /** 共用刀模号（写入备注） */
    @ExcelColumn(value = "共用刀模号", order = 10, comment = "与哪个刀模共用，如 JST-410，可留空；会写入备注")
    private String shareDieNo;

    /** 备注 */
    @ExcelColumn(value = "备注", order = 11, comment = "≤500 字，超长会截断")
    private String remark;
}
