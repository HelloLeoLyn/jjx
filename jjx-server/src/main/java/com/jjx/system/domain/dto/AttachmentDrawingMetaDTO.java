package com.jjx.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 公共附件上传可选的图纸元数据（dev-20261014-029，019 方案B）。
 *
 * <p>字段全部可选：不传 = 不写对应列，行为与改造前一致；传入时写入 {@code sys_attachment} 的既有图纸列。
 * 保留附件表列（不新增表、不把图纸列搬到引用表），使工程图纸经公共组件上传时元数据不丢。
 * 图纸库现有读列口径（分类 tab / 版本分组 / 原稿-打印件唯一性 / 元数据编辑）不受影响。</p>
 */
@Data
@Schema(description = "附件可选图纸元数据（不传=不写这些列）")
public class AttachmentDrawingMetaDTO {

    @Schema(description = "图种/文件类别（写入 sys_attachment.category）")
    private String category;

    @Schema(description = "图纸编号（写入 sys_attachment.drawing_no，统一大写）")
    private String drawingNo;

    @Schema(description = "图纸名称（写入 sys_attachment.drawing_name）")
    private String drawingName;

    @Schema(description = "版本（写入 sys_attachment.version，统一大写）")
    private String version;

    @Schema(description = "文件用途：ORIGINAL=工程原稿，PRINT=预览打印件（写入 sys_attachment.file_role）")
    private String fileRole;
}
