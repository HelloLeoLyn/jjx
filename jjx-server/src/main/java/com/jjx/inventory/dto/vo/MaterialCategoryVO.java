package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 物料分类视图对象VO
 */
@Data
public class MaterialCategoryVO {

    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private Long parentId;
    private String parentName;
    private Integer categoryLevel;
    private Integer sortOrder;
    private String status;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private List<MaterialCategoryVO> children;
}
