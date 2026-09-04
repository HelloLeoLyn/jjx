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
 * 供前端运行态加载（pdf_template / production_config），登录即可访问（打印页公司抬头依赖）
 *
 * 2026-09-04 治理：打印场景开放读取，但敏感开票资料（税号/开户行/银行账号/联行号/法人）不下发；
 * 后续按业务场景白名单放行（见 dev-20260904-014），当前一律过滤。
 */
@Tag(name = "配置模块")
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigModuleController {

    private static final Set<String> SUPPORTED_GROUPS =
            Set.of("pdf_template", "production_config");

    /** 敏感开票资料键：打印开放通道不下发，后续按白名单放行 */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "company_tax_no", "company_bank", "company_account", "company_bank_code", "company_legal");

    private final SysConfigService sysConfigService;

    @GetMapping("/module/{group}")
    @Operation(summary = "按模块返回启用配置键值对（登录即可，敏感键过滤）")
    public Result<Map<String, String>> module(@PathVariable String group) {
        if (!SUPPORTED_GROUPS.contains(group)) {
            throw new BusinessException("不支持的配置分组: " + group);
        }
        Map<String, String> all = sysConfigService.listActiveMapByGroup(group);
        all.keySet().removeIf(SENSITIVE_KEYS::contains);
        return Result.success(all);
    }
}
