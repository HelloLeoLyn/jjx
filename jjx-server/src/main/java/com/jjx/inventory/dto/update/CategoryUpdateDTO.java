package com.jjx.inventory.dto.update;

import lombok.Data;

/**
 * 物料分类更新参数DTO
 */
@Data
public class CategoryUpdateDTO {

    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private Long parentId;
    private Integer sortOrder;
    private String status;
    private String remark;
}
