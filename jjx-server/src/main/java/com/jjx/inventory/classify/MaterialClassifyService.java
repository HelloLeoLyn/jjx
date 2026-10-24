package com.jjx.inventory.classify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MaterialClassifyService {
    private final MaterialClassifyRuleMapper rules;
    private final InventoryMaterialMapper materials;
    private final MaterialMasterMapper locks;
    private final MaterialMasterCatalog catalog;
    private final ObjectMapper objectMapper;

    public record BatchRequest(List<Long> materialIds, Long categoryId) { }
    public record Item(Long materialId, boolean success, String message) { }
    public record BatchResult(int successCount, int failCount, List<Item> details) { }
    public record Match(Long ruleId, String keyword, MaterialClassifyRule.MatchField matchField,
                        Long categoryId, String categoryCode, Long templateId) { }
    public record Change(Long materialId, String materialName, Match rule) { }
    public record ApplyResult(boolean dryRun, List<Change> changes, List<Item> failures) { }

    public List<MaterialClassifyRule> listRules() {
        return rules.selectList(new LambdaQueryWrapper<MaterialClassifyRule>()
                .orderByDesc(MaterialClassifyRule::getPriority).orderByAsc(MaterialClassifyRule::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialClassifyRule saveRule(MaterialClassifyRule input, boolean updating, String operator) {
        if (input == null || updating != (input.getId() != null)) throw new BusinessException("新增不传ID，修改必须传ID");
        if (updating && rules.selectById(input.getId()) == null) throw new BusinessException("规则不存在");
        String keyword = input.getKeyword() == null ? "" : input.getKeyword().trim();
        if (keyword.isEmpty() || keyword.length() > 200) throw new BusinessException("关键词须为1至200字");
        String code = input.getCategoryCode() == null ? "" : input.getCategoryCode().trim();
        var config = categoryByCode(code);
        lockConfiguration(config.category().categoryId());
        MaterialClassifyRule rule = new MaterialClassifyRule();
        rule.setId(input.getId()); rule.setKeyword(keyword); rule.setCategoryCode(code);
        rule.setMatchField(input.getMatchField() == null ? MaterialClassifyRule.MatchField.ANY : input.getMatchField());
        rule.setPriority(input.getPriority() == null ? 0 : input.getPriority());
        rule.setEnabled(input.getEnabled() == null || input.getEnabled());
        rule.setUpdateBy(operator); rule.setUpdateTime(LocalDateTime.now());
        try {
            if (updating) {
                if (rules.updateById(rule) != 1) throw new BusinessException("规则修改失败");
            } else {
                rule.setCreateBy(operator); rule.setCreateTime(LocalDateTime.now());
                if (rules.insert(rule) != 1) throw new BusinessException("规则新增失败");
            }
        } catch (DuplicateKeyException e) { throw new BusinessException("关键词与品类的规则已存在"); }
        return rules.selectById(rule.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        if (id == null || rules.deleteById(id) != 1) throw new BusinessException("规则不存在");
    }

    public MaterialMasterCatalog.Configuration categoryByCode(String code) {
        var found = catalog.categories().stream().filter(c -> c.selectable() && c.categoryCode().equals(code)).toList();
        if (found.size() != 1) throw new BusinessException("品类不存在、已停用或不是末级品类：" + code);
        return catalog.configuration(found.get(0).categoryId());
    }

    /** 大优先级先匹配，同级id升序；字面包含且忽略大小写，不将关键词解释成正则。 */
    public Match match(String name, String specification) { return match(name, specification, listRules()); }

    private Match match(String name, String specification, List<MaterialClassifyRule> candidates) {
        for (var rule : candidates) {
            if (!Boolean.TRUE.equals(rule.getEnabled())) continue;
            String keyword = rule.getKeyword().toLowerCase(Locale.ROOT);
            boolean inName = contains(name, keyword), inSpec = contains(specification, keyword);
            boolean hit = switch (rule.getMatchField()) {
                case NAME -> inName; case SPEC -> inSpec; case ANY -> inName || inSpec;
            };
            if (!hit) continue;
            var config = categoryByCode(rule.getCategoryCode());
            return new Match(rule.getId(), rule.getKeyword(), rule.getMatchField(), config.category().categoryId(),
                    rule.getCategoryCode(), config.template().getTemplateId());
        }
        return null;
    }
    private static boolean contains(String value, String keyword) { return value != null && value.toLowerCase(Locale.ROOT).contains(keyword); }

    @Transactional(rollbackFor = Exception.class)
    public BatchResult classify(BatchRequest request, String operator) {
        if (request == null || request.materialIds() == null || request.materialIds().isEmpty() || request.materialIds().size() > 200)
            throw new BusinessException("每次须选择1至200条物料");
        if (request.materialIds().stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(request.materialIds()).size() != request.materialIds().size())
            throw new BusinessException("物料ID须为正整数且不得重复");
        if (request.categoryId() == null) throw new BusinessException("请选择末级品类");
        lockConfiguration(request.categoryId());
        var config = catalog.configuration(request.categoryId());
        List<Item> details = new ArrayList<>();
        for (Long id : request.materialIds().stream().sorted().toList()) {
            InventoryMaterial material = locks.lockMaterial(id);
            if (material == null) { details.add(new Item(id, false, "物料不存在")); continue; }
            try { validateConfirmation(material, config); }
            catch (BusinessException e) { details.add(new Item(id, false, e.getMessage())); continue; }
            writeClassification(id, config.category().categoryId(), config.template().getTemplateId(), MaterialClassSource.MANUAL, true, operator, false);
            details.add(new Item(id, true, "人工归类已确认"));
        }
        int success = (int) details.stream().filter(Item::success).count();
        return new BatchResult(success, details.size() - success, List.copyOf(details));
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, String operator) {
        InventoryMaterial initial = materials.selectById(id);
        if (initial == null || initial.getCategoryId() == null) throw new BusinessException("物料不存在或尚未归类");
        lockConfiguration(initial.getCategoryId());
        InventoryMaterial material = locks.lockMaterial(id);
        if (material == null || !Objects.equals(initial.getCategoryId(), material.getCategoryId())) throw new BusinessException("物料分类已变化，请刷新后确认");
        var config = catalog.configuration(material.getCategoryId());
        validateConfirmation(material, config);
        MaterialClassSource source = material.getClassSource() == null ? MaterialClassSource.MANUAL : MaterialClassSource.valueOf(material.getClassSource());
        writeClassification(id, material.getCategoryId(), config.template().getTemplateId(), source, true, operator, false);
    }

    /** 自动归类只更新未归类行；并发人工归类后条件UPDATE不能覆盖人工结论。 */
    @Transactional(rollbackFor = Exception.class)
    public ApplyResult applyRules(boolean confirm, String operator) {
        List<MaterialClassifyRule> candidates = listRules();
        List<Change> changes = new ArrayList<>(); List<Item> failures = new ArrayList<>();
        for (var material : materials.selectList(new LambdaQueryWrapper<InventoryMaterial>()
                .isNull(InventoryMaterial::getCategoryId).orderByAsc(InventoryMaterial::getMaterialId))) {
            var source = MaterialSourceValues.from(material);
            Match match;
            try { match = match(source.originalName(), source.originalSpecification(), candidates); }
            catch (BusinessException e) { failures.add(new Item(material.getMaterialId(), false, e.getMessage())); continue; }
            if (match == null) continue;
            if (confirm) {
                lockConfiguration(match.categoryId());
                var currentConfig = catalog.configuration(match.categoryId());
                InventoryMaterial current = locks.lockMaterial(material.getMaterialId());
                if (current == null || current.getCategoryId() != null) {
                    failures.add(new Item(material.getMaterialId(), false, "物料已删除或已归类，未覆盖")); continue;
                }
                // 来源可能在预览后变化，实际写入前以锁定后的事实重新匹配。
                var currentSource = MaterialSourceValues.from(current);
                Match currentMatch = match(currentSource.originalName(), currentSource.originalSpecification(), candidates);
                if (currentMatch == null || !currentMatch.equals(match) || !Objects.equals(currentConfig.template().getTemplateId(), match.templateId())) {
                    failures.add(new Item(material.getMaterialId(), false, "来源或模板已变化，请重新预览")); continue;
                }
                writeClassification(material.getMaterialId(), match.categoryId(), match.templateId(), MaterialClassSource.RULE, false, operator, true);
            }
            changes.add(new Change(material.getMaterialId(), material.getMaterialName(), match));
        }
        return new ApplyResult(!confirm, List.copyOf(changes), List.copyOf(failures));
    }

    private void lockConfiguration(Long categoryId) {
        if (locks.lockCategory(categoryId) == null || locks.lockTemplate(categoryId) == null) throw new BusinessException("分类或模板不存在");
    }

    private void validateConfirmation(InventoryMaterial material, MaterialMasterCatalog.Configuration config) {
        Map<String, Object> attributes;
        try { attributes = material.getAttributeJson() == null ? new LinkedHashMap<>() : objectMapper.readValue(material.getAttributeJson(), new TypeReference<LinkedHashMap<String, Object>>() { }); }
        catch (Exception e) { throw new BusinessException("物料属性数据格式错误"); }
        if (attributes == null) throw new BusinessException("物料属性必须为JSON对象");
        Object meta = attributes.remove("material");
        if (meta == null && config.schema().metaRequired()) throw new BusinessException("缺【材质】列或值");
        if (meta != null && !config.schema().metaOptions().contains(meta.toString())) throw new BusinessException("材质越界：" + meta);
        config.schema().validateAttributes(attributes, objectMapper);
    }

    private void writeClassification(Long id, Long categoryId, Long templateId, MaterialClassSource source,
                                     boolean confirmed, String operator, boolean onlyUnclassified) {
        var update = new UpdateWrapper<InventoryMaterial>().eq("material_id", id);
        if (onlyUnclassified) update.isNull("category_id");
        update.set("category_id", categoryId).set("template_id", templateId).set("class_source", source.name())
                .set("class_confirmed", confirmed).set("update_by", operator).set("update_time", LocalDateTime.now());
        if (materials.update(null, update) != 1) throw new BusinessException("归类写入失败，整次操作已回滚");
    }
}
