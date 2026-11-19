package com.jjx.inventory.master;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/** 后端统一生成候选；只更新用户实际预览并勾选的行。 */
@Service
@RequiredArgsConstructor
public class MaterialRuleApplicationService {
    private final MaterialMasterService master;
    private final MaterialMasterCatalog catalog;
    private final MaterialMasterMapper locks;
    private final InventoryMaterialMapper materials;
    private final SysOperLogMapper logs;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper json;

    public record Options(Long categoryId, boolean applyName, boolean applySpecification, boolean applyModel) { }
    public record Selection(Long materialId, String previewToken) { }
    public record ApplyRequest(Options options, List<Selection> selections) { }
    public record Change(Long materialId, String materialCode, String oldName, String newName,
                         String oldModel, String newModel, String oldSpecification, String newSpecification,
                         boolean selectable, String message, String previewToken) { }
    public record Preview(long total, int pageNum, int pageSize, List<Change> records) { }
    public record Outcome(Long materialId, String materialCode, boolean success, boolean skipped, String message) { }
    public record Applied(int success, int skipped, int failed, List<Outcome> records) { }
    private record Evaluation(InventoryMaterial candidate, Change change) { }

    public MaterialMasterService.SpecPreview previewSpecification(Long categoryId, MaterialAttributeSchema.SpecPattern pattern) {
        var config = configuration(new Options(categoryId, false, true, false));
        var old = config.schema();
        var schema = new MaterialAttributeSchema(old.version(), old.metaOptions(), old.metaRequired(), old.defaultMeta(),
                old.defaultForm(), old.defaultUnit(), old.fields(), old.namePattern(), pattern, old.modelPattern(), old.measurementDefaults());
        schema.validateDefinition();
        var rows = materials.selectList(new LambdaQueryWrapper<InventoryMaterial>()
                .eq(InventoryMaterial::getCategoryId, categoryId).orderByDesc(InventoryMaterial::getMaterialId).last("LIMIT 5"));
        var samples = rows.stream().map(row -> new MaterialMasterService.SpecSample(row.getMaterialId(), row.getMaterialCode(),
                row.getSpecification(), schema.specification(row, master.readAttributes(row.getAttributeJson()), config.category().categoryName()))).toList();
        return new MaterialMasterService.SpecPreview(samples, "按当前规格草稿试算，尚未应用到现有物料");
    }

    private MaterialMasterCatalog.Configuration configuration(Options options) {
        if (options == null || options.categoryId() == null) throw new BusinessException("请选择物料品类");
        if (!options.applyName() && !options.applySpecification() && !options.applyModel())
            throw new BusinessException("请至少选择一项应用内容");
        var config = catalog.optionalConfiguration(options.categoryId());
        if (config == null) throw new BusinessException("该品类没有启用的规则模板");
        return config;
    }

    /** 按主键分段读取，后端筛选实际差异；不会将整表交给前端分页。 */
    public Preview preview(Options options, int pageNum, int pageSize, String keyword) {
        var config = configuration(options);
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) throw new BusinessException("分页参数无效，每页最多100条");
        String term = keyword == null ? "" : keyword.trim();
        if (term.length() > 100) throw new BusinessException("搜索内容最多100字");
        long total = 0, after = 0, offset = ((long) pageNum - 1) * pageSize;
        var records = new ArrayList<Change>();
        while (true) {
            var query = new LambdaQueryWrapper<InventoryMaterial>()
                    .eq(InventoryMaterial::getCategoryId, options.categoryId())
                    .gt(InventoryMaterial::getMaterialId, after)
                    .orderByAsc(InventoryMaterial::getMaterialId).last("LIMIT 200");
            if (!term.isEmpty()) query.and(q -> q.like(InventoryMaterial::getMaterialCode, term)
                    .or().like(InventoryMaterial::getMaterialName, term));
            var chunk = materials.selectList(query);
            if (chunk.isEmpty()) break;
            for (var material : chunk) {
                Change change = evaluate(material, options, config).change();
                if (change == null) continue;
                if (total >= offset && records.size() < pageSize) records.add(change);
                total++;
            }
            after = chunk.get(chunk.size() - 1).getMaterialId();
            if (chunk.size() < 200) break;
        }
        return new Preview(total, pageNum, pageSize, records);
    }

    private Evaluation evaluate(InventoryMaterial material, Options options, MaterialMasterCatalog.Configuration config) {
        var candidate = new InventoryMaterial();
        BeanUtils.copyProperties(material, candidate);
        try {
            var schema = config.schema();
            if (options.applyModel()) {
                String model = master.renderModel(options.categoryId(), draft(candidate), null);
                candidate.setModel(required(model, "型号", 100));
            }
            if (options.applyName() && schema.namePattern() != null && !schema.namePattern().fields().isEmpty())
                candidate.setMaterialName(required(master.renderNaming(options.categoryId(), draft(candidate)), "名称", 200));
            if (options.applySpecification() && schema.hasSpecPattern())
                candidate.setSpecification(required(master.specificationPreview(candidate).specification(), "规格", 500));
            if (same(material, candidate)) return new Evaluation(candidate, null);
            master.validateRuleCandidate(candidate);
            return new Evaluation(candidate, change(material, candidate, true, "将按已保存规则更新", token(material, candidate, config, options)));
        } catch (BusinessException e) {
            return new Evaluation(candidate, change(material, candidate, false, e.getMessage(), null));
        }
    }

    private MaterialMasterDTO draft(InventoryMaterial material) {
        var draft = new MaterialMasterDTO();
        BeanUtils.copyProperties(material, draft, "attributeJson");
        draft.setAttributeJson(master.readAttributes(material.getAttributeJson()));
        return draft;
    }

    private static String required(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new BusinessException(label + "未生成有效内容，请补全资料或调整规则");
        if (value.length() > max) throw new BusinessException(label + "生成结果超过" + max + "字");
        return value;
    }

    private static boolean same(InventoryMaterial a, InventoryMaterial b) {
        return Objects.equals(a.getMaterialName(), b.getMaterialName()) && Objects.equals(a.getModel(), b.getModel())
                && Objects.equals(a.getSpecification(), b.getSpecification());
    }

    private Change change(InventoryMaterial old, InventoryMaterial next, boolean selectable, String message, String token) {
        return new Change(old.getMaterialId(), old.getMaterialCode(), old.getMaterialName(), next.getMaterialName(),
                old.getModel(), next.getModel(), old.getSpecification(), next.getSpecification(), selectable, message, token);
    }

    private String token(InventoryMaterial old, InventoryMaterial next, MaterialMasterCatalog.Configuration config, Options options) {
        try {
            byte[] data = json.writeValueAsBytes(List.of(json.valueToTree(old), json.valueToTree(next),
                    json.valueToTree(config), json.valueToTree(options)));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) { throw new IllegalStateException("规则预览指纹生成失败", e); }
    }

    /** 每条独立事务，规则/资料变化或单条失败不会把其它已选记录错误写入。 */
    public Applied apply(ApplyRequest request, String operator, Long userId, String realName) {
        if (request == null) throw new BusinessException("应用请求不能为空");
        configuration(request.options());
        var selected = request.selections();
        if (selected == null || selected.isEmpty() || selected.size() > 500)
            throw new BusinessException("请选择1至500条已预览的物料");
        var ids = new HashSet<Long>();
        for (var row : selected) {
            if (row == null || row.materialId() == null || row.previewToken() == null || !row.previewToken().matches("[a-f0-9]{64}")
                    || !ids.add(row.materialId())) throw new BusinessException("勾选数据无效或重复，请重新预览");
        }
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var outcomes = new ArrayList<Outcome>();
        for (var row : selected) {
            try {
                outcomes.add(Objects.requireNonNull(transaction.execute(status -> applyOne(request.options(), row, operator, userId, realName))));
            } catch (BusinessException e) {
                outcomes.add(new Outcome(row.materialId(), null, false, false, e.getMessage()));
            } catch (org.springframework.dao.DataAccessException e) {
                org.slf4j.LoggerFactory.getLogger(getClass()).error("物料规则应用失败 materialId={}", row.materialId(), e);
                outcomes.add(new Outcome(row.materialId(), null, false, false, "数据库写入失败，该条已回滚，请重新预览"));
            }
        }
        int success = (int) outcomes.stream().filter(Outcome::success).count();
        int skipped = (int) outcomes.stream().filter(Outcome::skipped).count();
        return new Applied(success, skipped, outcomes.size() - success - skipped, outcomes);
    }

    private Outcome applyOne(Options options, Selection row, String operator, Long userId, String realName) {
        if (locks.lockCategory(options.categoryId()) == null) throw new BusinessException("品类不存在");
        if (locks.lockTemplate(options.categoryId()) == null) throw new BusinessException("规则模板不存在");
        var config = configuration(options);
        var material = locks.lockMaterial(row.materialId());
        if (material == null || !Objects.equals(material.getCategoryId(), options.categoryId()))
            throw new BusinessException("物料不存在或已不属于当前品类，请重新预览");
        var evaluated = evaluate(material, options, config);
        var change = evaluated.change();
        if (change == null) return new Outcome(row.materialId(), material.getMaterialCode(), false, true, "已与新规则一致，无需更新");
        if (!change.selectable()) throw new BusinessException(change.message());
        if (!Objects.equals(row.previewToken(), change.previewToken()))
            throw new BusinessException("规则或物料资料已变化，请重新预览后勾选");
        var result = master.saveRuleCandidate(material, evaluated.candidate(), "应用已保存的品类规则", operator,
                new MaterialMasterService.SpecificationPreview(evaluated.candidate().getSpecification(), options.applySpecification(), "应用已保存规则"));
        var log = new SysOperLog();
        log.setModule("物料档案"); log.setBusinessType(BusinessType.UPDATE.getCode());
        log.setBizType("material"); log.setBizId(row.materialId().toString());
        log.setAction("应用品类规则"); log.setOperUrl("/inventory/material/master/rules/apply");
        log.setUsername(operator); log.setUserId(userId); log.setRealName(realName);
        log.setStatus(StatusEnum.NORMAL.getCode()); log.setCreateTime(LocalDateTime.now());
        log.setDetail(result.detail());
        if (logs.insert(log) != 1) throw new BusinessException("修改日志保存失败，该条已回滚");
        return new Outcome(row.materialId(), material.getMaterialCode(), true, false, "已应用");
    }
}
