package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 物料分类实体类
 * 对应表：inventory_material_category
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_material_category")
public class InventoryMaterialCategory extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 分类ID */
    @TableId(type = IdType.AUTO)
    private Long categoryId;

    /** 分类编码 */
    private String categoryCode;

    /** 分类名称 */
    private String categoryName;

    /** 父分类ID，0表示顶级分类 */
    private Long parentId;

    /** 层级：1一级/2二级/3三级 */
    private Integer categoryLevel;

    /** 分类路径，如：/1/2/3 */
    private String categoryPath;

    /** 排序序号 */
    private Integer sortOrder;

    /** 状态：0正常 1停用 */
    private String status;

}
