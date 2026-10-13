package com.jjx.system.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AttachmentRefDTO {
    @NotNull @Positive
    private Long attachmentId;
    @NotBlank @Size(max = 50)
    private String sourceType;
    @NotNull @Positive
    private Long sourceId;
    @Positive
    private Long productId;
    /** 行级附件归属：产品编码（内部字段；不建产品档案。S5 dev-20261013-043） */
    @Size(max = 64)
    private String productCode;
    @Size(max = 100)
    private String documentType;
    @Size(max = 255)
    private String displayName;
    @Size(max = 500)
    private String remark;
    @Min(0)
    private Integer sortOrder;
}
