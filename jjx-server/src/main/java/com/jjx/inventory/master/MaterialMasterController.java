package com.jjx.inventory.master;

import cn.dev33.satoken.annotation.*;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.inventory.domain.InventoryMaterial;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/categories")
    @SaCheckLogin
    public Result<List<MaterialMasterCatalog.Category>> categories(@RequestParam(defaultValue = "false") boolean includeDisabled) {
        if (includeDisabled) cn.dev33.satoken.stp.StpUtil.checkPermission("inventory:category:list");
        return Result.success(catalog.categories(includeDisabled));
    }

    @GetMapping("/dictionaries")
    @SaCheckLogin
    public Result<Map<String, List<MaterialMasterCatalog.DictionaryOption>>> dictionaries() { return Result.success(catalog.dictionaries()); }

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
    public Result<com.jjx.inventory.domain.InventoryMaterialCategory> saveCategory(@RequestBody MaterialMasterConfigurationService.CategorySave dto) {
        // 创建和修改分别核验，避免有新增权限即能修改已有节点。
        cn.dev33.satoken.stp.StpUtil.checkPermission(dto.categoryId() == null ? "inventory:category:add" : "inventory:category:edit");
        return Result.success(configurationService.saveCategory(dto, getUsername()));
    }

    @PutMapping("/template")
    @SaCheckPermission("inventory:category:edit")
    public Result<InventoryAttributeTemplate> saveTemplate(@RequestBody MaterialMasterConfigurationService.TemplateSave dto) {
        return Result.success(configurationService.saveTemplate(dto, getUsername()));
    }

    @GetMapping("/template/{categoryId}")
    @SaCheckPermission("inventory:category:list")
    public Result<MaterialMasterConfigurationService.TemplateDetail> template(@PathVariable Long categoryId) {
        return Result.success(configurationService.templateDetail(categoryId));
    }

    public record NamingPreviewRequest(Long categoryId, MaterialNamingPattern namePattern) { }

    /** 命名规则预览：只读试算，按品类取最近 5 条物料给新旧名对照。 */
    @PostMapping("/naming/preview")
    @SaCheckPermission(value = {"inventory:category:list", "inventory:material:view"}, mode = SaMode.OR)
    public Result<MaterialMasterService.NamingPreview> previewNaming(@RequestBody NamingPreviewRequest request) {
        return Result.success(service.previewNaming(request.categoryId(), request.namePattern()));
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
