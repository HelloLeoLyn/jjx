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
        for (InventoryMaterial material : materials) {
            String generated = pattern.compose(material, schema);
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
        for (InventoryMaterial material : materials) {
            String generated = pattern.compose(material, schema);
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
                    material.getMaterialName(), normalized.compose(material, schema)));
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
        if (schema == null || schema.fields().isEmpty())
            return new SpecificationPreview(material.getSpecification(), false, "未生成：未归类、分类无启用模板或模板无属性，保留原规格；不接受外部 specification 文本");
        String specification = schema.compose(readAttributes(material.getAttributeJson()), material.getGrammage() != null ? material.getGrammage().stripTrailingZeros().toPlainString()
                : material.getThicknessMm() == null ? null : material.getThicknessMm().stripTrailingZeros().toPlainString(), material.getSize());
        return new SpecificationPreview(specification.isEmpty() ? null : specification, true, "规格已由模板属性/厚度/尺寸生成；不接受外部 specification 文本");
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

    /** 同时供保存和导入预检使用，不写库、不生成业务编号。 */
    public Prepared prepare(MaterialMasterDTO dto) {
        if (dto == null) throw new BusinessException("物料不能为空");
        InventoryMaterial old = dto.getMaterialId() == null ? null : materialMapper.selectById(dto.getMaterialId());
        if (dto.getMaterialId() != null && old == null) throw new BusinessException("待更新物料不存在");
        MaterialMasterCatalog.Configuration config = catalog.optionalConfiguration(dto.getCategoryId());
        MaterialAttributeSchema schema = config == null
                ? new MaterialAttributeSchema(1, List.of(), false, null, null, "PCS", List.of(), null) : config.schema();
        String name = text(dto.getMaterialName(), "物料名称", 200, true);
        String meta = fallback(dto.getMaterialMeta(), schema.defaultMeta());
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
        MaterialSizeValue.SizeSpec sizeSpec = MaterialSizeValue.validate(dto.getWidthMm(), dto.getLengthMm(), dto.getThicknessMm(), dto.getSizeUnit());
        String specBasis = dto.getGrammage() != null ? "GRAMMAGE"
                : sizeSpec.thicknessMm() != null ? "THICKNESS" : "SIZE";
        if (dto.getGrammage() != null && dto.getGrammage().signum() <= 0) throw new BusinessException("克重必须大于0");
        if ("GRAMMAGE".equals(specBasis)) {
            if (sizeSpec.thicknessMm() != null) throw new BusinessException("克重与厚度只能选一种口径，请清掉厚度");
            if (sizeSpec.widthMm() == null) throw new BusinessException("克重口径仍需填宽度");
        }
        if ("THICKNESS".equals(specBasis) && sizeSpec.widthMm() == null) throw new BusinessException("填了厚度时仍需填宽度");
        // 厚度属性是历史模板定义，不能再成为尺寸写入入口。
        inputAttributes.remove("thickness_mm");
        Map<String, Object> attributes = schema.validateAttributes(inputAttributes, objectMapper);
        InventoryMaterial material = new InventoryMaterial();
        BeanUtils.copyProperties(dto, material);
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
        material.setSizeUnit(sizeSpec == null ? null : sizeSpec.unit());
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
        material.setSpecification(specificationPreview(material, config == null ? null : schema).specification());
        if (dto.getSupplierId() != null) {
            var supplier = supplierMapper.selectById(dto.getSupplierId());
            if (supplier == null) throw new BusinessException("供应商不存在");
            material.setSupplierName(supplier.getSupplierName());
        } else material.setSupplierName(text(dto.getSupplierName(), "供应商", 200, false));
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
        validateRequiredColumns(material, config, dto.getMaterialId() == null);
        return new Prepared(material, tags);
    }

    /** 品类必填列（inventory_material_required_field，已按现分类树挂码）；新建时强制，编辑不追溯历史行。 */
    private void validateRequiredColumns(InventoryMaterial material, MaterialMasterCatalog.Configuration config,
                                         boolean creating) {
        if (!creating || config == null) return;
        Map<String, String> labels = Map.of(
                "material_grade", "材料显示名", "model", "型号", "color", "颜色",
                "specification", "规格", "brand", "品牌");
        for (String column : materialMapper.selectRequiredFields(config.category().categoryCode())) {
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
            columns.put("thickness_mm", material.getThicknessMm()); columns.put("size_unit", material.getSizeUnit());
            columns.put("grammage", material.getGrammage()); columns.put("thickness_tolerance", material.getThicknessTolerance());
            columns.put("spec_basis", material.getSpecBasis()); columns.put("unit", material.getUnit());
            columns.put("template_id", material.getTemplateId());
            columns.put("attribute_json", material.getAttributeJson()); columns.put("specification", material.getSpecification());
            columns.put("search_keywords", material.getSearchKeywords());
            columns.put("is_customer_supplied", material.getIsCustomerSupplied()); columns.put("is_sample", material.getIsSample());
            columns.put("supplier_id", material.getSupplierId()); columns.put("supplier_name", material.getSupplierName());
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
