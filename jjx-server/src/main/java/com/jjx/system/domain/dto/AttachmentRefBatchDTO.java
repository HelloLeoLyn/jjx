package com.jjx.system.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量修改附件引用元数据（PUT /system/attachment/refs/batch）。
 * <p>
 * 语义：**按行部分更新**——每条只写自己显式传入的非 null 字段，null 表示不改该字段
 * （所以"批量改类型"= 每行只带 documentType；"批量改名"= 每行带各自算好的 displayName）。
 * 与单条 PUT /refs/{refId}（全量替换、null=清空）**语义不同**，调用方需区分。
 */
@Data
public class AttachmentRefBatchDTO {
    @NotEmpty(message = "请选择要修改的附件")
    @Size(max = 500, message = "单次最多修改 500 条")
    @Valid
    private List<AttachmentRefBatchItem> items;
}
