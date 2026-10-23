package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料规格属性值实体（规格的结构化明细）。
 * 对应表：inventory_material_spec_value（dev-20261023-022）。
 * 注意：结存类数量不走本表；本表只描述物料主数据的规格参数。
 */
@Data
@TableName("inventory_material_spec_value")
public class InventoryMaterialSpecValue {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 物料ID，对应 inventory_material.material_id */
    private Long materialId;

    /** 属性编码 */
    private String attrCode;

    /** 属性名称冗余，便于统计不连定义表 */
    private String attrName;

    /** 单位冗余 */
    private String unit;

    /** 文本值 / 下拉值 */
    private String valueText;

    /** 数值（range 时为下限） */
    private BigDecimal valueNum;

    /** range 上限 */
    private BigDecimal valueNum2;

    /** 取值时属性排序 */
    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
