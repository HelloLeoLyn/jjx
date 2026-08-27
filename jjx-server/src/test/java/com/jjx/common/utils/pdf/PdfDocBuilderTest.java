package com.jjx.common.utils.pdf;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 临时验证：单据 PDF 渲染（字体加载/中文/表格），生成到 /tmp 供人工查看
 */
class PdfDocBuilderTest {

    @Test
    void renderQuotationPdf() throws Exception {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("报价单号", "QT2608050001");
        info.put("报价日期", "2026-08-05");
        info.put("客户名称", "测试客户有限公司");
        info.put("有效期至", "2026-09-05");
        info.put("联系人", "张三 13912345678");
        info.put("币种", "USD (汇率 7.2)");
        info.put("来源询价", "INQ2608050007");
        info.put("销售负责人", "sales01");

        List<String[]> rows = List.of(
                new String[]{"1", "P-001", "薄膜开关 5层 / 50×30×0.2 / 印刷 / 蓝色", "100", "个", "1.50", "150.00"},
                new String[]{"2", "P-002", "面板 / 80×40 / PET", "50", "个", "3.20", "160.00"}
        );

        byte[] pdf = PdfDocBuilder.create()
                .title("报  价  单")
                .info(info)
                .items(new String[]{"序号", "产品编码", "产品名称/规格", "数量", "单位", "单价", "金额"}, rows)
                .amounts(new String[][]{
                        {"小计", "310.00"},
                        {"税率(%)", "13"},
                        {"税额", "40.30"},
                        {"折扣", "0.00"},
                        {"合计", "350.30"},
                })
                .remark("以上报价含税，交货期 15 天。")
                .signatures("销售负责人：sales01", "客户确认：", "日期：")
                .toBytes();

        Path out = Path.of("/tmp/test-quotation.pdf");
        Files.write(out, pdf);
        System.out.println("PDF 生成成功: " + out + " (" + pdf.length + " bytes)");
        assertTrue(pdf.length > 2000, "PDF 文件过小，可能渲染失败");

        // 中文内容回读验证（乱码时提取不出中文）
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        System.out.println("PDF 文本: " + text.replace("\n", " | ").substring(0, Math.min(120, text.length())));
        assertTrue(text.contains("报价单号") && text.contains("测试客户有限公司") && text.contains("薄膜开关"), "PDF 中文渲染异常: " + text);
    }
}
