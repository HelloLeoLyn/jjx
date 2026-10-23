package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryMaterialCategory;
import com.jjx.inventory.domain.InventoryMaterialSpecAttrDef;
import com.jjx.inventory.domain.InventoryMaterialSpecValue;
import com.jjx.inventory.dto.update.MaterialSpecValuesDTO;
import com.jjx.inventory.dto.vo.MaterialSpecAttrDefVO;
import com.jjx.inventory.dto.vo.MaterialSpecValueVO;
import com.jjx.inventory.dto.vo.MaterialSpecValuesVO;
import com.jjx.inventory.mapper.InventoryMaterialCategoryMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryMaterialSpecAttrDefMapper;
import com.jjx.inventory.mapper.InventoryMaterialSpecValueMapper;
import com.jjx.inventory.service.InventoryMaterialSpecService;
import com.jjx.inventory.util.MaterialSpecComposer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料规格属性服务实现（dev-20261023-022）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryMaterialSpecServiceImpl implements InventoryMaterialSpecService {

    private final InventoryMaterialSpecAttrDefMapper defMapper;
    private final InventoryMaterialSpecValueMapper valueMapper;
    private final InventoryMaterialMapper materialMapper;
    private final InventoryMaterialCategoryMapper categoryMapper;

    @Override
    public List<MaterialSpecAttrDefVO> listDefs(String categoryCode) {
        return toDefVOs(loadDefs(categoryCode));
    }

    @Override
    public MaterialSpecValuesVO getByMaterialId(Long materialId) {
        if (materialId == null) {
            throw new BusinessException("物料ID不能为空");
        }
        InventoryMaterial material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("物料不存在");
        }
        String categoryCode = resolveCategoryCode(material.getCategoryId());
        MaterialSpecValuesVO vo = new MaterialSpecValuesVO();
        vo.setMaterialId(material.getMaterialId());
        vo.setMaterialCode(material.getMaterialCode());
        vo.setCategoryCode(categoryCode);
        vo.setDefs(toDefVOs(loadDefs(categoryCode)));
        List<InventoryMaterialSpecValue> values = loadValues(materialId);
        vo.setValues(toValueVOs(values));
        Map<String, Object> text = valueMapper.selectSpecText(materialId);
        vo.setSpecification(text == null ? material.getSpecification() : asString(text.get("specification")));
        vo.setSupplement(text == null ? null : asString(text.get("spec_supplement")));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialSpecValuesVO save(MaterialSpecValuesDTO dto) {
        if (dto == null) {
            throw new BusinessException("参数不能为空");
        }
        InventoryMaterial material = resolveMaterial(dto);
        if (material.getOriginalName() != null) throw new BusinessException("新模型物料属性须随物料表单一次保存");
        String categoryCode = StringUtils.hasText(dto.getCategoryCode())
                ? dto.getCategoryCode().trim()
                : resolveCategoryCode(material.getCategoryId());
        List<InventoryMaterialSpecAttrDef> defs = loadDefs(categoryCode);

        MaterialSpecValuesVO vo = new MaterialSpecValuesVO();
        vo.setMaterialId(material.getMaterialId());
        vo.setMaterialCode(material.getMaterialCode());
        vo.setCategoryCode(categoryCode);
        vo.setDefs(toDefVOs(defs));

        // 该品类未配置属性：规格仍由表单自由填写，本接口不覆盖，避免把人工填写的内容清空。
        if (defs.isEmpty()) {
            if (dto.getAttributes() != null && dto.getAttributes().stream()
                    .anyMatch(item -> item != null && StringUtils.hasText(item.getValue()))) {
                throw new BusinessException("该品类未配置规格属性，请直接在「规格」里填写");
            }
            Map<String, Object> text = valueMapper.selectSpecText(material.getMaterialId());
            vo.setSpecification(text == null ? material.getSpecification() : asString(text.get("specification")));
            vo.setSupplement(text == null ? null : asString(text.get("spec_supplement")));
            vo.setValues(toValueVOs(loadValues(material.getMaterialId())));
            return vo;
        }

        String text;
        try {
            text = MaterialSpecComposer.compose(defs, dto.getAttributes(),
                    dto.getSupplement(), Boolean.TRUE.equals(dto.getCreateMode()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }

        replaceValues(material.getMaterialId(), defs, dto.getAttributes());
        String supplement = StringUtils.hasText(dto.getSupplement()) ? dto.getSupplement().trim() : null;
        String storedText = text.isEmpty() ? null : text;
        if (storedText == null) {
            // 属性全空时不自动清空既有规格：历史物料尚未结构化，清空会丢下游（打印/BOM）口径。
            // 确需清空应显式在「规格」里操作，不随属性保存副作用发生。
            Map<String, Object> current = valueMapper.selectSpecText(material.getMaterialId());
            storedText = current == null ? null : asString(current.get("specification"));
            if (supplement == null && current != null) {
                supplement = asString(current.get("spec_supplement"));
            }
        }
        valueMapper.updateSpecText(material.getMaterialId(), storedText, supplement);

        vo.setSpecification(storedText);
        vo.setSupplement(supplement);
        vo.setValues(toValueVOs(loadValues(material.getMaterialId())));
        return vo;
    }

    private InventoryMaterial resolveMaterial(MaterialSpecValuesDTO dto) {
        InventoryMaterial material = null;
        if (dto.getMaterialId() != null) {
            material = materialMapper.selectById(dto.getMaterialId());
        } else if (StringUtils.hasText(dto.getMaterialCode())) {
            material = materialMapper.selectOne(new LambdaQueryWrapper<InventoryMaterial>()
                    .eq(InventoryMaterial::getMaterialCode, dto.getMaterialCode().trim()));
        }
        if (material == null) {
            throw new BusinessException("物料不存在，请先保存物料再保存规格属性");
        }
        return material;
    }

    private String resolveCategoryCode(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        InventoryMaterialCategory category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getCategoryCode();
    }

    private List<InventoryMaterialSpecAttrDef> loadDefs(String categoryCode) {
        if (!StringUtils.hasText(categoryCode)) {
            return new ArrayList<>();
        }
        return defMapper.selectList(new LambdaQueryWrapper<InventoryMaterialSpecAttrDef>()
                .eq(InventoryMaterialSpecAttrDef::getCategoryCode, categoryCode.trim())
                .eq(InventoryMaterialSpecAttrDef::getStatus, 1)
                .orderByAsc(InventoryMaterialSpecAttrDef::getSortOrder));
    }

    private List<InventoryMaterialSpecValue> loadValues(Long materialId) {
        return valueMapper.selectList(new LambdaQueryWrapper<InventoryMaterialSpecValue>()
                .eq(InventoryMaterialSpecValue::getMaterialId, materialId)
                .orderByAsc(InventoryMaterialSpecValue::getSortOrder));
    }

    /** 全量替换某个物料的属性值：删旧插新，保证同一属性只有一条。 */
    private void replaceValues(Long materialId, List<InventoryMaterialSpecAttrDef> defs,
                               List<MaterialSpecValuesDTO.Item> items) {
        valueMapper.delete(new LambdaQueryWrapper<InventoryMaterialSpecValue>()
                .eq(InventoryMaterialSpecValue::getMaterialId, materialId));
        Map<String, MaterialSpecValuesDTO.Item> byCode = new HashMap<>();
        if (items != null) {
            for (MaterialSpecValuesDTO.Item item : items) {
                if (item != null && StringUtils.hasText(item.getAttrCode())) {
                    byCode.put(item.getAttrCode().trim(), item);
                }
            }
        }
        for (InventoryMaterialSpecAttrDef def : defs) {
            MaterialSpecValuesDTO.Item item = byCode.get(def.getAttrCode());
            if (item == null) {
                continue;
            }
            String segment;
            try {
                segment = MaterialSpecComposer.describeValue(def, item);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(e.getMessage());
            }
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            InventoryMaterialSpecValue row = new InventoryMaterialSpecValue();
            row.setMaterialId(materialId);
            row.setAttrCode(def.getAttrCode());
            row.setAttrName(def.getAttrName());
            row.setUnit(def.getUnit());
            row.setSortOrder(def.getSortOrder());
            String type = def.getInputType() == null ? "" : def.getInputType().trim().toLowerCase();
            if ("number".equals(type) || "range".equals(type)) {
                row.setValueNum(parseDecimal(item.getValue(), def.getUnit()));
                row.setValueNum2("range".equals(type) ? parseDecimal(item.getValue2(), def.getUnit()) : null);
            } else {
                row.setValueText(segment);
            }
            valueMapper.insert(row);
        }
    }

    /** 组合阶段已校验过数值，这里只在写入结构化列时做宽松解析，失败不影响文本口径。 */
    private BigDecimal parseDecimal(String value, String unit) {
        String stripped = MaterialSpecComposer.stripUnit(value, unit == null ? "" : unit.trim());
        if (!StringUtils.hasText(stripped)) {
            return null;
        }
        try {
            return new BigDecimal(stripped.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<MaterialSpecAttrDefVO> toDefVOs(List<InventoryMaterialSpecAttrDef> defs) {
        List<MaterialSpecAttrDefVO> result = new ArrayList<>();
        for (InventoryMaterialSpecAttrDef def : defs) {
            MaterialSpecAttrDefVO vo = new MaterialSpecAttrDefVO();
            vo.setId(def.getId());
            vo.setCategoryCode(def.getCategoryCode());
            vo.setAttrCode(def.getAttrCode());
            vo.setAttrName(def.getAttrName());
            vo.setInputType(def.getInputType());
            vo.setUnit(def.getUnit());
            vo.setOptions(MaterialSpecComposer.parseOptions(def.getOptionsJson()));
            vo.setRequired(def.getRequired() != null && def.getRequired() == 1);
            vo.setSortOrder(def.getSortOrder());
            result.add(vo);
        }
        return result;
    }

    private List<MaterialSpecValueVO> toValueVOs(List<InventoryMaterialSpecValue> values) {
        List<MaterialSpecValueVO> result = new ArrayList<>();
        for (InventoryMaterialSpecValue value : values) {
            MaterialSpecValueVO vo = new MaterialSpecValueVO();
            vo.setAttrCode(value.getAttrCode());
            vo.setAttrName(value.getAttrName());
            vo.setUnit(value.getUnit());
            vo.setValueText(value.getValueText());
            vo.setValueNum(value.getValueNum());
            vo.setValueNum2(value.getValueNum2());
            result.add(vo);
        }
        return result;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
