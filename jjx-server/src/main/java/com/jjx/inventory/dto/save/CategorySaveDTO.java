package com.jjx.inventory.dto.save;

import lombok.Data;

/**
 * 物料分类新增参数DTO
 */
@Data
public class CategorySaveDTO {

    private String categoryCode;
    private String categoryName;
    private Long parentId;
    private Integer sortOrder;
    private String status;
    private String remark;
}
