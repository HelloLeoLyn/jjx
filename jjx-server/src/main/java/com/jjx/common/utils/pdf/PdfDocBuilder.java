package com.jjx.common.utils.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
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
 */
@Slf4j
public class PdfDocBuilder {

    private static final String FONT_PATH = "fonts/wqy-microhei.ttc";
    private static BaseFont CN_FONT;

    private final Document document;
    private final ByteArrayOutputStream output;

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

    /** 单据标题（居中加粗） */
    public PdfDocBuilder title(String text) {
        Font font = new Font(cnFont(), 20, Font.BOLD);
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(16);
        document.add(p);
        return this;
    }

    /** 单据信息区（两对 label/value，4 列） */
    public PdfDocBuilder info(Map<String, String> fields) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.getDefaultCell().setBorder(0);
        table.getDefaultCell().setPadding(3);
        Font labelFont = new Font(cnFont(), 10, Font.NORMAL, new java.awt.Color(0x66, 0x66, 0x66));
        Font valueFont = new Font(cnFont(), 10, Font.NORMAL);
        int i = 0;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            table.addCell(cell(e.getKey(), labelFont, Element.ALIGN_LEFT, 0));
            table.addCell(cell(valueOf(e.getValue()), valueFont, Element.ALIGN_LEFT, 0));
            i += 2;
        }
        // 字段为奇数时补齐空列，保证 4 列对齐
        while (i % 4 != 0) {
            table.addCell(cell("", labelFont, Element.ALIGN_LEFT, 0));
            i++;
        }
        table.setSpacingAfter(10);
        document.add(table);
        return this;
    }

    /** 明细表格 */
    public PdfDocBuilder items(String[] headers, List<String[]> rows) {
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        Font headerFont = new Font(cnFont(), 10, Font.BOLD);
        Font cellFont = new Font(cnFont(), 10, Font.NORMAL);
        for (String h : headers) {
            PdfPCell c = cell(h, headerFont, Element.ALIGN_CENTER, 1);
            c.setBackgroundColor(new java.awt.Color(0xF0, 0xF0, 0xF0));
            table.addCell(c);
        }
        for (String[] row : rows) {
            for (int j = 0; j < headers.length; j++) {
                boolean numeric = j >= 3 && j <= 6; // 数量/单位/单价/金额右对齐
                table.addCell(cell(j < row.length ? valueOf(row[j]) : "", cellFont,
                        numeric ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, 1));
            }
        }
        document.add(table);
        return this;
    }

    /** 金额汇总（label/value 两列，右对齐表格） */
    public PdfDocBuilder amounts(String[][] rows) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(38);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingAfter(12);
        Font labelFont = new Font(cnFont(), 10, Font.BOLD);
        Font valueFont = new Font(cnFont(), 10, Font.BOLD);
        for (String[] row : rows) {
            PdfPCell lc = cell(row[0], labelFont, Element.ALIGN_CENTER, 1);
            lc.setBackgroundColor(new java.awt.Color(0xF0, 0xF0, 0xF0));
            table.addCell(lc);
            table.addCell(cell(valueOf(row[1]), valueFont, Element.ALIGN_RIGHT, 1));
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

    /** 签名区 */
    public PdfDocBuilder signatures(String... labels) {
        PdfPTable table = new PdfPTable(labels.length);
        table.setWidthPercentage(100);
        table.getDefaultCell().setBorder(0);
        table.getDefaultCell().setPadding(8);
        Font font = new Font(cnFont(), 10, Font.NORMAL);
        for (String label : labels) {
            table.addCell(cell(label, font, Element.ALIGN_CENTER, 0));
        }
        document.add(table);
        return this;
    }

    /** 生成 PDF 字节数组 */
    public byte[] toBytes() {
        document.close();
        return output.toByteArray();
    }

    // ─────────── 内部方法 ───────────

    private PdfPCell cell(String text, Font font, int align, int border) {
        PdfPCell c = new PdfPCell(new com.lowagie.text.Phrase(valueOf(text), font));
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
}
