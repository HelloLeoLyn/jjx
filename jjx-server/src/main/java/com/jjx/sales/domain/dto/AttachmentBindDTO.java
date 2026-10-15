package com.jjx.sales.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 随单据保存一起提交的附件（dev-20261015-034）。
 * 目标：保存成功即代表附件已挂到本单；缺资料类型则拒绝保存，不留中间态。
 */
@Data
public class AttachmentBindDTO {
    @Schema(description = "附件ID（上传返回的临时件ID）")
    private Long attachmentId;

    @Schema(description = "二级资料类型编码（必选；缺则拒绝保存）")
    private String documentType;
}
