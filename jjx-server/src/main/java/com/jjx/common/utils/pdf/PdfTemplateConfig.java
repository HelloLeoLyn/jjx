package com.jjx.common.utils.pdf;

import lombok.Data;

/**
 * PDF 单据模板配置（后台可配置，sys_config group=pdf_template）
 * <p>
 * 用于单据 PDF 的公司抬头、主题色、签名栏等渲染（2026-08-07 新增）。
 */
@Data
public class PdfTemplateConfig {

    /** 公司名称（PDF 抬头） */
    private String companyName = "";

    /** 公司地址 */
    private String companyAddress = "";

    /** 联系电话 */
    private String companyPhone = "";

    /** 邮箱 */
    private String companyEmail = "";

    /** 主题色（十六进制，默认商务蓝） */
    private String themeColor = "#2B5AA7";

    /** 是否显示公司抬头 */
    private boolean showHeader = true;

    /** 是否显示页脚 */
    private boolean showFooter = true;

    /** 签名栏1标题 */
    private String signatureLabel1 = "销售负责人";

    /** 签名栏2标题 */
    private String signatureLabel2 = "客户确认";

    /** 签名栏3标题 */
    private String signatureLabel3 = "日期";

    /** 是否已配置公司名称（决定是否渲染抬头区） */
    public boolean hasCompanyName() {
        return companyName != null && !companyName.isBlank();
    }

    /** 解析主题色为 AWT Color，非法值回退商务蓝 */
    public java.awt.Color themeAwtColor() {
        try {
            if (themeColor != null && themeColor.matches("^#[0-9a-fA-F]{6}$")) {
                return java.awt.Color.decode(themeColor);
            }
        } catch (NumberFormatException ignored) {
            // 非法色值回退默认
        }
        return java.awt.Color.decode("#2B5AA7");
    }

    /** 主题色浅色变体（表头浅底、装饰线用） */
    public java.awt.Color themeLightColor() {
        java.awt.Color c = themeAwtColor();
        // 与白色按 85% 混合，得到浅色变体
        int r = (int) (c.getRed() * 0.15 + 255 * 0.85);
        int g = (int) (c.getGreen() * 0.15 + 255 * 0.85);
        int b = (int) (c.getBlue() * 0.15 + 255 * 0.85);
        return new java.awt.Color(r, g, b);
    }
}
