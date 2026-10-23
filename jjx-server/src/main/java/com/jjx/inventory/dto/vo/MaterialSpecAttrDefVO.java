package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.util.List;

/**
 * 物料规格属性定义（前端按品类渲染输入控件）。dev-20261023-022
 */
@Data
public class MaterialSpecAttrDefVO {

    private Long id;

    private String categoryCode;

    private String attrCode;

    private String attrName;

    /** number / range / select / text */
    private String inputType;

    private String unit;

    /** select 的可选项；其他类型为空 */
    private List<String> options;

    /** 1 新增时必填 0 选填 */
    private Boolean required;

    private Integer sortOrder;
}
