package com.jjx.system.controller;

import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * 配置模块接口：按分组返回启用配置键值对（仅 is_active=1）
 * 供前端运行态加载（pdf_template / production_config）
 */
@Tag(name = "配置模块")
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigModuleController {

    private static final Set<String> SUPPORTED_GROUPS =
            Set.of("pdf_template", "production_config");

    private final SysConfigService sysConfigService;

    @GetMapping("/module/{group}")
    @Operation(summary = "按模块返回启用配置键值对")
    public Result<Map<String, String>> module(@PathVariable String group) {
        if (!SUPPORTED_GROUPS.contains(group)) {
            throw new BusinessException("不支持的配置分组: " + group);
        }
        return Result.success(sysConfigService.listActiveMapByGroup(group));
    }
}
