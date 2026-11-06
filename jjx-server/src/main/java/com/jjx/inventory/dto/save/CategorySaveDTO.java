package com.jjx.inventory.dto.save;

import lombok.Data;

/**
 * 物料分类新增参数DTO
 */
@Data
public class CategorySaveDTO {

    private String categoryCode;
    /** 型号前缀，无启用子类的叶子保存时必填（2～8位大写字母或数字）；父级可空，非空须唯一。 */
    private String modelPrefix;
    private String categoryName;
    private Long parentId;
    private Integer sortOrder;
    private String status;
    private String remark;
}
