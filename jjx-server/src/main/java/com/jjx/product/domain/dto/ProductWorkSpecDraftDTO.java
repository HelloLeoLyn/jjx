package com.jjx.product.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** 完整草稿附件集合；移除仅作用于当前草稿，不改变已定版内容。 */
@Data
public class ProductWorkSpecDraftDTO {
    @NotBlank @Size(max = 64) private String revision;
    private Long baseVersionId;
    private Long preparedById;
    @Valid private ProductWorkSpecDTO spec;
    @Valid @Size(max = 500) private List<File> attachments;

    @Data
    public static class File {
        @NotNull @Positive private Long attachmentId;
        @NotBlank @Size(max = 100) private String documentType;
        @Size(max = 255) private String displayName;
        @Size(max = 500) private String remark;
        private Boolean print;
    }
}
