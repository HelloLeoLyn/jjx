package com.jjx.common.utils.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * 单据 PDF 构建器（DEV-160/595，基于 OpenPDF）
 * <p>
 * 中文字体：项目内嵌文泉驿微米黑（resources/fonts/wqy-microhei.ttc，TTC 取第 0 个字体），
 * 不依赖服务器系统字体，打包 jar 后依然可用（classpath 复制到临时文件加载）。
 * <p>
 * 2026-08-07：支持后台配置模板（PdfTemplateConfig）——公司抬头/主题色/签名栏/页脚，
 * 通过 {@link #withConfig(PdfTemplateConfig)} 传入；不传时保持基础样式（兼容旧调用）。
 */
@Slf4j
public class PdfDocBuilder {

    private static final String FONT_PATH = "fonts/wqy-microhei.ttc";
    private static BaseFont CN_FONT;

    private final Document document;
    private final ByteArrayOutputStream output;

    /** 模板配置（可为 null，为 null 时使用基础样式） */
    private PdfTemplateConfig config;

    private PdfDocBuilder() {
        document = new Document(PageSize.A4, 40, 40, 40, 40);
        output = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, output);
            document.open();
        } catch (DocumentException e) {
            throw new IllegalStateException("PDF 文档初始化失败", e);
        }
    }

    public static PdfDocBuilder create() {
        return new PdfDocBuilder();
    }

    /** 设置模板配置（后台可配置：公司抬头/主题色/签名栏/页脚） */
    public PdfDocBuilder withConfig(PdfTemplateConfig config) {
        this.config = config;
        return this;
    }

    /** 中文字体（懒加载，线程安全） */
    private static synchronized BaseFont cnFont() {
        if (CN_FONT == null) {
            try {
                ClassPathResource resource = new ClassPathResource(FONT_PATH);
                Path tmp = Files.createTempFile("wqy-font-", ".ttc");
                Files.copy(resource.getInputStream(), tmp, StandardCopyOption.REPLACE_EXISTING);
                // TTC 集合取第 0 个字体，嵌入子集保证任何环境可显示
                CN_FONT = BaseFont.createFont(tmp.toString() + ",0", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                log.info("中文字体加载成功: {}", FONT_PATH);
            } catch (IOException | DocumentException e) {
                throw new IllegalStateException("中文字体加载失败: " + FONT_PATH, e);
            }
        }
        return CN_FONT;
    }

    // ─────────── 构建方法 ───────────

    /** 单据标题（居中加粗；有配置时带主题色下划线装饰） */
    public PdfDocBuilder title(String text) {
        // 有配置且显示抬头 → 先渲染公司抬头区
        if (config != null && config.isShowHeader() && config.hasCompanyName()) {
            renderHeader();
        }

        Font font = new Font(cnFont(), 20, Font.BOLD);
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(6);
        document.add(p);

        // 主题色下划线装饰
        if (config != null) {
            java.awt.Color theme = config.themeAwtColor();
            PdfPTable line = new PdfPTable(1);
            line.setWidthPercentage(30);
            line.setHorizontalAlignment(Element.ALIGN_CENTER);
            line.setSpacingAfter(14);
            PdfPCell lc = new PdfPCell();
            lc.setFixedHeight(2f);
            lc.setBorder(0);
            lc.setBackgroundColor(theme);
            line.addCell(lc);
            document.add(line);
        } else {
            p.setSpacingAfter(16);
        }
        return this;
    }

    /** 公司抬头区：公司名（主题色大字）+ 地址/电话/邮箱（小字灰） */
    private void renderHeader() {
        java.awt.Color theme = config.themeAwtColor();

        // 公司名
        Font nameFont = new Font(cnFont(), 18, Font.BOLD, theme);
        Paragraph name = new Paragraph(config.getCompanyName(), nameFont);
        name.setAlignment(Element.ALIGN_CENTER);
        name.setSpacingAfter(2);
        document.add(name);

        // 联系信息行
        StringBuilder contact = new StringBuilder();
        if (notBlank(config.getCompanyAddress())) contact.append("地址：").append(config.getCompanyAddress());
        if (notBlank(config.getCompanyPhone())) {
            if (contact.length() > 0) contact.append("    ");
            contact.append("电话：").append(config.getCompanyPhone());
        }
        if (notBlank(config.getCompanyEmail())) {
            if (contact.length() > 0) contact.append("    ");
            contact.append("邮箱：").append(config.getCompanyEmail());
        }
        if (contact.length() > 0) {
            Font contactFont = new Font(cnFont(), 9, Font.NORMAL, new java.awt.Color(0x88, 0x88, 0x88));
            Paragraph cp = new Paragraph(contact.toString(), contactFont);
            cp.setAlignment(Element.ALIGN_CENTER);
            cp.setSpacingAfter(8);
            document.add(cp);
        } else {
            Paragraph spacer = new Paragraph(" ", new Font(cnFont(), 8, Font.NORMAL));
            spacer.setSpacingAfter(6);
            document.add(spacer);
        }
    }

    /** 单据信息区（两对 label/value，4 列；有配置时 label 浅色底） */
    public PdfDocBuilder info(Map<String, String> fields) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        Font labelFont = new Font(cnFont(), 10, Font.BOLD);
        Font valueFont = new Font(cnFont(), 10, Font.NORMAL);
        java.awt.Color labelBg = config != null ? config.themeLightColor() : new java.awt.Color(0xF5, 0xF5, 0xF5);
        int i = 0;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            PdfPCell lc = cell(e.getKey(), labelFont, Element.ALIGN_LEFT, 1);
            lc.setBackgroundColor(labelBg);
            table.addCell(lc);
            table.addCell(cell(valueOf(e.getValue()), valueFont, Element.ALIGN_LEFT, 1));
            i += 2;
        }
        // 字段为奇数时补齐空列，保证 4 列对齐
        while (i % 4 != 0) {
            PdfPCell ec = cell("", labelFont, Element.ALIGN_LEFT, 1);
            ec.setBackgroundColor(labelBg);
            table.addCell(ec);
            i++;
        }
        document.add(table);
        return this;
    }

    /** 明细表格（有配置时：表头主题色底白字 + 斑马纹 + 数字右对齐） */
    public PdfDocBuilder items(String[] headers, List<String[]> rows) {
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        Font headerFont = new Font(cnFont(), 10, Font.BOLD);
        Font cellFont = new Font(cnFont(), 10, Font.NORMAL);
        java.awt.Color headerBg = config != null ? config.themeAwtColor() : new java.awt.Color(0xF0, 0xF0, 0xF0);
        java.awt.Color headerFg = config != null ? java.awt.Color.WHITE : java.awt.Color.BLACK;

        Font headerFontColored = new Font(cnFont(), 10, Font.BOLD, headerFg);
        for (String h : headers) {
            PdfPCell c = cell(h, headerFontColored, Element.ALIGN_CENTER, 1);
            c.setBackgroundColor(headerBg);
            table.addCell(c);
        }
        int rowIdx = 0;
        for (String[] row : rows) {
            boolean numeric = rowIdx >= 0 && row.length > 3; // 数量及之后右对齐
            for (int j = 0; j < headers.length; j++) {
                boolean isNumeric = j >= 3 && j <= 6; // 数量/单位/单价/金额右对齐
                PdfPCell c = cell(j < row.length ? valueOf(row[j]) : "", cellFont,
                        isNumeric ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, 1);
                // 斑马纹（有配置时）
                if (config != null && rowIdx % 2 == 1) {
                    c.setBackgroundColor(new java.awt.Color(0xF7, 0xF9, 0xFC));
                }
                table.addCell(c);
            }
            rowIdx++;
        }
        document.add(table);
        return this;
    }

    /** 金额汇总（label/value 两列，右对齐表格；有配置时合计行主题色底） */
    public PdfDocBuilder amounts(String[][] rows) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(38);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingAfter(12);
        Font labelFont = new Font(cnFont(), 10, Font.BOLD);
        Font valueFont = new Font(cnFont(), 10, Font.BOLD);
        for (int i = 0; i < rows.length; i++) {
            String[] row = rows[i];
            boolean isTotal = i == rows.length - 1;
            PdfPCell lc = cell(row[0], labelFont, Element.ALIGN_CENTER, 1);
            PdfPCell vc = cell(valueOf(row[1]), valueFont, Element.ALIGN_RIGHT, 1);
            if (isTotal && config != null) {
                java.awt.Color theme = config.themeAwtColor();
                lc.setBackgroundColor(theme);
                vc.setBackgroundColor(theme);
                Font totalFont = new Font(cnFont(), 11, Font.BOLD, java.awt.Color.WHITE);
                lc.setPhrase(new Phrase(row[0], totalFont));
                vc.setPhrase(new Phrase(valueOf(row[1]), totalFont));
            } else {
                lc.setBackgroundColor(new java.awt.Color(0xF0, 0xF0, 0xF0));
            }
            table.addCell(lc);
            table.addCell(vc);
        }
        document.add(table);
        return this;
    }

    /** 备注 */
    public PdfDocBuilder remark(String text) {
        if (text == null || text.isBlank()) {
            return this;
        }
        Font font = new Font(cnFont(), 10, Font.NORMAL, new java.awt.Color(0x55, 0x55, 0x55));
        Paragraph p = new Paragraph("备注：" + text, font);
        p.setSpacingAfter(14);
        document.add(p);
        return this;
    }

    /** 签名区（有配置时用配置的签名栏标题，并加下划线留白） */
    public PdfDocBuilder signatures(String... labels) {
        String[] finalLabels = labels;
        if (config != null) {
            finalLabels = new String[]{
                    config.getSignatureLabel1() == null || config.getSignatureLabel1().isBlank() ? (labels.length > 0 ? labels[0] : "") : config.getSignatureLabel1(),
                    config.getSignatureLabel2() == null || config.getSignatureLabel2().isBlank() ? (labels.length > 1 ? labels[1] : "") : config.getSignatureLabel2(),
                    config.getSignatureLabel3() == null || config.getSignatureLabel3().isBlank() ? (labels.length > 2 ? labels[2] : "") : config.getSignatureLabel3(),
            };
        }
        PdfPTable table = new PdfPTable(finalLabels.length);
        table.setWidthPercentage(100);
        table.setSpacingBefore(20);
        Font font = new Font(cnFont(), 10, Font.NORMAL);
        for (String label : finalLabels) {
            PdfPCell c = new PdfPCell(new Phrase(label, font));
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setBorder(0);
            c.setPaddingTop(24);
            c.setPaddingBottom(4);
            // 底部下划线
            PdfPCell line = new PdfPCell(new Phrase(" ", new Font(cnFont(), 6, Font.NORMAL)));
            line.setBorder(0);
            line.setBorderWidthTop(0);
            line.setFixedHeight(0);
            table.addCell(c);
        }
        document.add(table);
        return this;
    }

    /** 生成 PDF 字节数组 */
    public byte[] toBytes() {
        // 页脚：页号（有配置且开启时）
        if (config != null && config.isShowFooter()) {
            addFooter();
        }
        document.close();
        return output.toByteArray();
    }

    /** 页脚：底部居中显示公司名（多页页号需 PageEvent，后续可增强） */
    private void addFooter() {
        if (config.hasCompanyName()) {
            Paragraph footer = new Paragraph(config.getCompanyName(),
                    new Font(cnFont(), 9, Font.NORMAL, new java.awt.Color(0xAA, 0xAA, 0xAA)));
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(12);
            try {
                document.add(footer);
            } catch (Exception ignored) {
                // 文档已关闭时忽略
            }
        }
    }

    // ─────────── 内部方法 ───────────

    private PdfPCell cell(String text, Font font, int align, int border) {
        PdfPCell c = new PdfPCell(new Phrase(valueOf(text), font));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
        if (border == 0) {
            c.setBorder(0);
        }
        return c;
    }

    private static String valueOf(String s) {
        return s == null ? "" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
