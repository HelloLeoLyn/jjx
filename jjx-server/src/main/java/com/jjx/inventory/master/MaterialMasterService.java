package com.jjx.inventory.master;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublishSupport;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.service.InventoryMaterialService;
import com.jjx.purchase.mapper.PurchaseSupplierMapper;
import com.jjx.system.domain.entity.SysTag;
import com.jjx.system.service.ISysTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MaterialMasterService {
    private final MaterialMasterCatalog catalog;
    private final MaterialMasterMapper masterMapper;
    private final InventoryMaterialMapper materialMapper;
    private final InventoryMaterialService materialService;
    private final PurchaseSupplierMapper supplierMapper;
    private final ISysTagService tagService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private static final List<String> REQUIRED_FIELD_KEYS =
            List.of("material_grade", "model", "color", "specification", "brand");

    /** 型号占用查询（dev-20261107-012）：全局唯一，排除自身；返回占用物料摘要或 null。 */
    public Map<String, Object> modelOccupied(String model, Long excludeId) {
        if (model == null || model.isBlank()) return null;
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.inventory.domain.InventoryMaterial>()
                .eq(com.jjx.inventory.domain.InventoryMaterial::getModel, model.trim())
                .ne(excludeId != null, com.jjx.inventory.domain.InventoryMaterial::getMaterialId, excludeId)
                .last("LIMIT 1");
        var hit = materialMapper.selectOne(wrapper);
        if (hit == null) return null;
        return Map.of("materialId", hit.getMaterialId(), "materialCode", hit.getMaterialCode(),
                "materialName", hit.getMaterialName() == null ? "" : hit.getMaterialName());
    }

    public Map<String, Boolean> requiredFields(Long categoryId) {
        var category = materialMapper.selectCategory(categoryId);
        if (category == null) throw new BusinessException("分类不存在");
        var required = materialMapper.selectRequiredFields((String) category.get("category_code"));
        Map<String, Boolean> result = new LinkedHashMap<>();
        REQUIRED_FIELD_KEYS.forEach(key -> result.put(key, required.contains(key)));
        return result;
    }

    public void validateRequiredFields(Map<String, Object> fields) {
        if (fields == null || !fields.keySet().equals(new HashSet<>(REQUIRED_FIELD_KEYS))
                || fields.values().stream().anyMatch(value -> !(value instanceof Boolean)))
            throw new BusinessException("必填字段须为 material_grade/model/color/specification/brand 五个布尔值");
    }

    /** 与物料新建使用同一品类锁；五个布尔值一次提交，不保留旧的勾选。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveRequiredFields(Long categoryId, Map<String, Object> fields) {
        validateRequiredFields(fields);
        if (categoryId == null || masterMapper.lockCategory(categoryId) == null)
            throw new BusinessException("分类不存在");
        var category = materialMapper.selectCategory(categoryId);
        String code = (String) category.get("category_code");
        REQUIRED_FIELD_KEYS.forEach(key -> materialMapper.upsertRequiredField(code, key, (Boolean) fields.get(key)));
    }

    public void saveCategoryRemark(Long categoryId, String remark) {
        materialMapper.updateCategoryRemark(categoryId, text(remark, "备注", 200, false));
    }

    public record Detail(InventoryMaterial material, Map<String, Object> attributes,
                         MaterialMasterCatalog.Configuration configuration, List<SysTag> tags, MaterialSourceValues source,
                         SpecificationPreview specificationGeneration) { }

    /** 命名规则预览：按品类取样最多 5 条，给出旧名→新名对照，不改库。 */
    public record NamingSample(Long materialId, String materialCode, String oldName, String newName) { }
    public record NamingPreview(List<NamingSample> samples, String message) { }

    public record RenameChange(Long materialId, String materialCode, String oldName, String newName, String skipReason) { }
    public record RenameReport(int total, int changeable, List<RenameChange> samples, String message) { }

    /**
     * 批量改名预演（只读）：按品类规则算出每条物料的新名称，列出会改/跳过及原因。
     * 跳过原因：rule 未配置规则、blank 规则生成空名、same 与当前名称一致。
     */
    public RenameReport previewRename(Long categoryId, Integer limit) {
        MaterialAttributeSchema schema = requireNamingSchema(categoryId);
        MaterialNamingPattern pattern = schema.namePattern();
        List<InventoryMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<InventoryMaterial>()
                .eq(InventoryMaterial::getCategoryId, categoryId)
                .orderByAsc(InventoryMaterial::getMaterialId));
        int changeable = 0;
        List<RenameChange> samples = new ArrayList<>();
        int max = limit == null || limit <= 0 ? 50 : Math.min(limit, 500);
        String categoryName = catalog.optionalConfiguration(categoryId).category().categoryName();
        for (InventoryMaterial material : materials) {
            String generated = pattern.compose(material, schema, categoryName, brandLabelOf(material), colorLabelOf(material), categoryPrefixOf(material.getCategoryId()), brandAbbrOf(material));
            String skip = generated == null || generated.isBlank() ? "blank"
                    : Objects.equals(generated, material.getMaterialName()) ? "same" : null;
            if (skip == null) changeable++;
            if (samples.size() < max) {
                samples.add(new RenameChange(material.getMaterialId(), material.getMaterialCode(),
                        material.getMaterialName(), generated, skip));
            }
        }
        return new RenameReport(materials.size(), changeable, samples,
                "共 " + materials.size() + " 条，可改名 " + changeable + " 条，明细最多返回 " + max + " 条");
    }

    /** 按品类规则批量改名：只改差异行，逐条走 updateNaming 以保留审计与事件。 */
    @Transactional(rollbackFor = Exception.class)
    public int applyRename(Long categoryId, String operator) {
        MaterialAttributeSchema schema = requireNamingSchema(categoryId);
        MaterialNamingPattern pattern = schema.namePattern();
        List<InventoryMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<InventoryMaterial>()
                .eq(InventoryMaterial::getCategoryId, categoryId)
                .orderByAsc(InventoryMaterial::getMaterialId));
        int changed = 0;
        String categoryName = catalog.optionalConfiguration(categoryId).category().categoryName();
        for (InventoryMaterial material : materials) {
            String generated = pattern.compose(material, schema, categoryName, brandLabelOf(material), colorLabelOf(material), categoryPrefixOf(material.getCategoryId()), brandAbbrOf(material));
            if (generated == null || generated.isBlank() || Objects.equals(generated, material.getMaterialName())) continue;
            updateNaming(material.getMaterialId(),
                    new NamingPatch(generated, null, "按品类命名规则重算"), operator);
            changed++;
        }
        return changed;
    }

    private MaterialAttributeSchema requireNamingSchema(Long categoryId) {
        if (categoryId == null) throw new BusinessException("请选择末级品类");
        MaterialMasterCatalog.Configuration config = catalog.optionalConfiguration(categoryId);
        if (config == null) throw new BusinessException("该品类未配置属性模板");
        MaterialAttributeSchema schema = config.schema();
        if (schema.namePattern() == null || schema.namePattern().normalized().fields().isEmpty())
            throw new BusinessException("该品类未配置命名规则");
        schema.namePattern().validate(schema);
        return schema;
    }

    public record SpecSample(Long materialId, String materialCode, String oldSpec, String newSpec) { }
    public record SpecPreview(List<SpecSample> samples, String message) { }

    /** 规格模板预览：读取已保存的字段单位配置，按品类取最近 5 条物料，给出“当前规格 → 按模板生成”对照（dev-20261029-029）。 */
    public SpecPreview previewSpecification(Long categoryId) {
        if (categoryId == null) throw new BusinessException("请选择末级品类");
        MaterialMasterCatalog.Configuration config = catalog.optionalConfiguration(categoryId);
        if (config == null) throw new BusinessException("该品类未配置属性模板");
        MaterialAttributeSchema schema = config.schema();
        List<InventoryMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<InventoryMaterial>()
                .eq(InventoryMaterial::getCategoryId, categoryId)
                .orderByDesc(InventoryMaterial::getMaterialId)
                .last("LIMIT 5"));
        List<SpecSample> samples = new ArrayList<>();
        for (InventoryMaterial material : materials) {
            samples.add(new SpecSample(material.getMaterialId(), material.getMaterialCode(),
                    material.getSpecification(), generateSpecification(schema, material)));
        }
        return new SpecPreview(samples, materials.isEmpty() ? "该品类暂无物料，模板只影响以后生成" : "预览取该品类最近 5 条物料");
    }

    public record FieldOption(String key, String label, String group) { }

    /** 命名/规格可选字段清单：公共字段 + 该品类属性字段（唯一真源，新增字段无需改前端）。 */
    public List<FieldOption> fieldOptions(Long categoryId) {
        List<FieldOption> options = new ArrayList<>();
        MaterialNamingPattern.COMMON_LABELS.forEach((key, label) -> options.add(new FieldOption(key, label, "公共")));
        MaterialMasterCatalog.Configuration config = categoryId == null ? null : catalog.optionalConfiguration(categoryId);
        if (config != null) {
            config.schema().fields().forEach(field -> options.add(new FieldOption(field.key(), field.label(), "属性")));
        }
        return options;
    }

    private String categoryPrefixOf(Long categoryId) {
        var config = catalog.optionalConfiguration(categoryId);
        return config == null ? null : config.category().modelPrefix();
    }

    /** 品牌字典显示名（命名里显示中文，dev-20261105-004）：取不到时返回 null，回落存库代码。 */
    private String brandLabelOf(InventoryMaterial material) {
        if (material == null || material.getBrand() == null || material.getBrand().isBlank()) return null;
        try {
            String label = catalog.dictionaryLabel("brand", material.getBrand(), false);
            return label == null || label.isBlank() ? null : label;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 品牌缩写（字典项 ext_data.abbr，dev-20261107-014）：未配置返回 null，命名规则回落到中文名。 */
    private String brandAbbrOf(InventoryMaterial material) {
        if (material == null || material.getBrand() == null || material.getBrand().isBlank()) return null;
        try {
            return catalog.brandAbbreviation(material.getBrand());
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 颜色字典显示名：字典缺失或读取失败时由命名规则回退原值。 */
    private String colorLabelOf(InventoryMaterial material) {
        if (material == null || material.getColor() == null || material.getColor().isBlank()) return null;
        try {
            String label = catalog.dictionaryLabel("color", material.getColor(), false);
            return label == null || label.isBlank() ? null : label;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 品类名（供 category 字段使用，失败返回 null）。 */
    private String categoryNameOf(Long categoryId) {
        if (categoryId == null) return null;
        MaterialMasterCatalog.Configuration configuration = catalog.optionalConfiguration(categoryId);
        return configuration == null ? null : configuration.category().categoryName();
    }

    private String generateSpecification(MaterialAttributeSchema schema, InventoryMaterial material) {
        Map<String, Object> attributes = readAttributes(material.getAttributeJson());
        if (schema.hasSpecPattern()) {
            return schema.specification(material, attributes, categoryNameOf(material.getCategoryId()));
        }
        String legacyThickness = material.getGrammage() != null ? material.getGrammage().stripTrailingZeros().toPlainString()
                : material.getThicknessMm() == null ? null : material.getThicknessMm().stripTrailingZeros().toPlainString();
        return schema.compose(attributes, legacyThickness, material.getSize());
    }

    /** 无状态渲染：用当前表单值按品类已保存的规格模板生成规格文本，不写库（dev-20261029-027）。 */
    public String renderSpecification(Long categoryId, MaterialMasterDTO draft) {
        if (categoryId == null) throw new BusinessException("请选择末级品类");
        MaterialMasterCatalog.Configuration config = catalog.optionalConfiguration(categoryId);
        if (config == null || draft == null) return null;
        MaterialAttributeSchema schema = config.schema();
        Map<String, Object> attributes = draft.getAttributeJson() == null ? Map.of() : draft.getAttributeJson();
        if (schema.hasSpecPattern()) {
            InventoryMaterial draftMaterial = new InventoryMaterial();
            draftMaterial.setWidthMm(draft.getWidthMm());
            draftMaterial.setLengthMm(draft.getLengthMm());
            draftMaterial.setThicknessMm(draft.getThicknessMm());
            draftMaterial.setGrammage(draft.getGrammage());
            draftMaterial.setThicknessTolerance(draft.getThicknessTolerance());
            draftMaterial.setSpecBasis(draft.getSpecBasis());
            draftMaterial.setModel(draft.getModel());
            draftMaterial.setColor(draft.getColor());
            draftMaterial.setBrand(draft.getBrand());
            draftMaterial.setMaterialMeta(categoryDefinesMaterial(schema) ? schema.defaultMeta() : draft.getMaterialMeta());
            draftMaterial.setMaterialForm(draft.getMaterialForm());
            draftMaterial.setUnit(draft.getUnit());
            draftMaterial.setSupplierName(draft.getSupplierName());
            draftMaterial.setSupplierMaterialName(draft.getSupplierMaterialName());
            draftMaterial.setRemark(draft.getRemark());
            draftMaterial.setSearchKeywords(draft.getSearchKeywords());
            draftMaterial.setIsCustomerSupplied(draft.getIsCustomerSupplied());
            draftMaterial.setIsSample(draft.getIsSample());
            return schema.specification(draftMaterial, attributes, config.category().categoryName());
        }
        String legacyThickness = draft.getGrammage() != null ? draft.getGrammage().stripTrailingZeros().toPlainString()
                : draft.getThicknessMm() == null ? null : draft.getThicknessMm().stripTrailingZeros().toPlainString();
        return schema.compose(attributes, legacyThickness,
                com.jjx.inventory.master.MaterialSizeValue.render(draft.getWidthMm(), draft.getLengthMm(), draft.getThicknessMm(), null));
    }

    /** 无状态渲染：用“当前表单值”按该品类已保存的命名规则生成名称，不写库（dev-20261029-025）。 */
    public String renderNaming(Long categoryId, MaterialMasterDTO draft) {
        if (categoryId == null) throw new BusinessException("请选择末级品类");
        MaterialMasterCatalog.Configuration config = catalog.optionalConfiguration(categoryId);
        if (config == null) return null;
        MaterialAttributeSchema schema = config.schema();
        MaterialNamingPattern pattern = schema.namePattern();
        return renderPattern(config, draft, pattern);
    }

    /** 型号只读渲染；可传尚未保存的规则供配置页预览。 */
    public String renderModel(Long categoryId, MaterialMasterDTO draft, MaterialNamingPattern pattern) {
        if (categoryId == null) throw new BusinessException("请选择末级品类");
        var config = catalog.optionalConfiguration(categoryId);
        if (config == null) return null;
        var rule = pattern == null ? config.schema().modelPattern() : pattern;
        if (rule != null) {
            rule.validateModel(config.schema());
            if (!rule.normalized().fields().isEmpty()) MaterialNamingPattern.requireCategoryPrefix(config.category().modelPrefix());
        }
        return renderPattern(config, draft, rule == null ? null : rule.forModel());
    }

    private String renderPattern(MaterialMasterCatalog.Configuration config, MaterialMasterDTO draft,
                                 MaterialNamingPattern pattern) {
        if (pattern == null || pattern.normalized().fields().isEmpty()) return null;
        MaterialAttributeSchema schema = config.schema();
        InventoryMaterial material = new InventoryMaterial();
        material.setCategoryId(config.category().categoryId());
        if (draft != null) {
            material.setBrand(draft.getBrand());
            material.setModel(draft.getModel());
            material.setColor(draft.getColor());
            material.setUnit(fallback(draft.getUnit(), schema.defaultUnit()));
            material.setWidthMm(draft.getWidthMm());
            material.setLengthMm(draft.getLengthMm());
            material.setThicknessMm(draft.getThicknessMm());
            material.setGrammage(draft.getGrammage());
            material.setSupplierName(draft.getSupplierName());
            if (draft.getSupplierId() != null) {
                var supplier = supplierMapper.selectById(draft.getSupplierId());
                if (supplier != null) material.setSupplierName(supplier.getSupplierName());
            }
            material.setIsCustomerSupplied(draft.getIsCustomerSupplied());
            material.setIsSample(draft.getIsSample());
            material.setMaterialMeta(resolveMaterialMeta(draft.getMaterialMeta(), schema));
            material.setMaterialForm(fallback(draft.getMaterialForm(), schema.defaultForm()));
            try {
                material.setAttributeJson(objectMapper.writeValueAsString(
                        draft.getAttributeJson() == null ? Map.of() : draft.getAttributeJson()));
            } catch (Exception e) {
                material.setAttributeJson("{}");
            }
        }
        return pattern.compose(material, schema, config.category().categoryName(), brandLabelOf(material), colorLabelOf(material), categoryPrefixOf(material.getCategoryId()), brandAbbrOf(material));
    }

    /** 命名规则预览（只读）：按品类取样 5 条物料，用传入规则试算新旧名对照。 */
    public NamingPreview previewNaming(Long categoryId, MaterialNamingPattern pattern) {
        if (categoryId == null) throw new BusinessException("请选择末级品类");
        MaterialMasterCatalog.Configuration config = catalog.optionalConfiguration(categoryId);
        if (config == null) throw new BusinessException("该品类未配置属性模板");
        MaterialAttributeSchema schema = config.schema();
        MaterialNamingPattern normalized = pattern == null ? MaterialNamingPattern.defaults() : pattern.normalized();
        normalized.validate(schema);
        List<InventoryMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<InventoryMaterial>()
                .eq(InventoryMaterial::getCategoryId, categoryId)
                .orderByDesc(InventoryMaterial::getMaterialId)
                .last("LIMIT 5"));
        List<NamingSample> samples = new ArrayList<>();
        for (InventoryMaterial material : materials) {
            samples.add(new NamingSample(material.getMaterialId(), material.getMaterialCode(),
                    material.getMaterialName(), normalized.compose(material, schema, config.category().categoryName(), brandLabelOf(material), colorLabelOf(material), categoryPrefixOf(material.getCategoryId()), brandAbbrOf(material))));
        }
        return new NamingPreview(samples, materials.isEmpty() ? "该品类暂无物料，规则只影响后续新建" : "预览取该品类最近 5 条物料");
    }
    public record Prepared(InventoryMaterial material, List<Long> tagIds) { }
    public record SpecificationPreview(String specification, boolean generated, String message) { }

    /** 命名补丁不接收主数据表单，避免绕过模板校验修改其它字段。 */
    public record NamingPatch(String materialName, String specification, String reason) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectOtherField(String field, Object value) {
            throw new BusinessException("命名补丁不支持字段：" + field);
        }
    }
    public record NamingResult(InventoryMaterial material, String detail, SpecificationPreview specificationGeneration) { }

    public SpecificationPreview specificationPreview(InventoryMaterial material) {
        return specificationPreview(material, catalog.specificationSchema(material.getCategoryId()));
    }

    /** 只读原始事实；BOM 的显示组合规则由前端维护。 */
    public Map<String, Object> specificationSource(InventoryMaterial material) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("size", material == null ? null : material.getSize());
        source.put("attributes", material == null ? Map.of() : readAttributes(material.getAttributeJson()));
        source.put("schema", material == null ? null : catalog.specificationSchema(material.getCategoryId()));
        return source;
    }

    private SpecificationPreview specificationPreview(InventoryMaterial material, MaterialAttributeSchema schema) {
        if (schema == null || schema.fields().isEmpty() && !schema.hasSpecPattern())
            return new SpecificationPreview(material.getSpecification(), false, "未生成：未归类、分类无启用模板或模板无属性，保留原规格；不接受外部 specification 文本");
        Map<String, Object> previewAttributes = readAttributes(material.getAttributeJson());
        String legacyThickness = material.getGrammage() != null ? material.getGrammage().stripTrailingZeros().toPlainString()
                : material.getThicknessMm() == null ? null : material.getThicknessMm().stripTrailingZeros().toPlainString();
        String specification = schema.hasSpecPattern()
                ? schema.specification(material, previewAttributes, categoryNameOf(material.getCategoryId()))
                : schema.compose(previewAttributes, legacyThickness, material.getSize());
        // 模板字段当前全无值时规格模板渲染结果是 null 或空串（MaterialAttributeSchema.specification:210），
        // 统一归一为 null，避免调用方解引用空值（dev-20261031-001）。
        if (specification == null || specification.isBlank())
            return new SpecificationPreview(null, false, "未生成：规格模板字段当前均无值，规格留空；不接受外部 specification 文本");
        return new SpecificationPreview(specification, true, "规格已由模板属性/厚度/尺寸生成；不接受外部 specification 文本");
    }

    @Transactional(rollbackFor = Exception.class)
    public NamingResult updateNaming(Long id, NamingPatch patch, String operator) {
        if (patch == null) throw new BusinessException("命名补丁不能为空");
        String name = text(patch.materialName(), "物料名称", 200, true);
        String reason = text(patch.reason(), "重算依据", 1000, false);
        InventoryMaterial existing = masterMapper.lockMaterial(id);
        if (existing == null) throw new BusinessException("物料不存在");
        SpecificationPreview preview = specificationPreview(existing);
        String specification = preview.specification(); // patch.specification 故意忽略，只允许模型渲染结果。
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(existing.getMaterialName(), name))
            changes.add("名称：" + Objects.toString(existing.getMaterialName(), "空") + " → " + name);
        if (!Objects.equals(existing.getSpecification(), specification))
            changes.add("规格：" + Objects.toString(existing.getSpecification(), "空") + " → " + Objects.toString(specification, "空"));
        if (changes.isEmpty()) return new NamingResult(existing, com.jjx.system.utils.OperLogDetailBuilder.changes(changes), preview);
        UpdateWrapper<InventoryMaterial> update = new UpdateWrapper<>();
        update.eq("material_id", id).set("material_name", name).set("specification", specification)
                .set("update_by", operator).set("update_time", LocalDateTime.now());
        if (materialMapper.update(null, update) != 1) throw new BusinessException("物料命名修改失败");
        if (reason != null) changes.add("重算依据：" + reason);
        var payload = EventPublishSupport.payload("material", id, existing.getMaterialCode());
        payload.put("materialName", name);
        EventPublishSupport.fireAfterCommit(eventPublisher, "inventory.material.updated", payload);
        return new NamingResult(materialMapper.selectById(id), com.jjx.system.utils.OperLogDetailBuilder.changes(changes), preview);
    }

    public Detail detail(Long id) {
        InventoryMaterial material = materialMapper.selectById(id);
        if (material == null) throw new BusinessException("物料不存在");
        MaterialAttributeSchema schema = catalog.specificationSchema(material.getCategoryId());
        MaterialMasterCatalog.Configuration config = schema == null ? null : catalog.optionalConfiguration(material.getCategoryId());
        return new Detail(material, readAttributes(material.getAttributeJson()), config,
                tagService.getBizTags("inventory_material", id), MaterialSourceValues.from(material), specificationPreview(material, schema));
    }

    public Map<String, Object> readAttributes(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> attributes = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
            // material是材质的存储镜像，表单字段仍由模板校验，避免把镜像当作品类自定义属性。
            attributes.remove("material");
            return attributes;
        }
        catch (Exception e) { throw new BusinessException("物料属性数据格式错误"); }
    }

    public List<InventoryMaterial> selectForExport(com.jjx.inventory.dto.query.MaterialQueryDTO query) {
        return materialService.selectEntities(query);
    }

    /** 材质已由品类唯一确定；保存、预览与 Excel 共用模板判定。 */
    static boolean categoryDefinesMaterial(MaterialAttributeSchema schema) {
        return schema != null && schema.metaRequired() && schema.metaOptions().size() == 1
                && Objects.equals(schema.defaultMeta(), schema.metaOptions().get(0));
    }

    private static String resolveMaterialMeta(String input, MaterialAttributeSchema schema) {
        return categoryDefinesMaterial(schema) ? schema.defaultMeta() : fallback(input, schema.defaultMeta());
    }

    /** 同时供保存和导入预检使用，不写库、不生成业务编号。 */
    public Prepared prepare(MaterialMasterDTO dto) {
        if (dto == null) throw new BusinessException("物料不能为空");
        InventoryMaterial old = dto.getMaterialId() == null ? null : materialMapper.selectById(dto.getMaterialId());
        if (dto.getMaterialId() != null && old == null) throw new BusinessException("待更新物料不存在");
        MaterialMasterCatalog.Configuration config = catalog.optionalConfiguration(dto.getCategoryId());
        MaterialAttributeSchema schema = config == null
                ? new MaterialAttributeSchema(1, List.of(), false, null, null, "PCS", List.of(), null) : config.schema();
        String name = text(dto.getMaterialName(), "物料名称", 200, true);
        String meta = resolveMaterialMeta(dto.getMaterialMeta(), schema);
        if (meta == null && schema.metaOptions().size() == 1) meta = schema.metaOptions().get(0);
        if (config != null && meta != null && !schema.metaOptions().contains(meta)) throw new BusinessException("材质不在该品类允许范围");
        if (meta == null && schema.metaRequired()) throw new BusinessException("请选择材质");
        String form = fallback(dto.getMaterialForm(), schema.defaultForm());
        String unit = fallback(dto.getUnit(), schema.defaultUnit());
        var dictionaries = catalog.dictionaries();
        String metaLabel = dictionaryLabel(dictionaries, "material_meta", meta, false);
        String formLabel = dictionaryLabel(dictionaries, "form", form, false);
        dictionaryLabel(dictionaries, "unit", unit, true);
        dictionaryLabel(dictionaries, "color", dto.getColor(), false);
        dictionaryLabel(dictionaries, "brand", dto.getBrand(), false);
        Map<String, Object> inputAttributes = new LinkedHashMap<>(dto.getAttributeJson() == null ? Map.of() : dto.getAttributeJson());
        MaterialSizeValue.SizeSpec sizeSpec = MaterialSizeValue.validate(dto.getWidthMm(), dto.getLengthMm(), dto.getThicknessMm(), null);
        String specBasis = dto.getGrammage() != null ? "GRAMMAGE"
                : sizeSpec.thicknessMm() != null ? "THICKNESS" : "SIZE";
        if (dto.getGrammage() != null && dto.getGrammage().signum() <= 0) throw new BusinessException("克重必须大于0");
        if ("GRAMMAGE".equals(specBasis)) {
            if (sizeSpec.thicknessMm() != null) throw new BusinessException("克重与厚度只能选一种口径，请清掉厚度");
            if (sizeSpec.widthMm() == null) throw new BusinessException("克重口径仍需填宽度");
        }
        // 厚度口径不要求宽度（只填厚度合法，dev-20261030-016）；克重口径仍需宽度。
        // 厚度属性是历史模板定义，不能再成为尺寸写入入口。
        inputAttributes.remove("thickness_mm");
        Map<String, Object> attributes = schema.validateAttributes(inputAttributes, objectMapper);
        InventoryMaterial material = new InventoryMaterial();
        BeanUtils.copyProperties(dto, material, "sizeUnit");
        material.setMaterialName(name);
        material.setMaterialMeta(meta);
        material.setMaterialForm(form);
        material.setMaterialGrade(text(metaLabel + formLabel, "材料显示名", 30, false));
        material.setUnit(unit);
        material.setModel(text(dto.getModel(), "型号", 100, false));
        material.setBrand(text(dto.getBrand(), "品牌", 50, false));
        material.setColor(text(dto.getColor(), "颜色", 20, false));
        material.setWidthMm(sizeSpec == null ? null : sizeSpec.widthMm());
        material.setLengthMm(sizeSpec == null ? null : sizeSpec.lengthMm());
        material.setThicknessMm(sizeSpec == null ? null : sizeSpec.thicknessMm());
        material.setSpecBasis(specBasis);
        material.setGrammage(dto.getGrammage());
        material.setThicknessTolerance(dto.getThicknessTolerance());
        material.setSearchKeywords(text(dto.getSearchKeywords(), "别名", 500, false));
        material.setRemark(text(dto.getRemark(), "业务备注", 1000, false));
        material.setSpecification(old == null ? null : old.getSpecification());
        material.setIsCustomerSupplied(Boolean.TRUE.equals(dto.getIsCustomerSupplied()));
        material.setIsSample(Boolean.TRUE.equals(dto.getIsSample()));
        if (config != null) MaterialAttributeColumns.apply(material, config.template().getTemplateId());
        else material.setTemplateId(null);
        Map<String, Object> storedAttributes = new LinkedHashMap<>(attributes);
        storedAttributes.remove("thickness_mm");
        if (meta != null) storedAttributes.put("material", meta);
        try { material.setAttributeJson(objectMapper.writeValueAsString(storedAttributes)); }
        catch (Exception e) { throw new BusinessException("属性无法序列化"); }
        if (dto.getSupplierId() != null) {
            var supplier = supplierMapper.selectById(dto.getSupplierId());
            if (supplier == null) throw new BusinessException("供应商不存在");
            material.setSupplierName(supplier.getSupplierName());
        } else material.setSupplierName(text(dto.getSupplierName(), "供应商", 200, false));
        material.setSupplierMaterialName(text(dto.getSupplierMaterialName(), "供应商品名", 200, false));
        if (old == null && material.getModel() == null && schema.modelPattern() != null) {
            schema.modelPattern().validateModel(schema);
            if (!schema.modelPattern().normalized().fields().isEmpty()) MaterialNamingPattern.requireCategoryPrefix(config.category().modelPrefix());
            material.setModel(text(schema.modelPattern().forModel().compose(material, schema,
                    config.category().categoryName(), brandLabelOf(material), colorLabelOf(material), categoryPrefixOf(material.getCategoryId()), brandAbbrOf(material)), "型号", 100, false));
        }
        material.setSpecification(specificationPreview(material, config == null ? null : schema).specification());
        for (BigDecimal number : Arrays.asList(dto.getSafeStock(), dto.getMaxStock(), dto.getReorderPoint(), dto.getStandardPrice())) {
            if (number != null && (number.signum() < 0 || number.stripTrailingZeros().scale() > 4 || number.precision() - number.scale() > 8)) throw new BusinessException("库存及价格参数须为有效的非负数，最多4位小数");
        }
        for (Integer days : Arrays.asList(dto.getLeadTime(), dto.getShelfLife(), dto.getExpiryAlertDays())) {
            if (days != null && days < 0) throw new BusinessException("天数参数不能为负数");
        }
        if (dto.getSafeStock() != null && dto.getMaxStock() != null && dto.getMaxStock().signum() > 0 && dto.getSafeStock().compareTo(dto.getMaxStock()) > 0) throw new BusinessException("安全库存不能高于最高库存");
        List<Long> tags = dto.getTagIds() == null ? List.of() : dto.getTagIds().stream().distinct().toList();
        if (!tags.isEmpty()) {
            Set<Long> allowed = new HashSet<>(tagService.listTags("material_attribute", null, StatusEnum.NORMAL.getCode())
                    .stream().map(SysTag::getTagId).toList());
            if (!allowed.containsAll(tags)) throw new BusinessException("物料标签不存在或已停用");
        }
        validateRequiredColumns(material, config, dto.getMaterialId() == null, Boolean.TRUE.equals(dto.getFromBom()));
        return new Prepared(material, tags);
    }

    /** 品类必填列（inventory_material_required_field，已按现分类树挂码）；新建时强制，编辑不追溯历史行。 */
    private void validateRequiredColumns(InventoryMaterial material, MaterialMasterCatalog.Configuration config,
                                         boolean creating, boolean fromBom) {
        if (!creating || config == null) return;
        Map<String, String> labels = Map.of(
                "material_grade", "材料显示名", "model", "型号", "color", "颜色",
                "specification", "规格", "brand", "品牌");
        for (String column : materialMapper.selectRequiredFields(config.category().categoryCode())) {
            // BOM 快速建档场景颜色常未知：该入口不强制颜色，其它必填照常（dev-20261029-016）
            if (fromBom && "color".equals(column)) continue;
            String value = switch (column) {
                case "material_grade" -> material.getMaterialGrade();
                case "model" -> material.getModel();
                case "color" -> material.getColor();
                case "specification" -> material.getSpecification();
                case "brand" -> material.getBrand();
                default -> null;
            };
            if (value == null || value.isBlank()) {
                throw new BusinessException(labels.getOrDefault(column, column) + "不能为空（"
                        + config.category().categoryName() + " 必填）");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryMaterial save(MaterialMasterDTO dto, String operator) {
        return save(dto, operator, com.jjx.inventory.classify.MaterialClassSource.MANUAL);
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryMaterial save(MaterialMasterDTO dto, String operator, com.jjx.inventory.classify.MaterialClassSource source) {
        if (dto == null) throw new BusinessException("物料不能为空");
        // 与模板维护取得相同顺序的锁，防止校验通过后模板被改写。
        if (dto.getCategoryId() != null) {
            if (masterMapper.lockCategory(dto.getCategoryId()) == null) throw new BusinessException("分类不存在");
            masterMapper.lockTemplate(dto.getCategoryId());
        }
        Prepared prepared = prepare(dto);
        InventoryMaterial material = prepared.material();
        boolean creating = material.getMaterialId() == null;
        InventoryMaterial existing = creating ? null : masterMapper.lockMaterial(material.getMaterialId());
        if (!creating && existing == null) throw new BusinessException("物料不存在");
        if (existing != null && source != com.jjx.inventory.classify.MaterialClassSource.MANUAL && Boolean.TRUE.equals(existing.getClassConfirmed()))
            throw new BusinessException("人工确认物料不能被导入覆盖");
        if (existing != null) preserveParameters(material, existing, dto);
        // 型号不再做唯一校验（2026-11-07 dev-20261107-003）：同型号可对应不同供应商/规格；
        // 手工保存与导入共用同一判重口径：只有各关键字段（含供应商）完全相同才拦（dev-20261107-008）。
        requireNoDuplicate(material);
        if (existing != null && !Objects.equals(existing.getUnit(), material.getUnit()) && masterMapper.transactionCount(existing.getMaterialId()) > 0) throw new BusinessException("已有库存流水，不能直接修改基本计量单位");
        material.setClassSource(material.getCategoryId() == null ? null : source.name());
        // 显式选择品类的表单/导入均已按模板校验；只有规则推断需要后续人工确认。
        material.setClassConfirmed(material.getCategoryId() != null && source != com.jjx.inventory.classify.MaterialClassSource.RULE);
        material.setUpdateBy(operator);
        material.setUpdateTime(LocalDateTime.now());
        if (creating) {
            String code = text(dto.getMaterialCode(), "物料编码", 50, false);
            material.setMaterialCode(code == null ? (material.getCategoryId() == null ? materialService.generateUnclassifiedCode() : materialService.generateCode(material.getCategoryId())) : code);
            if (materialService.existsByCode(material.getMaterialCode())) throw new BusinessException("物料编码已存在");
            material.setStatus(StatusEnum.NORMAL.getCode());
            material.setCreateBy(operator);
            material.setCreateTime(LocalDateTime.now());
            if (materialMapper.insert(material) != 1) throw new BusinessException("物料新增失败");
        } else {
            material.setMaterialCode(existing.getMaterialCode());
            UpdateWrapper<InventoryMaterial> update = new UpdateWrapper<>();
            update.eq("material_id", material.getMaterialId());
            // 完整表单显式SET空值；库存余额和历史单据不在本次写入列中。
            Map<String, Object> columns = new LinkedHashMap<>();
            columns.put("category_id", material.getCategoryId());
            columns.put("class_source", material.getClassSource()); columns.put("class_confirmed", material.getClassConfirmed());
            columns.put("material_name", material.getMaterialName()); columns.put("material_meta", material.getMaterialMeta());
            columns.put("material_form", material.getMaterialForm()); columns.put("material_grade", material.getMaterialGrade());
            columns.put("model", material.getModel()); columns.put("color", material.getColor()); columns.put("brand", material.getBrand());
            columns.put("width_mm", material.getWidthMm()); columns.put("length_mm", material.getLengthMm());
            columns.put("thickness_mm", material.getThicknessMm());
            columns.put("grammage", material.getGrammage()); columns.put("thickness_tolerance", material.getThicknessTolerance());
            columns.put("spec_basis", material.getSpecBasis()); columns.put("unit", material.getUnit());
            columns.put("template_id", material.getTemplateId());
            columns.put("attribute_json", material.getAttributeJson()); columns.put("specification", material.getSpecification());
            columns.put("search_keywords", material.getSearchKeywords());
            columns.put("is_customer_supplied", material.getIsCustomerSupplied()); columns.put("is_sample", material.getIsSample());
            columns.put("supplier_id", material.getSupplierId()); columns.put("supplier_name", material.getSupplierName());
            columns.put("supplier_material_name", material.getSupplierMaterialName());
            columns.put("remark", material.getRemark()); columns.put("safe_stock", material.getSafeStock());
            columns.put("max_stock", material.getMaxStock()); columns.put("reorder_point", material.getReorderPoint());
            columns.put("standard_price", material.getStandardPrice()); columns.put("lead_time", material.getLeadTime());
            columns.put("batch_control", material.getBatchControl()); columns.put("shelf_life", material.getShelfLife());
            columns.put("expiry_alert_days", material.getExpiryAlertDays());
            columns.put("default_warehouse_id", material.getDefaultWarehouseId()); columns.put("default_location_id", material.getDefaultLocationId());
            columns.put("update_by", operator); columns.put("update_time", material.getUpdateTime());
            columns.forEach(update::set);
            if (materialMapper.update(null, update) != 1) throw new BusinessException("物料修改失败");
        }
        List<Long> tagIds = existing != null && dto.getTagIds() == null
                ? tagService.getBizTags("inventory_material", existing.getMaterialId()).stream().map(SysTag::getTagId).toList()
                : prepared.tagIds();
        tagService.setBizTags("inventory_material", material.getMaterialId(), tagIds, operator);
        var payload = EventPublishSupport.payload("material", material.getMaterialId(), material.getMaterialCode());
        payload.put("materialName", material.getMaterialName());
        EventPublishSupport.fireAfterCommit(eventPublisher, creating ? "inventory.material.created" : "inventory.material.updated", payload);
        return materialMapper.selectById(material.getMaterialId());
    }

    public static void validateSize(String size) {
        MaterialSizeValue.validate(size);
    }

    /**
     * 重复物料判重键：品类+名称+材质+形态+型号+颜色+品牌+规格+单位+属性+供应商（2026-11-07 dev-20261107-008）。
     * 导入的文件内/库内判重与手工保存共用这一个键，避免两套口径。
     */
    public String duplicateKey(InventoryMaterial material) {
        String supplier = material.getSupplierName() == null || material.getSupplierName().isBlank()
                ? null : material.getSupplierName().trim();
        try {
            return objectMapper.writeValueAsString(Arrays.asList(material.getCategoryId(), material.getMaterialName(),
                    material.getMaterialMeta(), material.getMaterialForm(), material.getModel(), material.getColor(),
                    material.getBrand(), material.getSize(), material.getUnit(), supplier,
                    readAttributes(material.getAttributeJson())));
        } catch (Exception e) { throw new BusinessException("物料重复性检查失败"); }
    }

    /** 手工新增/编辑用：同名候选里若判重键完全一致则拦；排除自身。 */
    private void requireNoDuplicate(InventoryMaterial material) {
        if (material.getMaterialName() == null || material.getMaterialName().isBlank()) return;
        var query = new LambdaQueryWrapper<InventoryMaterial>()
                .eq(InventoryMaterial::getMaterialName, material.getMaterialName())
                .ne(material.getMaterialId() != null, InventoryMaterial::getMaterialId, material.getMaterialId());
        String key = duplicateKey(material);
        for (InventoryMaterial other : materialMapper.selectList(query))
            if (key.equals(duplicateKey(other)))
                throw new BusinessException("与已有物料重复（品类/名称/材质/形态/型号/颜色/品牌/规格/单位/属性/供应商完全相同）："
                        + material.getMaterialName());
    }

    private static void preserveParameters(InventoryMaterial material, InventoryMaterial old, MaterialMasterDTO dto) {
        if (dto.getSafeStock() == null) material.setSafeStock(old.getSafeStock());
        if (dto.getMaxStock() == null) material.setMaxStock(old.getMaxStock());
        if (dto.getReorderPoint() == null) material.setReorderPoint(old.getReorderPoint());
        if (dto.getStandardPrice() == null) material.setStandardPrice(old.getStandardPrice());
        if (dto.getLeadTime() == null) material.setLeadTime(old.getLeadTime());
        if (dto.getShelfLife() == null) material.setShelfLife(old.getShelfLife());
        if (dto.getExpiryAlertDays() == null) material.setExpiryAlertDays(old.getExpiryAlertDays());
        if (dto.getBatchControl() == null) material.setBatchControl(old.getBatchControl());
        if (dto.getDefaultWarehouseId() == null) material.setDefaultWarehouseId(old.getDefaultWarehouseId());
        if (dto.getDefaultLocationId() == null) material.setDefaultLocationId(old.getDefaultLocationId());
    }

    private static String fallback(String value, String defaultValue) { return value == null ? defaultValue : value.isBlank() ? null : value.trim(); }
    private static String dictionaryLabel(Map<String, List<MaterialMasterCatalog.DictionaryOption>> dictionaries, String dict, String code, boolean required) {
        String label = Map.of("material_meta","材质","form","形态","unit","单位","color","颜色","brand","品牌").get(dict);
        if (code == null || code.isBlank()) { if (required) throw new BusinessException("请选择" + label); return ""; }
        return dictionaries.getOrDefault(dict,List.of()).stream().filter(o -> o.code().equals(code)).map(MaterialMasterCatalog.DictionaryOption::label)
                .findFirst().orElseThrow(() -> new BusinessException(label + "选项不存在或已停用：" + code));
    }
    private static String text(String value, String label, int length, boolean required) {
        String result = value == null || value.isBlank() ? null : value.trim();
        if (required && result == null) throw new BusinessException(label + "不能为空");
        if (result != null && result.length() > length) throw new BusinessException(label + "不能超过" + length + "字");
        return result;
    }
}
