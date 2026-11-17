package com.jjx.inventory.master;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
                               Integer sortOrder, Integer status, String modelPrefix) { }
    public record TemplateSave(String templateCode, String templateName, Long categoryId,
                               MaterialAttributeSchema attributeSchema, Integer status) { }
    public record TemplateDetail(InventoryAttributeTemplate template, MaterialAttributeSchema schema) { }
    public record PatternSave(Long categoryId, String block, JsonNode pattern) { }
    private static final Set<String> PATTERN_BLOCKS = Set.of("name_pattern", "model_pattern", "spec_pattern");

    /** 在分类锁内读取最新模板，只替换指定规则；其它块不接受客户端快照。 */
    @Transactional(rollbackFor = Exception.class)
    public TemplateDetail savePattern(PatternSave dto, String operator) {
        if (dto == null || dto.categoryId() == null || dto.block() == null || !PATTERN_BLOCKS.contains(dto.block())
                || dto.pattern() == null || !dto.pattern().isObject() || !dto.pattern().path("fields").isArray())
            fail("请选择分类和单个规则块，并提交含 fields 数组的规则对象");
        if (locks.lockCategory(dto.categoryId()) == null) fail("分类不存在");
        if (catalog.categories().stream().noneMatch(c -> c.categoryId().equals(dto.categoryId()))) fail("分类不在启用的新模型分类树内");
        if (hasChildren(dto.categoryId())) fail("模板只能绑定末级分类");
        InventoryAttributeTemplate existing = locks.lockTemplate(dto.categoryId());
        if (existing == null) fail("请先保存属性字段，建立模板后再保存规则");
        MaterialAttributeSchema old = MaterialAttributeSchema.parse(existing.getAttributeSchema(), objectMapper);
        ObjectNode merged;
        try { merged = (ObjectNode) objectMapper.readTree(existing.getAttributeSchema()); }
        catch (Exception e) { throw new BusinessException("现有模板格式错误"); }
        merged.set(dto.block(), dto.pattern());
        MaterialAttributeSchema next = MaterialAttributeSchema.parse(merged.toString(), objectMapper);
        if (materialCount(dto.categoryId()) > 0) requireCompatibleUpdate(old, next, objectMapper);
        // 目标块也沿用现有 DTO 序列化，不能把请求中的未知 JSON 字段带入模板结构。
        merged.set(dto.block(), objectMapper.valueToTree(switch (dto.block()) {
            case "name_pattern" -> next.namePattern();
            case "model_pattern" -> next.modelPattern();
            default -> next.specPattern();
        }));
        // 只写 attribute_schema 与审计列，保留其余 JSON 节点的原值。
        InventoryAttributeTemplate patch = new InventoryAttributeTemplate();
        patch.setTemplateId(existing.getTemplateId());
        patch.setAttributeSchema(merged.toString());
        patch.setUpdateBy(operator); patch.setUpdateTime(LocalDateTime.now());
        templates.updateById(patch);
        existing.setAttributeSchema(patch.getAttributeSchema());
        return new TemplateDetail(existing, next);
    }

    /** 属性字段独立保存，规则从锁内最新值承接，避免字段页覆盖刚保存的规则。 */
    @Transactional(rollbackFor = Exception.class)
    public TemplateDetail saveTemplateFields(TemplateSave dto, String operator) {
        if (dto == null || dto.categoryId() == null || dto.attributeSchema() == null
                || locks.lockCategory(dto.categoryId()) == null) fail("请选择末级分类并提交模板定义");
        InventoryAttributeTemplate existing = locks.lockTemplate(dto.categoryId());
        MaterialAttributeSchema current = existing == null ? null : MaterialAttributeSchema.parse(existing.getAttributeSchema(), objectMapper);
        MaterialAttributeSchema fields = dto.attributeSchema();
        MaterialAttributeSchema merged = new MaterialAttributeSchema(fields.version(), fields.metaOptions(), fields.metaRequired(),
                fields.defaultMeta(), fields.defaultForm(), fields.defaultUnit(), fields.fields(),
                current == null ? null : current.namePattern(), current == null ? null : current.specPattern(), current == null ? null : current.modelPattern());
        InventoryAttributeTemplate saved = saveTemplate(new TemplateSave(dto.templateCode(), dto.templateName(), dto.categoryId(), merged, dto.status()), operator);
        return new TemplateDetail(saved, merged);
    }

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
        // 2026-11-04 dev-20261104-008：层级放开到四级（业内"分类树深度允许不齐"，目前只有电子元件/辅料需要子类）。
        // 父级已有模板时允许加子类，但必须先把它下面的物料迁到子类，否则物料会挂在不可选的父级上。
        if (parent.categoryLevel() >= 4) fail("已到四级分类，不能再增加子分类");
        if (template(parent.categoryId()) != null && materialCount(parent.categoryId()) > 0)
            fail("上级分类已有录入模板且存在物料，请先把物料迁到子分类后再增加子分类");
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
        entity.setModelPrefix(dto.modelPrefix());
        com.jjx.inventory.service.impl.InventoryMaterialCategoryServiceImpl.validateModelPrefix(entity, categories);
        entity.setCategoryName(dto.categoryName().trim()); entity.setParentId(dto.parentId());
        entity.setCategoryLevel(parent.categoryLevel() + 1);
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
        // 包含规格字段显示单位白名单、尺寸三键及小数位范围校验；不修改历史模板。
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
                if (StatusEnum.DISABLE.getCode().equals(dto.status())) fail("已有物料引用模板，不能直接停用");
                checkTemplateReferences(dto.categoryId(), MaterialAttributeSchema.parse(existing.getAttributeSchema(), objectMapper), dto.attributeSchema());
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

    /** 纯显示、新建默认值与追加选项不改变旧物料事实。 */
    public static void requireCompatibleUpdate(MaterialAttributeSchema old, MaterialAttributeSchema next, ObjectMapper mapper) {
        if (requiresReferenceCheck(old, next, mapper)) fail("该调整可能影响已有物料，需检查具体属性引用");
    }

    private static Map<String, MaterialAttributeSchema.Field> fieldMap(MaterialAttributeSchema schema) {
        Map<String, MaterialAttributeSchema.Field> result = new LinkedHashMap<>();
        schema.fields().forEach(f -> result.put(f.key(), f));
        return result;
    }

    private static JsonNode fieldRules(MaterialAttributeSchema.Field field, ObjectMapper mapper) {
        ObjectNode rules = mapper.valueToTree(field);
        rules.remove(List.of("label", "common", "in_spec", "spec_order", "true_label", "false_label", "default", "options"));
        return rules;
    }

    private static Set<String> codes(MaterialAttributeSchema.Field field) {
        Set<String> result = new HashSet<>();
        if (field.options() != null) field.options().forEach(o -> result.add(o.code()));
        return result;
    }

    private static boolean fieldNeedsCheck(MaterialAttributeSchema.Field old, MaterialAttributeSchema.Field next, ObjectMapper mapper) {
        if (old == null) {
            if (next.key().equals("thickness_mm")) fail("厚度请在统一尺寸组件中编辑");
            return next.required();
        }
        return next == null || !fieldRules(old, mapper).equals(fieldRules(next, mapper)) || !codes(next).containsAll(codes(old));
    }

    private static boolean requiresReferenceCheck(MaterialAttributeSchema old, MaterialAttributeSchema next, ObjectMapper mapper) {
        if (!next.metaOptions().containsAll(old.metaOptions()) || !old.metaRequired() && next.metaRequired()) return true;
        var before = fieldMap(old);
        var after = fieldMap(next);
        Set<String> keys = new HashSet<>(before.keySet()); keys.addAll(after.keySet());
        return keys.stream().anyMatch(key -> fieldNeedsCheck(before.get(key), after.get(key), mapper));
    }

    private void checkTemplateReferences(Long categoryId, MaterialAttributeSchema old, MaterialAttributeSchema next) {
        if (!requiresReferenceCheck(old, next, objectMapper)) return;
        long after = 0;
        while (true) {
            var page = locks.templateReferences(categoryId, after);
            for (var material : page) requireMaterialCompatibility(old, next, material, objectMapper);
            if (page.size() < 200) return;
            after = page.get(page.size() - 1).getMaterialId();
        }
    }

    /** 只校验本次改变的约束，报出具体引用；不写入或自动转换旧值。 */
    public static void requireMaterialCompatibility(MaterialAttributeSchema old, MaterialAttributeSchema next,
                                                     InventoryMaterial material, ObjectMapper mapper) {
        String code = material.getMaterialCode() == null ? String.valueOf(material.getMaterialId()) : material.getMaterialCode();
        try {
            String meta = material.getMaterialMeta();
            if ((!next.metaOptions().containsAll(old.metaOptions()) || !old.metaRequired() && next.metaRequired())
                    && (meta == null || meta.isBlank() ? next.metaRequired() : !next.metaOptions().contains(meta)))
                fail("材质“" + (meta == null ? "未填写" : meta) + "”不符合新的材质范围或必填要求");
            Map<String, Object> values = new LinkedHashMap<>();
            if (material.getAttributeJson() != null && !material.getAttributeJson().isBlank())
                values.putAll(mapper.readValue(material.getAttributeJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            if (material.getThicknessMm() != null) values.put("thickness_mm", material.getThicknessMm());
            var before = fieldMap(old); var after = fieldMap(next);
            Set<String> keys = new LinkedHashSet<>(before.keySet()); keys.addAll(after.keySet());
            for (String key : keys) {
                var previous = before.get(key); var updated = after.get(key);
                if (!fieldNeedsCheck(previous, updated, mapper)) continue;
                boolean used = !MaterialAttributeSchema.missing(mapper.valueToTree(values.get(key)));
                if (updated == null) {
                    if (used) fail("属性“" + previous.label() + "”已有值，不能删除或修改属性编码");
                    continue;
                }
                if (used && previous != null && (!Objects.equals(previous.type(), updated.type()) || !Objects.equals(previous.unit(), updated.unit())))
                    fail("属性“" + previous.label() + "”已有值，不能直接改变类型或单位");
                try { MaterialAttributeSchema.validateExistingField(updated, values, mapper); }
                catch (BusinessException ex) { fail(ex.getMessage() + "（现值：" + mapper.valueToTree(values.get(key)) + "）"); }
            }
        } catch (BusinessException ex) { fail("模板修改影响物料 " + code + "：" + ex.getMessage()); }
        catch (Exception ex) { fail("物料 " + code + " 的属性数据格式错误，请先核对档案"); }
    }

    private boolean hasChildren(Long id) { return categories.selectCount(new LambdaQueryWrapper<InventoryMaterialCategory>().eq(InventoryMaterialCategory::getParentId, id)) > 0; }
    private InventoryAttributeTemplate template(Long id) { return templates.selectOne(new LambdaQueryWrapper<InventoryAttributeTemplate>().eq(InventoryAttributeTemplate::getCategoryId, id)); }
    /** 锁定读使用最新已提交事实，不能用RR旧快照漏看刚保存的引用。 */
    private long materialCount(Long id) { return locks.firstMaterialInCategory(id) == null ? 0 : 1; }
    private static void validateStatus(Integer status) { if (status != null && !StatusEnum.NORMAL.getCode().equals(status) && !StatusEnum.DISABLE.getCode().equals(status)) fail("启用状态无效"); }
    private static void fail(String message) { throw new BusinessException(message); }
}
