package com.jjx.system.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 批量修改中的单条：只写入非 null 字段（部分更新），null 表示不改该条的这个字段。
 */
@Data
public class AttachmentRefBatchItem {
    @NotNull(message = "附件引用不能为空")
    private Long refId;

    @Size(max = 100)
    private String documentType;

    @Size(max = 255)
    private String displayName;

    @Size(max = 500)
    private String remark;

    @Min(value = 0, message = "排序号不能为负")
    private Integer sortOrder;
}
