package com.jjx.production.domain.dto;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class QualityTemplateQueryDTO extends PageQuery {
    private String keyword;
    private String recordNo;
    private String recordName;
    private String ownerDept;
    private String category;
    private Integer status;
}
