package com.jjx.product.controller;

import com.jjx.common.core.result.Result;
import com.jjx.product.domain.entity.ConfigModel;
import com.jjx.product.domain.entity.ConfigOption;
import com.jjx.product.service.IConfigModelService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "产品配置模型")
@RestController
@RequestMapping("/engineering/config")
@RequiredArgsConstructor
public class ConfigModelController {
    private final IConfigModelService configService;

    @Operation(summary = "配置模型列表")
    @GetMapping
    public Result<?> list() { return Result.success(configService.listPage(null)); }

    @Operation(summary = "配置模型分页")
    @GetMapping("/page")
    public Result<?> page() { return Result.success(configService.listPage(null)); }

    @Operation(summary = "配置模型详情（含选项）")
    @GetMapping("/{modelId}")
    public Result<Map<String, Object>> detail(@PathVariable Long modelId) {
        return Result.success(configService.getModelDetail(modelId));
    }

    @Operation(summary = "创建配置模型")
    @PostMapping
    @SaCheckPermission("engineering:edit")
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        ConfigModel model = toModel(body);
        List<ConfigOption> options = toOptions(body);
        return Result.success(configService.createModel(model, options));
    }

    @Operation(summary = "更新配置模型")
    @PutMapping
    @SaCheckPermission("engineering:edit")
    public Result<Void> update(@RequestBody Map<String, Object> body) {
        ConfigModel model = toModel(body);
        List<ConfigOption> options = toOptions(body);
        configService.updateModel(model, options);
        return Result.success();
    }

    @Operation(summary = "删除配置模型")
    @DeleteMapping("/{modelId}")
    @SaCheckPermission("engineering:delete")
    public Result<Void> delete(@PathVariable Long modelId) {
        configService.deleteModel(modelId);
        return Result.success();
    }

    @Operation(summary = "设置默认模型")
    @PutMapping("/{modelId}/default")
    @SaCheckPermission("engineering:edit")
    public Result<Void> setDefault(@PathVariable Long modelId) {
        configService.setDefault(modelId);
        return Result.success();
    }

    @Operation(summary = "启用/停用")
    @PutMapping("/{modelId}/status/{status}")
    @SaCheckPermission("engineering:edit")
    public Result<Void> changeStatus(@PathVariable Long modelId, @PathVariable Integer status) {
        configService.changeStatus(modelId, status);
        return Result.success();
    }

    private ConfigModel toModel(Map<String, Object> body) {
        ConfigModel m = new ConfigModel();
        m.setModelId(body.get("modelId") != null ? Long.valueOf(body.get("modelId").toString()) : null);
        m.setModelCode(body.get("modelCode") != null ? body.get("modelCode").toString() : null);
        m.setModelName(body.get("modelName") != null ? body.get("modelName").toString() : null);
        m.setProductId(body.get("productId") != null ? Long.valueOf(body.get("productId").toString()) : null);
        m.setIsDefault(body.get("isDefault") != null ? Integer.valueOf(body.get("isDefault").toString()) : 0);
        m.setStatus(body.get("status") != null ? Integer.valueOf(body.get("status").toString()) : 1);
        m.setRemark(body.get("remark") != null ? body.get("remark").toString() : null);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<ConfigOption> toOptions(Map<String, Object> body) {
        Object raw = body.get("options");
        if (raw == null) return null;
        List<Map<String, Object>> list = (List<Map<String, Object>>) raw;
        return list.stream().map(o -> {
            ConfigOption opt = new ConfigOption();
            opt.setOptionId(o.get("optionId") != null ? Long.valueOf(o.get("optionId").toString()) : null);
            opt.setOptionCode(o.get("optionCode") != null ? o.get("optionCode").toString() : null);
            opt.setOptionName(o.get("optionName") != null ? o.get("optionName").toString() : null);
            opt.setOptionType(o.get("optionType") != null ? o.get("optionType").toString() : null);
            opt.setValueJson(o.get("valueJson") != null ? o.get("valueJson").toString() : null);
            opt.setDependsOn(o.get("dependsOn") != null ? o.get("dependsOn").toString() : null);
            opt.setExcludes(o.get("excludes") != null ? o.get("excludes").toString() : null);
            opt.setIsRequired(o.get("isRequired") != null ? Integer.valueOf(o.get("isRequired").toString()) : 0);
            opt.setSortOrder(o.get("sortOrder") != null ? Integer.valueOf(o.get("sortOrder").toString()) : 0);
            return opt;
        }).toList();
    }
}
