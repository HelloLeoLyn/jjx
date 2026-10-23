package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 物料规格属性整体视图：品类属性定义 + 当前取值 + 生成文本。dev-20261023-022
 */
@Data
public class MaterialSpecValuesVO {

    private Long materialId;

    private String materialCode;

    private String categoryCode;

    /** 属性组合出的规格文本（写回 inventory_material.specification） */
    private String specification;

    /** 生成文本之外的补充说明，≤30 字 */
    private String supplement;

    /** 该品类可填的属性定义；为空表示该品类走自由填写（特殊物料不阻断） */
    private List<MaterialSpecAttrDefVO> defs = new ArrayList<>();

    /** 物料当前已保存的属性值 */
    private List<MaterialSpecValueVO> values = new ArrayList<>();
}
