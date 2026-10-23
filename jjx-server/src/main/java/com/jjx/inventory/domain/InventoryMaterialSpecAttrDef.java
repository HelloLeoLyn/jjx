package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物料规格属性定义实体（按品类给的可选项）。
 * 对应表：inventory_material_spec_attr_def（dev-20261023-022）。
 */
@Data
@TableName("inventory_material_spec_attr_def")
public class InventoryMaterialSpecAttrDef {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 品类编码，对应 inventory_material_category.category_code */
    private String categoryCode;

    /** 属性编码，如 thickness / surface */
    private String attrCode;

    /** 属性名称，如 厚度 / 表面处理 */
    private String attrName;

    /** 录入类型：number / range / select / text */
    private String inputType;

    /** 单位，如 mm / g / ℃ */
    private String unit;

    /** select 选项 JSON 数组，如 ["光面","雾面"] */
    private String optionsJson;

    /** 1 新增时必填 0 选填 */
    private Integer required;

    /** 排序 */
    private Integer sortOrder;

    /** 1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
