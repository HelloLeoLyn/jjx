package com.jjx.engineering.domain.dto;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 网版（网框 + 当前版面）导入 DTO（2026-09-21，网版导入功能）。
 *
 * <p>一行 = 一个网框；「版面内容」非空时，同时把内容落到该网框的**当前版面**（status=ACTIVE）。
 * 导入按网框编号 upsert（表已有唯一索引 uk_screen_frame_no），重复导入不会产生重复网框。</p>
 */
@Data
public class ScreenFrameImportDTO {

    /** 网框编号（唯一，如 G0001） */
    @ExcelColumn(value = "网框编号", order = 1, required = true, comment = "如 G0001 / A0123，重复则更新该网框")
    private String frameNo;

    /** 框型（如 G / A / B / C / F / H，留空则取编号首字母） */
    @ExcelColumn(value = "框型", order = 2, comment = "G/A/B/C/F/H 等，留空自动取编号首字母")
    private String frameType;

    /** 目数 */
    @ExcelColumn(value = "目数", order = 3, comment = "如 300，可留空")
    private String mesh;

    /** 存放位置 */
    @ExcelColumn(value = "存放位置", order = 4, comment = "如 网版架A区，可留空")
    private String location;

    /** 状态（空框/已制版/维护中/报废；留空时：有版面内容=已制版，无内容=空框） */
    @ExcelColumn(value = "状态", order = 5, comment = "空框/已制版/维护中/报废，留空自动判定")
    private String status;

    /** 版面内容（非空时写入当前版面；可为空） */
    @ExcelColumn(value = "版面内容", order = 6, comment = "网版上印的内容，如「JST-464 反印覆银 JTT-056 反印导光」")
    private String content;

    /** 备注 */
    @ExcelColumn(value = "备注", order = 7, comment = "≤500 字")
    private String remark;
}
