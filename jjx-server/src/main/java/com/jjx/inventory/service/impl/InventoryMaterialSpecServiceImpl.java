package com.jjx.inventory.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.dto.update.MaterialSpecValuesDTO;
import com.jjx.inventory.dto.vo.MaterialSpecAttrDefVO;
import com.jjx.inventory.dto.vo.MaterialSpecValueVO;
import com.jjx.inventory.dto.vo.MaterialSpecValuesVO;
import com.jjx.inventory.mapper.InventoryMaterialCategoryMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.service.InventoryMaterialSpecService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 旧规格接口只读兼容；302双表已停用，属性保存统一走物料表单。dev-20261024-007 */
@Deprecated
@Service
@RequiredArgsConstructor
public class InventoryMaterialSpecServiceImpl implements InventoryMaterialSpecService {
    private final InventoryMaterialMapper materialMapper;
    private final InventoryMaterialCategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;

    /** 旧定义已停用；新定义由主数据分类配置接口返回。 */
    @Override
    public List<MaterialSpecAttrDefVO> listDefs(String categoryCode) {
        return List.of();
    }

    @Override
    public MaterialSpecValuesVO getByMaterialId(Long materialId) {
        if (materialId == null) throw new BusinessException("物料ID不能为空");
        var material = materialMapper.selectById(materialId);
        if (material == null) throw new BusinessException("物料不存在");
        MaterialSpecValuesVO vo = new MaterialSpecValuesVO();
        vo.setMaterialId(materialId);
        vo.setMaterialCode(material.getMaterialCode());
        if (material.getCategoryId() != null) {
            var category = categoryMapper.selectById(material.getCategoryId());
            vo.setCategoryCode(category == null ? null : category.getCategoryCode());
        }
        vo.setSpecification(material.getSpecification());
        vo.setDefs(List.of());
        List<MaterialSpecValueVO> values = new ArrayList<>();
        if (material.getAttributeJson() != null && !material.getAttributeJson().isBlank()) {
            Map<String, Object> attributes;
            try {
                attributes = objectMapper.readValue(material.getAttributeJson(), new TypeReference<Map<String, Object>>() { });
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new BusinessException("物料属性数据格式错误");
            }
            if (attributes == null) throw new BusinessException("物料属性必须为JSON对象");
            attributes.forEach((key, value) -> {
                if (value == null) return;
                MaterialSpecValueVO item = new MaterialSpecValueVO();
                item.setAttrCode(key);
                item.setValueText(value.toString());
                values.add(item);
            });
        }
        vo.setValues(values);
        return vo;
    }

    /** 禁止旧入口继续写302表或独立改规格，避免双轨。 */
    @Override
    public MaterialSpecValuesVO save(MaterialSpecValuesDTO dto) {
        throw new BusinessException("旧规格属性保存接口已停用，请使用物料表单保存属性");
    }
}
