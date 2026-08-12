package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.common.utils.ExcelUtils;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.production.domain.dto.ToolingDTO;
import com.jjx.production.domain.dto.ToolingQueryDTO;
import com.jjx.production.domain.vo.ToolingVO;
import com.jjx.production.service.ToolingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 工装模具档案 Controller
 */
@Tag(name = "工装模具档案")
@RestController
@RequestMapping("/production/tooling")
@RequiredArgsConstructor
public class ToolingController extends BaseController {

    private final ToolingService toolingService;

    @Operation(summary = "分页查询")
    @SaCheckPermission("production:tooling:query")
    @GetMapping("/page")
    public Result<PageResult<ToolingVO>> page(ToolingQueryDTO query) {
        return Result.success(toolingService.page(query));
    }

    @Operation(summary = "查询列表（导出用）")
    @SaCheckPermission("production:tooling:export")
    @GetMapping("/list")
    public Result<List<ToolingVO>> list(ToolingQueryDTO query) {
        return Result.success(toolingService.list(query));
    }

    @Operation(summary = "查询详情")
    @SaCheckPermission("production:tooling:query")
    @GetMapping("/{id}")
    public Result<ToolingVO> getById(@PathVariable Long id) {
        return Result.success(toolingService.getById(id));
    }

    @Operation(summary = "下拉选项（未报废，供工艺卡片/工序引用）")
    @GetMapping("/options")
    public Result<List<ToolingVO>> options(@RequestParam(required = false) String type) {
        return Result.success(toolingService.options(type));
    }

    @Operation(summary = "按规则生成编号")
    @SaCheckPermission("production:tooling:add")
    @GetMapping("/gen-no")
    public Result<String> genNo(@RequestParam String type) {
        return Result.success(toolingService.genNo(type));
    }

    @Operation(summary = "新增")
    @SaCheckPermission("production:tooling:add")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ToolingDTO dto) {
        return Result.success(toolingService.create(dto));
    }

    @Operation(summary = "修改")
    @SaCheckPermission("production:tooling:edit")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody ToolingDTO dto) {
        toolingService.update(dto);
        return Result.success();
    }

    @Operation(summary = "状态变更")
    @SaCheckPermission("production:tooling:changeStatus")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody java.util.Map<String, Integer> body) {
        toolingService.changeStatus(id, body.get("status"));
        return Result.success();
    }

    @Operation(summary = "删除")
    @SaCheckPermission("production:tooling:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        toolingService.delete(id);
        return Result.success();
    }

    @Operation(summary = "Excel导入")
    @SaCheckPermission("production:tooling:import")
    @PostMapping("/import")
    public Result<String> importExcel(@RequestParam("file") MultipartFile file) throws Exception {
        List<com.jjx.production.domain.dto.ToolingImportDTO> list =
                ExcelUtils.importExcel(file, com.jjx.production.domain.dto.ToolingImportDTO.class);
        return Result.success(toolingService.importExcel(list, getUsername()));
    }

    @Operation(summary = "下载导入模板")
    @SaCheckPermission("production:tooling:import")
    @GetMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtils.downloadTemplate(response, com.jjx.production.domain.dto.ToolingImportDTO.class, "工装模具导入模板");
    }

    @Operation(summary = "Excel导出")
    @SaCheckPermission("production:tooling:export")
    @GetMapping("/export")
    public void export(ToolingQueryDTO query, HttpServletResponse response) {
        List<ToolingVO> rows = toolingService.list(query);
        if (rows == null || rows.isEmpty()) {
            throw new com.jjx.common.exception.BusinessException("暂无数据可导出");
        }
        List<com.jjx.production.domain.dto.ToolingImportDTO> exportRows = new java.util.ArrayList<>();
        for (ToolingVO vo : rows) {
            com.jjx.production.domain.dto.ToolingImportDTO d = new com.jjx.production.domain.dto.ToolingImportDTO();
            d.setToolingNo(vo.getToolingNo());
            d.setToolingName(vo.getToolingName());
            d.setToolingType(vo.getTypeLabel());
            d.setSpec(vo.getSpec());
            d.setLifeLimit(vo.getLifeLimit());
            d.setLocation(vo.getLocation());
            d.setCustomer(vo.getCustomer());
            d.setResponsible(vo.getResponsible());
            d.setEnableDate(vo.getEnableDate() == null ? null : vo.getEnableDate().toString());
            d.setRemark(vo.getRemark());
            exportRows.add(d);
        }
        ExcelUtils.export(response, exportRows, com.jjx.production.domain.dto.ToolingImportDTO.class, "工装模具档案");
    }
}
