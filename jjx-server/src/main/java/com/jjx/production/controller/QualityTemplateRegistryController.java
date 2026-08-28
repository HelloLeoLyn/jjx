package com.jjx.production.controller;

import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.QualityTemplateQueryDTO;
import com.jjx.production.domain.entity.QualityTemplateRegistry;
import com.jjx.production.service.QualityTemplateRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "质量记录模板注册表")
@RestController
@RequestMapping("/production/quality-template")
@RequiredArgsConstructor
public class QualityTemplateRegistryController {
    private final QualityTemplateRegistryService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public Result<PageResult<QualityTemplateRegistry>> page(QualityTemplateQueryDTO query) {
        return Result.success(service.page(query));
    }

    @Operation(summary = "查询详情")
    @GetMapping("/{id}")
    public Result<QualityTemplateRegistry> detail(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @Operation(summary = "新增")
    @PostMapping
    public Result<Long> create(@RequestBody QualityTemplateRegistry template) {
        return Result.success(service.create(template));
    }

    @Operation(summary = "更新（含换版文件回填）")
    @PutMapping
    public Result<Void> update(@RequestBody QualityTemplateRegistry template) {
        service.update(template);
        return Result.success();
    }

    @Operation(summary = "生效/停用")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        service.changeStatus(id, body.get("status"));
        return Result.success();
    }

    @Operation(summary = "删除草稿")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
