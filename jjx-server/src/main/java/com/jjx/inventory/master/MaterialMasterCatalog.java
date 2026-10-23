package com.jjx.inventory.master;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterialCategory;
import com.jjx.inventory.mapper.InventoryMaterialCategoryMapper;
import com.jjx.system.domain.entity.SysDictItem;
import com.jjx.system.mapper.SysDictItemMapper;
import com.jjx.system.mapper.SysDictMapper;
import com.jjx.system.domain.entity.SysDict;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MaterialMasterCatalog {
    private final InventoryMaterialCategoryMapper categoryMapper;
    private final AttributeTemplateMapper templateMapper;
    private final SysDictItemMapper dictMapper;
    private final SysDictMapper dictTypeMapper;
    private final ObjectMapper objectMapper;

    public static final Map<String, String> ROOT_TYPES = Map.of("RAW", "R", "SEMI", "S", "FINISHED", "F");
    public static final List<String> DICTIONARIES = List.of("material_meta", "form", "color", "unit", "brand");

    public record Category(Long categoryId, String categoryCode, String categoryName, Long parentId,
                           int categoryLevel, String materialType, String pathName, boolean selectable, Integer sortOrder, Integer status) { }
    public record Configuration(Category category, List<Category> path, InventoryAttributeTemplate template,
                                MaterialAttributeSchema schema) { }
    public record DictionaryOption(String code, String label) { }

    public List<InventoryMaterialCategory> allCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<InventoryMaterialCategory>()
                .orderByAsc(InventoryMaterialCategory::getSortOrder, InventoryMaterialCategory::getCategoryId));
    }

    public List<Category> categories() {
        return categories(false);
    }

    public List<Category> categories(boolean includeDisabled) {
        List<InventoryMaterialCategory> all = allCategories();
        Map<Long, InventoryMaterialCategory> byId = new HashMap<>();
        all.forEach(c -> byId.put(c.getCategoryId(), c));
        Set<Long> templateCategories = new HashSet<>();
        templateMapper.selectList(new LambdaQueryWrapper<InventoryAttributeTemplate>()
                .eq(InventoryAttributeTemplate::getStatus, StatusEnum.NORMAL.getCode()))
                .forEach(t -> templateCategories.add(t.getCategoryId()));
        List<Category> result = new ArrayList<>();
        for (InventoryMaterialCategory category : all) {
            List<InventoryMaterialCategory> path = path(category.getCategoryId(), byId, false);
            if (path.isEmpty() || !includeDisabled && path.stream().anyMatch(c -> !enabled(c))) continue;
            boolean leaf = all.stream().noneMatch(c -> Objects.equals(category.getCategoryId(), c.getParentId()));
            result.add(toCategory(category, path, leaf && templateCategories.contains(category.getCategoryId())));
        }
        return result;
    }

    public Configuration configuration(Long categoryId) {
        Map<Long, InventoryMaterialCategory> byId = new HashMap<>();
        allCategories().forEach(c -> byId.put(c.getCategoryId(), c));
        List<InventoryMaterialCategory> path = path(categoryId, byId, true);
        if (path.stream().anyMatch(c -> !enabled(c))) throw new BusinessException("分类或上级分类已停用");
        if (byId.values().stream().anyMatch(c -> Objects.equals(categoryId, c.getParentId()))) throw new BusinessException("请选择末级品类");
        InventoryAttributeTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<InventoryAttributeTemplate>()
                .eq(InventoryAttributeTemplate::getCategoryId, categoryId));
        if (template == null || !StatusEnum.NORMAL.getCode().equals(template.getStatus())) throw new BusinessException("该品类尚未配置启用的属性模板");
        MaterialAttributeSchema schema = MaterialAttributeSchema.parse(template.getAttributeSchema(), objectMapper);
        List<Category> ancestors = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) ancestors.add(toCategory(path.get(i), path.subList(0, i + 1), i == path.size() - 1));
        return new Configuration(ancestors.get(ancestors.size() - 1), ancestors, template, schema);
    }

    private List<InventoryMaterialCategory> path(Long id, Map<Long, InventoryMaterialCategory> byId, boolean required) {
        List<InventoryMaterialCategory> path = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        while (id != null && id != 0L) {
            InventoryMaterialCategory c = byId.get(id);
            if (c == null || !seen.add(id) || path.size() == 3) {
                if (required) throw new BusinessException("分类路径无效或超过三级");
                return List.of();
            }
            path.add(0, c);
            id = c.getParentId();
        }
        if (path.isEmpty() || !ROOT_TYPES.containsKey(path.get(0).getCategoryCode())) {
            if (required) throw new BusinessException("请选择新模型分类树内的品类");
            return List.of();
        }
        for (int i = 0; i < path.size(); i++) {
            if (!Integer.valueOf(i + 1).equals(path.get(i).getCategoryLevel())) {
                if (required) throw new BusinessException("分类层级与父子关系不一致");
                return List.of();
            }
        }
        return path;
    }

    private Category toCategory(InventoryMaterialCategory category, List<InventoryMaterialCategory> path, boolean selectable) {
        return new Category(category.getCategoryId(), category.getCategoryCode(), category.getCategoryName(), category.getParentId(),
                path.size(), ROOT_TYPES.get(path.get(0).getCategoryCode()),
                String.join(" / ", path.stream().map(InventoryMaterialCategory::getCategoryName).toList()), selectable,
                category.getSortOrder(), enabled(category) ? StatusEnum.NORMAL.getCode() : StatusEnum.DISABLE.getCode());
    }

    private static boolean enabled(InventoryMaterialCategory c) { return StatusEnum.NORMAL.getCode().toString().equals(c.getStatus()); }

    public Map<String, List<DictionaryOption>> dictionaries() {
        Map<String, List<DictionaryOption>> result = new LinkedHashMap<>();
        Set<String> active = new HashSet<>(dictTypeMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .in(SysDict::getDictCode, DICTIONARIES).eq(SysDict::getIsActive, StatusEnum.NORMAL.getCode())
                .eq(SysDict::getTenantId, 1L)).stream().map(SysDict::getDictCode).toList());
        for (String code : DICTIONARIES) {
            if (!active.contains(code)) { result.put(code, List.of()); continue; }
            result.put(code, dictMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                    .eq(SysDictItem::getDictCode, code).eq(SysDictItem::getIsActive, StatusEnum.NORMAL.getCode())
                    .eq(SysDictItem::getTenantId, 1L).orderByAsc(SysDictItem::getSortOrder, SysDictItem::getItemId))
                    .stream().map(d -> new DictionaryOption(d.getItemValue(), d.getLabel())).toList());
        }
        return result;
    }

    public String dictionaryLabel(String dict, String code, boolean required) {
        if (code == null || code.isBlank()) {
            if (required) throw new BusinessException(dict + "不能为空");
            return "";
        }
        return dictionaries().getOrDefault(dict, List.of()).stream().filter(d -> code.equals(d.code()))
                .map(DictionaryOption::label).findFirst().orElseThrow(() -> new BusinessException("字典选项不存在或已停用：" + dict + "/" + code));
    }
}
