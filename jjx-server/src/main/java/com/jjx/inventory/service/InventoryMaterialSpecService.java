package com.jjx.inventory.service;

import com.jjx.inventory.dto.update.MaterialSpecValuesDTO;
import com.jjx.inventory.dto.vo.MaterialSpecAttrDefVO;
import com.jjx.inventory.dto.vo.MaterialSpecValuesVO;

import java.util.List;

/**
 * 物料规格属性服务（dev-20261023-022）。
 * <p>
 * 规格文本仍写回 inventory_material.specification，是下游唯一读取口径；
 * 属性值只是它的结构化来源，不构成第二真源。
 */
public interface InventoryMaterialSpecService {

    /** 按品类取属性定义（前端渲染输入项）。 */
    List<MaterialSpecAttrDefVO> listDefs(String categoryCode);

    /** 取某个物料的属性定义 + 当前值 + 当前规格文本。 */
    MaterialSpecValuesVO getByMaterialId(Long materialId);

    /** 保存属性值并重算规格文本。 */
    MaterialSpecValuesVO save(MaterialSpecValuesDTO dto);
}
