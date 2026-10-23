package com.jjx.inventory.master;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryMaterialCategory;
import com.jjx.inventory.mapper.InventoryMaterialCategoryMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MaterialMasterConfigurationService {
    private final MaterialMasterCatalog catalog;
    private final MaterialMasterMapper locks;
    private final InventoryMaterialCategoryMapper categories;
    private final AttributeTemplateMapper templates;
    private final InventoryMaterialMapper materials;
    private final ObjectMapper objectMapper;

    public record CategorySave(Long categoryId, String categoryCode, String categoryName, Long parentId,
                               Integer sortOrder, Integer status) { }
    public record TemplateSave(String templateCode, String templateName, Long categoryId,
                               MaterialAttributeSchema attributeSchema, Integer status) { }
    public record TemplateDetail(InventoryAttributeTemplate template, MaterialAttributeSchema schema) { }

    public TemplateDetail templateDetail(Long categoryId) {
        if (catalog.categories(true).stream().noneMatch(c -> c.categoryId().equals(categoryId))) fail("分类不在新模型分类树内");
        InventoryAttributeTemplate template = template(categoryId);
        return template == null ? null : new TemplateDetail(template,MaterialAttributeSchema.parse(template.getAttributeSchema(),objectMapper));
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeUnusedTemplate(Long categoryId) {
        if (locks.lockCategory(categoryId) == null) fail("分类不存在");
        InventoryAttributeTemplate template = locks.lockTemplate(categoryId);
        if (materialCount(categoryId) > 0) fail("已有物料引用，不能移除模板");
        if (template != null) templates.deleteById(template.getTemplateId());
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryMaterialCategory saveCategory(CategorySave dto, String operator) {
        if (dto == null || dto.categoryName() == null || dto.categoryName().isBlank() || dto.categoryName().length() > 100) fail("分类名称不能为空且不超过100字");
        validateStatus(dto.status());
        if (dto.parentId() == null || dto.parentId() == 0L) fail("一级类型固定为原材料、半成品、成品，请在现有节点下建分类");
        if (locks.lockCategory(dto.parentId()) == null) fail("上级分类不存在");
        MaterialMasterCatalog.Category parent = catalog.categories().stream().filter(c -> c.categoryId().equals(dto.parentId())).findFirst().orElseThrow(() -> new BusinessException("上级分类不在启用的新模型分类树内"));
        if (parent.categoryLevel() >= 3 || template(parent.categoryId()) != null) fail("上级分类已有录入模板或已到三级，不能再增加子分类");
        InventoryMaterialCategory current = dto.categoryId() == null ? null : categories.selectById(dto.categoryId());
        if (dto.categoryId() != null && current == null) fail("分类不存在");
        if (current != null) {
            locks.lockCategory(current.getCategoryId());
            if (MaterialMasterCatalog.ROOT_TYPES.containsKey(current.getCategoryCode())) fail("一级类型不能编辑");
            if (Objects.equals(current.getCategoryId(), dto.parentId())) fail("分类不能指向自身");
            boolean moving = !Objects.equals(current.getParentId(), dto.parentId());
            if (moving && (hasChildren(current.getCategoryId()) || template(current.getCategoryId()) != null || materialCount(current.getCategoryId()) > 0)) fail("已有子分类、模板或物料的分类不能直接迁移父节点");
            if (StatusEnum.DISABLE.getCode().equals(dto.status()) && (hasChildren(current.getCategoryId()) || materialCount(current.getCategoryId()) > 0)) fail("分类已有子分类或物料，不能直接停用");
        }
        InventoryMaterialCategory entity = current == null ? new InventoryMaterialCategory() : current;
        if (current == null) {
            if (dto.categoryCode() == null || !dto.categoryCode().matches("[A-Z][A-Z0-9_]{0,49}")) fail("分类编码须为大写字母、数字和下划线");
            if (categories.selectCount(new LambdaQueryWrapper<InventoryMaterialCategory>().eq(InventoryMaterialCategory::getCategoryCode, dto.categoryCode())) > 0) fail("分类编码已存在");
            entity.setCategoryCode(dto.categoryCode());
            entity.setCreateBy(operator);
        } else if (!Objects.equals(current.getCategoryCode(), dto.categoryCode())) fail("分类编码不能修改");
        entity.setCategoryName(dto.categoryName().trim()); entity.setParentId(dto.parentId());
        entity.setCategoryLevel(parent.categoryLevel() + 1); entity.setMaterialType(parent.materialType());
        String parentPath = categories.selectById(parent.categoryId()).getCategoryPath();
        entity.setCategoryPath((parentPath == null || parentPath.isBlank() ? parent.categoryCode() : parentPath) + "/" + entity.getCategoryCode());
        entity.setStatus((dto.status() == null ? StatusEnum.NORMAL.getCode() : dto.status()).toString());
        entity.setSortOrder(dto.sortOrder() == null ? 0 : dto.sortOrder()); entity.setUpdateBy(operator);
        if (current == null) categories.insert(entity); else categories.updateById(entity);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryAttributeTemplate saveTemplate(TemplateSave dto, String operator) {
        if (dto == null || dto.categoryId() == null || locks.lockCategory(dto.categoryId()) == null) fail("请选择末级分类");
        MaterialMasterCatalog.Category category = catalog.categories().stream().filter(c -> c.categoryId().equals(dto.categoryId())).findFirst().orElseThrow(() -> new BusinessException("分类不在启用的新模型分类树内"));
        if (hasChildren(category.categoryId())) fail("模板只能绑定末级分类");
        validateStatus(dto.status());
        if (dto.templateCode() == null || !dto.templateCode().matches("[A-Z][A-Z0-9_]{0,49}") || dto.templateName() == null || dto.templateName().isBlank() || dto.templateName().length() > 100) fail("模板编码或名称无效");
        if (dto.attributeSchema() == null) fail("模板定义不能为空");
        dto.attributeSchema().validateDefinition();
        for (String meta : dto.attributeSchema().metaOptions()) catalog.dictionaryLabel("material_meta", meta, true);
        catalog.dictionaryLabel("form", dto.attributeSchema().defaultForm(), false);
        catalog.dictionaryLabel("unit", dto.attributeSchema().defaultUnit(), true);
        InventoryAttributeTemplate existing = locks.lockTemplate(dto.categoryId());
        String schema;
        try { schema = objectMapper.writeValueAsString(dto.attributeSchema()); }
        catch (Exception e) { throw new BusinessException("模板序列化失败"); }
        if (existing != null && materialCount(dto.categoryId()) > 0) {
            try {
                if (!MaterialAttributeSchema.parse(existing.getAttributeSchema(), objectMapper).equals(dto.attributeSchema()) || StatusEnum.DISABLE.getCode().equals(dto.status())) fail("已有物料引用模板，结构与启用状态不能直接修改；需先完成版本迁移");
            } catch (BusinessException e) { throw e; }
            catch (Exception e) { throw new BusinessException("现有模板格式错误"); }
        }
        if (templates.selectCount(new LambdaQueryWrapper<InventoryAttributeTemplate>().eq(InventoryAttributeTemplate::getTemplateCode, dto.templateCode())
                .ne(existing != null, InventoryAttributeTemplate::getTemplateId, existing == null ? null : existing.getTemplateId())) > 0) fail("模板编码已被其他分类使用");
        InventoryAttributeTemplate entity = existing == null ? new InventoryAttributeTemplate() : existing;
        entity.setCategoryId(dto.categoryId()); entity.setTemplateCode(dto.templateCode()); entity.setTemplateName(dto.templateName().trim());
        entity.setAttributeSchema(schema); entity.setStatus(dto.status() == null ? StatusEnum.NORMAL.getCode() : dto.status());
        entity.setUpdateBy(operator); entity.setUpdateTime(LocalDateTime.now());
        if (existing == null) { entity.setCreateBy(operator); templates.insert(entity); } else templates.updateById(entity);
        return entity;
    }

    private boolean hasChildren(Long id) { return categories.selectCount(new LambdaQueryWrapper<InventoryMaterialCategory>().eq(InventoryMaterialCategory::getParentId, id)) > 0; }
    private InventoryAttributeTemplate template(Long id) { return templates.selectOne(new LambdaQueryWrapper<InventoryAttributeTemplate>().eq(InventoryAttributeTemplate::getCategoryId, id)); }
    /** 锁定读使用最新已提交事实，不能用RR旧快照漏看刚保存的引用。 */
    private long materialCount(Long id) { return locks.firstMaterialInCategory(id) == null ? 0 : 1; }
    private static void validateStatus(Integer status) { if (status != null && !StatusEnum.NORMAL.getCode().equals(status) && !StatusEnum.DISABLE.getCode().equals(status)) fail("启用状态无效"); }
    private static void fail(String message) { throw new BusinessException(message); }
}
