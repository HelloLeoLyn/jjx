package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料规格属性值。dev-20261023-022
 */
@Data
public class MaterialSpecValueVO {

    private String attrCode;

    private String attrName;

    private String unit;

    private String valueText;

    private BigDecimal valueNum;

    private BigDecimal valueNum2;
}
