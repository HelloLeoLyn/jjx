package com.jjx.system.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** PUT replaces all four editable fields; null explicitly clears optional metadata. */
@Data
public class AttachmentRefMetadataDTO {
    @Size(max = 100)
    private String documentType;
    @Size(max = 255)
    private String displayName;
    @Size(max = 500)
    private String remark;
    @Min(0)
    private Integer sortOrder;
}
