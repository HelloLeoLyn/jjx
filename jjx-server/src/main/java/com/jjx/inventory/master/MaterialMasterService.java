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
                         MaterialMasterCatalog.Configuration configuration, List<SysTag> tags, MaterialSourceValues source) { }
    public record Prepared(InventoryMaterial material, List<Long> tagIds) { }

    public Detail detail(Long id) {
        InventoryMaterial material = materialMapper.selectById(id);
        if (material == null) throw new BusinessException("物料不存在");
        MaterialMasterCatalog.Configuration config = null;
        if (material.getOriginalName() != null) config = catalog.configuration(material.getCategoryId());
        return new Detail(material, readAttributes(material.getAttributeJson()), config,
                tagService.getBizTags("inventory_material", id), MaterialSourceValues.from(material));
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
        query.setMasterDataOnly(true);
        return materialService.selectEntities(query);
    }

    /** 同时供保存和导入预检使用，不写库、不生成业务编号。 */
    public Prepared prepare(MaterialMasterDTO dto) {
        if (dto == null || dto.getCategoryId() == null) throw new BusinessException("请选择末级品类");
        if (dto.getMaterialId() != null && materialMapper.selectById(dto.getMaterialId()) == null) throw new BusinessException("待更新物料不存在");
        MaterialMasterCatalog.Configuration config = catalog.configuration(dto.getCategoryId());
        MaterialAttributeSchema schema = config.schema();
        String name = text(dto.getOriginalName(), "原名称", 200, true);
        String originalSpec = text(dto.getOriginalSpecification(), "原规格", 500, false);
        String meta = fallback(dto.getMaterialMeta(), schema.defaultMeta());
        if (meta == null && schema.metaOptions().size() == 1) meta = schema.metaOptions().get(0);
        if (meta != null && !schema.metaOptions().contains(meta)) throw new BusinessException("材质不在该品类允许范围");
        if (meta == null && schema.metaRequired()) throw new BusinessException("请选择材质");
        String form = fallback(dto.getMaterialForm(), schema.defaultForm());
        String unit = fallback(dto.getUnit(), schema.defaultUnit());
        var dictionaries = catalog.dictionaries();
        String metaLabel = dictionaryLabel(dictionaries, "material_meta", meta, false);
        String formLabel = dictionaryLabel(dictionaries, "form", form, false);
        dictionaryLabel(dictionaries, "unit", unit, true);
        dictionaryLabel(dictionaries, "color", dto.getColor(), false);
        dictionaryLabel(dictionaries, "brand", dto.getBrand(), false);
        Map<String, Object> attributes = schema.validateAttributes(dto.getAttributeJson(), objectMapper);
        String supplement = text(dto.getSpecSupplement(), "规格补充", 30, false);
        String specification = schema.compose(attributes);
        if (supplement != null) specification = specification.isEmpty() ? supplement : specification + "；" + supplement;
        if (specification.length() > 500) throw new BusinessException("生成规格超过500字");
        validateSize(dto.getSize());
        InventoryMaterial material = new InventoryMaterial();
        BeanUtils.copyProperties(dto, material);
        material.setOriginalName(name);
        material.setOriginalSpecification(originalSpec);
        material.setMaterialName(name);
        material.setMaterialMeta(meta);
        material.setMaterialForm(form);
        material.setMaterialGrade(text(metaLabel + formLabel, "材料显示名", 30, false));
        material.setMaterialType(config.category().materialType());
        material.setUnit(unit);
        material.setModel(text(dto.getModel(), "型号", 100, false));
        material.setBrand(text(dto.getBrand(), "品牌", 50, false));
        material.setColor(text(dto.getColor(), "颜色", 20, false));
        material.setSize(text(dto.getSize(), "尺寸", 50, false));
        material.setSearchKeywords(text(dto.getSearchKeywords(), "别名", 500, false));
        material.setRemark(text(dto.getRemark(), "业务备注", 1000, false));
        material.setSpecSupplement(supplement);
        material.setSpecification(specification.isEmpty() ? null : specification);
        material.setIsCustomerSupplied(Boolean.TRUE.equals(dto.getIsCustomerSupplied()));
        material.setIsSample(Boolean.TRUE.equals(dto.getIsSample()));
        MaterialAttributeColumns.apply(material, attributes, config.template().getTemplateId());
        Map<String, Object> storedAttributes = new LinkedHashMap<>(attributes);
        if (meta != null) storedAttributes.put("material", meta);
        try { material.setAttributeJson(objectMapper.writeValueAsString(storedAttributes)); }
        catch (Exception e) { throw new BusinessException("属性无法序列化"); }
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
        return new Prepared(material, tags);
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryMaterial save(MaterialMasterDTO dto, String operator) {
        if (dto == null || dto.getCategoryId() == null) throw new BusinessException("请选择末级品类");
        // 与模板维护取得相同顺序的锁，防止校验通过后模板被改写。
        if (masterMapper.lockCategory(dto.getCategoryId()) == null || masterMapper.lockTemplate(dto.getCategoryId()) == null) throw new BusinessException("分类或模板不存在");
        Prepared prepared = prepare(dto);
        InventoryMaterial material = prepared.material();
        boolean creating = material.getMaterialId() == null;
        InventoryMaterial existing = creating ? null : masterMapper.lockMaterial(material.getMaterialId());
        if (!creating && existing == null) throw new BusinessException("物料不存在");
        if (existing != null) preserveParameters(material, existing, dto);
        if (existing != null && !Objects.equals(existing.getUnit(), material.getUnit()) && masterMapper.transactionCount(existing.getMaterialId()) > 0) throw new BusinessException("已有库存流水，不能直接修改基本计量单位");
        material.setUpdateBy(operator);
        material.setUpdateTime(LocalDateTime.now());
        if (creating) {
            String code = text(dto.getMaterialCode(), "物料编码", 50, false);
            material.setMaterialCode(code == null ? materialService.generateCode(material.getMaterialType()) : code);
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
            columns.put("category_id", material.getCategoryId()); columns.put("material_type", material.getMaterialType());
            columns.put("original_name", material.getOriginalName()); columns.put("original_specification", material.getOriginalSpecification());
            columns.put("material_name", material.getMaterialName()); columns.put("material_meta", material.getMaterialMeta());
            columns.put("material_form", material.getMaterialForm()); columns.put("material_grade", material.getMaterialGrade());
            columns.put("model", material.getModel()); columns.put("color", material.getColor()); columns.put("brand", material.getBrand());
            columns.put("size", material.getSize()); columns.put("unit", material.getUnit());
            columns.put("template_id", material.getTemplateId()); columns.put("thickness", material.getThickness());
            columns.put("width_size", material.getWidthSize());
            columns.put("attribute_json", material.getAttributeJson()); columns.put("specification", material.getSpecification());
            columns.put("spec_supplement", material.getSpecSupplement()); columns.put("search_keywords", material.getSearchKeywords());
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
        if (size == null || size.isBlank()) return;
        if (!size.matches("\\s*\\d+(?:\\.\\d+)?(?:\\s*[×xX*]\\s*\\d+(?:\\.\\d+)?)?\\s*")) throw new BusinessException("尺寸仅填写宽或宽×长，单位mm；厚度请填品类属性");
        for (String part : size.trim().split("[×xX*]")) if (new BigDecimal(part.trim()).signum() <= 0) throw new BusinessException("尺寸必须大于0");
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
