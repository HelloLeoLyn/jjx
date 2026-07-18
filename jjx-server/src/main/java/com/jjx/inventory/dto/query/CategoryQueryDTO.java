package com.jjx.inventory.dto.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 物料分类查询参数DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CategoryQueryDTO extends Page<Object> {

    // private Long categoryId;
    private String categoryCode;
    private String categoryName;
    // private Long parentId;
    private String status;

}
