package com.jjx.inventory.dto.vo;

import lombok.Data;

/** 物料建档用扁平品类选项。 */
@Data
public class MaterialCategoryOptionVO {
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private String rootType;
    private String categoryRootName;
}
