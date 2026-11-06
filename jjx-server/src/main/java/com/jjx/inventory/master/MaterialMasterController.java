package com.jjx.inventory.master;

import cn.dev33.satoken.annotation.*;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.dto.SysDictItemDTO;
import com.jjx.system.domain.entity.SysDictItem;
import com.jjx.system.service.SysDictService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inventory/material/master")
@RequiredArgsConstructor
public class MaterialMasterController extends BaseController {
    private final MaterialMasterCatalog catalog;
    private final MaterialMasterService service;
    private final MaterialMasterConfigurationService configurationService;
    private final MaterialMasterExcelService excelService;
    private final MaterialMasterAuditService auditService;
    private final SysDictService dictService;

    private static final List<String> MATERIAL_DICTIONARIES = List.of("brand", "color", "form", "unit");

    /** 维护列表包含停用项，避免停用后无法重新启用。 */
    @GetMapping("/dictionaries/{dictCode}/items")
    @SaCheckPermission("inventory:material:dict:view")
    public Result<List<SysDictItem>> dictionaryItems(@PathVariable String dictCode) {
        checkMaterialDictionary(dictCode);
        return Result.success(dictService.selectItemsByDictCode(dictCode));
    }

    public enum DictionaryAction { CREATE, UPDATE, DELETE, STATUS }
    public record DictionaryMutation(@NotNull DictionaryAction action, @Valid SysDictItemDTO item,
                                     Long itemId, Integer isActive) { }

    /** 只代理物料字典写入；唯一性校验、持久化及缓存失效仍由原字典服务负责。 */
    @PostMapping("/dictionaries/{dictCode}/items")
    @SaCheckPermission("inventory:material:dict:edit")
    public Result<Void> mutateDictionaryItem(@PathVariable String dictCode,
            @Validated @RequestBody DictionaryMutation request) {
        checkMaterialDictionary(dictCode);
        var action = request.action();
        var item = request.item();
        if (action == DictionaryAction.CREATE || action == DictionaryAction.UPDATE) {
            if (item == null) throw new BusinessException("字典项不能为空");
            if (!dictCode.equals(item.getDictCode())) throw new BusinessException("字典编码与路径不一致");
            checkDictionaryStatus(item.getIsActive());
        }
        if (action == DictionaryAction.CREATE) {
            if (item.getItemId() != null) throw new BusinessException("新增不能指定字典项ID");
            return toAjax(dictService.insertDictItem(item));
        }
        Long itemId = action == DictionaryAction.UPDATE ? item.getItemId() : request.itemId();
        var existing = dictService.selectItemsByDictCode(dictCode).stream()
                .filter(row -> row.getItemId().equals(itemId)).findFirst()
                .orElseThrow(() -> new BusinessException("字典项不存在或不属于当前物料字典"));
        return switch (action) {
            case UPDATE -> {
                if (!existing.getItemKey().equals(item.getItemKey()))
                    throw new BusinessException("字典键不可修改");
                yield toAjax(dictService.updateDictItem(item));
            }
            case DELETE -> toAjax(dictService.deleteDictItemByIds(new Long[]{itemId}));
            case STATUS -> {
                checkDictionaryStatus(request.isActive());
                yield toAjax(dictService.changeDictItemStatus(itemId, request.isActive()));
            }
            default -> throw new BusinessException("不支持的字典操作");
        };
    }

    private static void checkMaterialDictionary(String dictCode) {
        if (!MATERIAL_DICTIONARIES.contains(dictCode))
            throw new BusinessException("物料字典仅允许 brand/color/form/unit");
    }

    private static void checkDictionaryStatus(Integer status) {
        if (!StatusEnum.NORMAL.getCode().equals(status) && !StatusEnum.DISABLE.getCode().equals(status))
            throw new BusinessException("字典项状态只能为启用或禁用");
    }

    @GetMapping("/categories")
    @SaCheckLogin
    public Result<List<MaterialMasterCatalog.Category>> categories(@RequestParam(defaultValue = "false") boolean includeDisabled) {
        if (includeDisabled) cn.dev33.satoken.stp.StpUtil.checkPermission("inventory:category:list");
        return Result.success(catalog.categories(includeDisabled));
    }

    @GetMapping("/dictionaries")
    @SaCheckLogin
    public Result<Map<String, List<MaterialMasterCatalog.DictionaryOption>>> dictionaries() { return Result.success(catalog.dictionaries()); }

    @GetMapping("/categories/{categoryId}/required-fields")
    @SaCheckPermission("inventory:category:list")
    public Result<Map<String, Boolean>> requiredFields(@PathVariable Long categoryId) {
        return Result.success(service.requiredFields(categoryId));
    }

    @PutMapping("/categories/{categoryId}/required-fields")
    @SaCheckPermission("inventory:category:edit")
    public Result<Void> saveRequiredFields(@PathVariable Long categoryId, @RequestBody Map<String, Object> fields) {
        service.saveRequiredFields(categoryId, fields);
        return Result.success();
    }

    @GetMapping("/configuration/{categoryId}")
    @SaCheckLogin
    public Result<MaterialMasterCatalog.Configuration> configuration(@PathVariable Long categoryId) { return Result.success(catalog.configuration(categoryId)); }

    @GetMapping("/{id:\\d+}")
    @SaCheckPermission(value = {"inventory:material:view", "engineering:bom:add", "engineering:bom:edit"}, mode = SaMode.OR)
    public Result<MaterialMasterService.Detail> detail(@PathVariable Long id) { return Result.success(service.detail(id)); }

    @PostMapping
    @SaCheckPermission(value = {"inventory:material:add", "engineering:bom:add", "engineering:bom:edit"}, mode = SaMode.OR)
    public Result<InventoryMaterial> create(@RequestBody MaterialMasterDTO dto) {
        if (dto.getMaterialId() != null) throw new com.jjx.common.exception.BusinessException("新增不能指定物料ID");
        InventoryMaterial saved = service.save(dto, getUsername());
        return Result.success(saved, service.specificationPreview(saved).message());
    }

    @PutMapping
    @SaCheckPermission("inventory:material:edit")
    public Result<InventoryMaterial> update(@RequestBody MaterialMasterDTO dto) {
        if (dto.getMaterialId() == null) throw new com.jjx.common.exception.BusinessException("修改必须指定物料ID");
        InventoryMaterial saved = service.save(dto, getUsername());
        return Result.success(saved, service.specificationPreview(saved).message());
    }

    @PutMapping("/{id:\\d+}/naming")
    @SaCheckPermission("inventory:material:edit")
    @com.jjx.system.annotation.Log(module = "物料管理", businessType = com.jjx.system.annotation.BusinessType.UPDATE,
            bizType = "'material'", bizId = "#id", action = "重算名称与规格", detail = "#result.data.detail")
    public Result<MaterialMasterService.NamingResult> updateNaming(@PathVariable Long id,
            @RequestBody MaterialMasterService.NamingPatch patch) {
        var saved = service.updateNaming(id, patch, getUsername());
        return Result.success(saved, saved.specificationGeneration().message());
    }

    @PutMapping("/category")
    @SaCheckPermission(value = {"inventory:category:add", "inventory:category:edit"}, mode = SaMode.OR)
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<com.jjx.inventory.domain.InventoryMaterialCategory> saveCategory(@RequestBody MaterialCategorySaveDTO dto) {
        // 创建和修改分别核验，避免有新增权限即能修改已有节点。
        cn.dev33.satoken.stp.StpUtil.checkPermission(dto.categoryId() == null ? "inventory:category:add" : "inventory:category:edit");
        if (dto.requiredFields() != null) {
            cn.dev33.satoken.stp.StpUtil.checkPermission("inventory:category:edit");
            service.validateRequiredFields(dto.requiredFields());
        }
        var category = configurationService.saveCategory(dto.category(), getUsername());
        if (dto.requiredFields() != null) service.saveRequiredFields(category.getCategoryId(), dto.requiredFields());
        if (dto.remark() != null) {
            service.saveCategoryRemark(category.getCategoryId(), dto.remark());
            category.setRemark(dto.remark());
        }
        return Result.success(category);
    }

    @PutMapping("/template")
    @SaCheckPermission("inventory:category:edit")
    public Result<InventoryAttributeTemplate> saveTemplate(@RequestBody MaterialMasterConfigurationService.TemplateSave dto) {
        return Result.success(configurationService.saveTemplate(dto, getUsername()));
    }

    @PutMapping("/template/pattern")
    @SaCheckPermission("inventory:category:edit")
    public Result<MaterialMasterConfigurationService.TemplateDetail> savePattern(@RequestBody MaterialMasterConfigurationService.PatternSave dto) {
        return Result.success(configurationService.savePattern(dto, getUsername()));
    }

    @PutMapping("/template/fields")
    @SaCheckPermission("inventory:category:edit")
    public Result<MaterialMasterConfigurationService.TemplateDetail> saveTemplateFields(@RequestBody MaterialMasterConfigurationService.TemplateSave dto) {
        return Result.success(configurationService.saveTemplateFields(dto, getUsername()));
    }

    @GetMapping("/template/{categoryId}")
    @SaCheckPermission("inventory:category:list")
    public Result<MaterialMasterConfigurationService.TemplateDetail> template(@PathVariable Long categoryId) {
        return Result.success(configurationService.templateDetail(categoryId));
    }

    /** 规格模板预览：按品类取最近 5 条物料，给“当前规格 → 按模板生成”对照（只读）。 */
    @PostMapping("/spec/preview")
    @SaCheckPermission(value = {"inventory:category:list", "inventory:material:view"}, mode = SaMode.OR)
    public Result<MaterialMasterService.SpecPreview> previewSpecification(@RequestBody SpecRenderRequest request) {
        return Result.success(service.previewSpecification(request.categoryId()));
    }

    /** 命名/规格可选字段：公共字段 + 该品类属性字段。 */
    @GetMapping("/naming/fields")
    @SaCheckPermission(value = {"inventory:category:list", "inventory:material:view"}, mode = SaMode.OR)
    public Result<List<MaterialMasterService.FieldOption>> fieldOptions(@RequestParam(required = false) Long categoryId) {
        return Result.success(service.fieldOptions(categoryId));
    }

    public record SpecRenderRequest(Long categoryId, MaterialMasterDTO material) { }

    /** 规格模板渲染：用当前表单值按已保存模板生成规格文本（不写库）。 */
    @PostMapping("/spec/render")
    @SaCheckPermission(value = {"inventory:category:list", "inventory:material:view"}, mode = SaMode.OR)
    public Result<String> renderSpecification(@RequestBody SpecRenderRequest request) {
        return Result.success(service.renderSpecification(request.categoryId(), request.material()));
    }

    public record NamingRenderRequest(Long categoryId, MaterialMasterDTO material) { }

    /** 命名规则渲染：用当前表单值按已保存规则生成名称（不写库，dev-20261029-025）。 */
    @PostMapping("/naming/render")
    @SaCheckPermission(value = {"inventory:category:list", "inventory:material:view"}, mode = SaMode.OR)
    public Result<String> renderNaming(@RequestBody NamingRenderRequest request) {
        return Result.success(service.renderNaming(request.categoryId(), request.material()));
    }

    /** 型号规则渲染：用当前表单值按已保存规则预览内部型号（只读）。 */
    @PostMapping("/model/render")
    @SaCheckPermission(value = {"inventory:category:list", "inventory:material:view"}, mode = SaMode.OR)
    public Result<String> renderModel(@RequestBody NamingRenderRequest request) {
        return Result.success(service.renderModel(request.categoryId(), request.material(), null));
    }

    public record NamingPreviewRequest(Long categoryId, MaterialNamingPattern namePattern) { }

    /** 命名规则预览：只读试算，按品类取最近 5 条物料给新旧名对照。 */
    @PostMapping("/naming/preview")
    @SaCheckPermission(value = {"inventory:category:list", "inventory:material:view"}, mode = SaMode.OR)
    public Result<MaterialMasterService.NamingPreview> previewNaming(@RequestBody NamingPreviewRequest request) {
        return Result.success(service.previewNaming(request.categoryId(), request.namePattern()));
    }

    public record RenameRequest(Long categoryId) { }

    /** 批量改名预演：按品类已保存的命名规则算差异，只读。 */
    @GetMapping("/naming/changes")
    @SaCheckPermission(value = {"inventory:category:list", "inventory:material:view"}, mode = SaMode.OR)
    public Result<MaterialMasterService.RenameReport> renameChanges(@RequestParam Long categoryId,
            @RequestParam(required = false) Integer limit) {
        return Result.success(service.previewRename(categoryId, limit));
    }

    /** 批量改名执行：只改差异行，逐条留痕。 */
    @PostMapping("/naming/apply")
    @SaCheckPermission("inventory:material:edit")
    public Result<Integer> applyRename(@RequestBody RenameRequest request) {
        return Result.success(service.applyRename(request.categoryId(), getUsername()));
    }

    @DeleteMapping("/template/{categoryId}")
    @SaCheckPermission("inventory:category:edit")
    public Result<Void> removeTemplate(@PathVariable Long categoryId) {
        configurationService.removeUnusedTemplate(categoryId);
        return Result.success();
    }

    @GetMapping("/import-template")
    @SaCheckPermission(value = {"inventory:material:add", "inventory:material:import"}, mode = SaMode.OR)
    public void importTemplate(@RequestParam(required = false) Long categoryId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        download(response, excelService.template(categoryId), "material-template.xlsx");
    }

    @PostMapping("/import")
    @SaCheckPermission(value = {"inventory:material:add", "inventory:material:import"}, mode = SaMode.OR)
    public Result<MaterialMasterExcelService.ImportResult> importExcel(@RequestParam org.springframework.web.multipart.MultipartFile file,
            @RequestParam(required = false) Long categoryId, @RequestParam(defaultValue = "true") boolean preview,
            @RequestParam(defaultValue = "false") boolean autoClassify) {
        return Result.success(excelService.importExcel(file, categoryId, preview, getUsername(), cn.dev33.satoken.stp.StpUtil.hasPermission("inventory:material:edit"), autoClassify));
    }

    @GetMapping("/export")
    @SaCheckPermission("inventory:material:view")
    public void export(com.jjx.inventory.dto.query.MaterialQueryDTO query, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        download(response, excelService.exportQuery(query), "material-master.xlsx");
    }

    private static void download(jakarta.servlet.http.HttpServletResponse response, byte[] content, String name) throws java.io.IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + name);
        response.getOutputStream().write(content);
    }

    @GetMapping("/audit")
    @SaCheckPermission("inventory:material:view")
    public Result<MaterialMasterAuditService.Audit> audit() { return Result.success(auditService.inspect()); }

    @GetMapping("/migration-candidates")
    @SaCheckPermission("inventory:material:view")
    public void migrationCandidates(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        download(response, auditService.migrationCandidates(), "material-migration-candidates.xlsx");
    }
}
