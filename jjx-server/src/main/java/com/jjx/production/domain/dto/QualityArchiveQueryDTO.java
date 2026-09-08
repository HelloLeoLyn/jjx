package com.jjx.production.domain.dto;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class QualityArchiveQueryDTO extends PageQuery {
    private Long templateId;
    private String ownerDept;
    private Boolean archived;
    private String expiryState;
    private String recordNo;
}
