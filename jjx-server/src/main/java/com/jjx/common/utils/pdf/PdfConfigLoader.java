package com.jjx.common.utils.pdf;

import com.jjx.system.domain.entity.SysConfig;
import com.jjx.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 模板配置加载器：从 sys_config（group=pdf_template）读取后台配置
 * <p>
 * 2026-08-07：PDF 单据模板支持后台配置（公司抬头/主题色/签名栏等）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfConfigLoader {

    private final SysConfigService sysConfigService;

    /**
     * 加载 PDF 模板配置（每次导出实时读取，后台修改即时生效）
     */
    public PdfTemplateConfig load() {
        PdfTemplateConfig cfg = new PdfTemplateConfig();
        try {
            List<SysConfig> configs = sysConfigService.listByGroup("pdf_template");
            Map<String, String> map = new HashMap<>();
            for (SysConfig c : configs) {
                if (c.getIsActive() != null && c.getIsActive() == 0) continue;
                map.put(c.getConfigKey(), c.getConfigValue());
            }
            cfg.setCompanyName(str(map.get("company_name")));
            cfg.setCompanyAddress(str(map.get("company_address")));
            cfg.setCompanyPhone(str(map.get("company_phone")));
            cfg.setCompanyEmail(str(map.get("company_email")));
            if (map.containsKey("theme_color")) cfg.setThemeColor(str(map.get("theme_color")));
            if (map.containsKey("show_header")) cfg.setShowHeader(!"0".equals(map.get("show_header")));
            if (map.containsKey("show_footer")) cfg.setShowFooter(!"0".equals(map.get("show_footer")));
            if (map.containsKey("signature_label1")) cfg.setSignatureLabel1(str(map.get("signature_label1")));
            if (map.containsKey("signature_label2")) cfg.setSignatureLabel2(str(map.get("signature_label2")));
            if (map.containsKey("signature_label3")) cfg.setSignatureLabel3(str(map.get("signature_label3")));
        } catch (Exception e) {
            log.warn("加载 PDF 模板配置失败，使用默认配置: {}", e.getMessage());
        }
        return cfg;
    }

    private static String str(String v) {
        return v == null ? "" : v;
    }
}
