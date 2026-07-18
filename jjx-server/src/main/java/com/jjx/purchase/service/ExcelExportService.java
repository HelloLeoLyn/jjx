package com.jjx.purchase.service;

import com.jjx.purchase.domain.dto.OrderExportDTO;
import com.jjx.purchase.domain.dto.OrderItemDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelExportService {

    public static byte[] exportOrder(OrderExportDTO order) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("订购单");

            // 设置列宽（根据模板调整）
            setColumnWidths(sheet);

            // 创建样式
            Map<String, CellStyle> styles = createStyles(workbook);

            int rowNum = 0;

            // 第1行：公司名称
            rowNum = createCompanyName(sheet, styles, rowNum);

            // 第2行：地址
            rowNum = createAddress(sheet, styles, rowNum);

            // 第3行：电话传真
            rowNum = createPhone(sheet, styles, rowNum);

            // 第4行：Email
            rowNum = createEmail(sheet, styles, rowNum);

            // 第6行：订购单标题
            rowNum = createOrderTitle(sheet, styles, rowNum);

            // 第6行：空行
            rowNum = createEmptyRow(sheet, rowNum);

            // 第7行：空行 + 订单号码
            rowNum = createOrderNoRow(sheet, styles, rowNum, order);

            // 第8行：厂商 + 订货时间
            rowNum = createSupplierRow(sheet, styles, rowNum, order);

            // 第9行：联系人 + 交货时间
            rowNum = createContactRow(sheet, styles, rowNum, order);

            // 第10行：TEL + 交易方式
            rowNum = createTelAndTradeRow(sheet, styles, rowNum, order);

            // 第11行：表头
            rowNum = createTableHeader(sheet, styles, rowNum);

            // 第12行及以后：数据行
            rowNum = createDataRows(sheet, styles, rowNum, order.getItems());

            // 合计行
            rowNum = createTotalRow(sheet, styles, rowNum, order);

            // 交易条款
            rowNum = createTermsRows(sheet, styles, rowNum);

            // 空行
            rowNum = createEmptyRow(sheet, rowNum);

            // JJX-QR-024
            rowNum = createCodeRow(sheet, styles, rowNum);

            // 签名行
            createSignatureRow(sheet, styles, rowNum);

            // 输出
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("导出Excel失败", e);
        }
    }

    private static void setColumnWidths(Sheet sheet) {
        sheet.setColumnWidth(0, 2560);  // A列 - 项次
        sheet.setColumnWidth(1, 5120);  // B列 - 品名
        sheet.setColumnWidth(2, 5120);  // C列 - 规格
        sheet.setColumnWidth(3, 2560);  // D列 - 单位
        sheet.setColumnWidth(4, 2560);  // E列 - 数量
        sheet.setColumnWidth(5, 2560);  // F列 - 单价
        sheet.setColumnWidth(6, 3840);  // G列 - 金额
        sheet.setColumnWidth(7, 3840);  // H列 - 备注
    }

    private static Map<String, CellStyle> createStyles(Workbook workbook) {
        Map<String, CellStyle> styles = new HashMap<>();

        // 1. 公司名称样式（黑体16号居中）
        Font companyFont = workbook.createFont();
        companyFont.setFontHeightInPoints((short) 20);
        companyFont.setFontName("宋体");
        companyFont.setBold(true);
        CellStyle companyStyle = workbook.createCellStyle();
        companyStyle.setFont(companyFont);
        companyStyle.setAlignment(HorizontalAlignment.CENTER);
        companyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.put("company", companyStyle);

        // 2. 地址信息样式（宋体10号居中）
        Font addressFont = workbook.createFont();
        addressFont.setFontHeightInPoints((short) 10);
        addressFont.setFontName("宋体");
        CellStyle addressStyle = workbook.createCellStyle();
        addressStyle.setFont(addressFont);
        addressStyle.setAlignment(HorizontalAlignment.CENTER);
        addressStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.put("address", addressStyle);

        // 3. 订购单标题样式（黑体18号居中）
        Font titleFont = workbook.createFont();
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setFontName("黑体");
        titleFont.setBold(true);
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.put("title", titleStyle);

        // 4. 标签样式（加粗）--厂商
        Font labelFont = workbook.createFont();
        labelFont.setFontHeightInPoints((short) 12);
        labelFont.setFontName("宋体");
        CellStyle labelStyle = workbook.createCellStyle();
        labelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        labelStyle.setFont(labelFont);
        styles.put("label", labelStyle);

        // 5. 值样式（常规）
        Font valueFont = workbook.createFont();
        valueFont.setFontHeightInPoints((short) 12);
        valueFont.setFontName("宋体");
        CellStyle valueStyle = workbook.createCellStyle();
        valueStyle.setFont(valueFont);
        valueStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.put("value", valueStyle);

        // 6. 表头样式（加粗居中带边框）
        Font headerFont = workbook.createFont();
        headerFont.setFontHeightInPoints((short) 12);
        headerFont.setFontName("宋体");
        headerFont.setBold(true);
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        styles.put("header", headerStyle);

        // 7. 数据样式（带边框）
        Font dataFont = workbook.createFont();
        dataFont.setFontHeightInPoints((short) 10);
        dataFont.setFontName("宋体");
        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setFont(dataFont);
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        dataStyle.setAlignment(HorizontalAlignment.CENTER);
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.put("data", dataStyle);

        // 8. 数字样式（右对齐）
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.cloneStyleFrom(dataStyle);
        numberStyle.setAlignment(HorizontalAlignment.RIGHT);
        styles.put("number", numberStyle);

        // 9. 交易条款样式
        Font termsFont = workbook.createFont();
        termsFont.setFontHeightInPoints((short) 9);
        termsFont.setFontName("宋体");
        CellStyle termsStyle = workbook.createCellStyle();
        termsStyle.setFont(termsFont);
        termsStyle.setVerticalAlignment(VerticalAlignment.TOP);
        styles.put("terms", termsStyle);

        // 10. 签名项样式
        Font signFont = workbook.createFont();
        signFont.setFontHeightInPoints((short) 11);
        signFont.setFontName("宋体");
        CellStyle signStyle = workbook.createCellStyle();
        signStyle.setFont(signFont);
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.put("sign", termsStyle);

        return styles;
    }

    private static int createCompanyName(Sheet sheet, Map<String, CellStyle> styles, int rowNum) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(28);
        Cell cell = row.createCell(0);
        cell.setCellValue("深圳市精捷信科技有限公司");
        cell.setCellStyle(styles.get("company"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 7));
        return rowNum + 1;
    }

    private static int createAddress(Sheet sheet, Map<String, CellStyle> styles, int rowNum) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(18);
        Cell cell = row.createCell(0);
        cell.setCellValue("地址：深圳市宝安区沙井街共和村丽城工业园F栋4楼");
        cell.setCellStyle(styles.get("address"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 7));
        return rowNum + 1;
    }

    private static int createPhone(Sheet sheet, Map<String, CellStyle> styles, int rowNum) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(18);
        Cell cell = row.createCell(0);
        cell.setCellValue("电话：0755-21507378  传真：0755-29856700");
        cell.setCellStyle(styles.get("address"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 7));
        return rowNum + 1;
    }

    private static int createEmail(Sheet sheet, Map<String, CellStyle> styles, int rowNum) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(18);
        Cell cell = row.createCell(0);
        cell.setCellValue("E-mail: cg@jjx.cc   QQ：1452049538");
        cell.setCellStyle(styles.get("address"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 7));
        return rowNum + 1;
    }

    private static int createEmptyRow(Sheet sheet, int rowNum) {
        sheet.createRow(rowNum);
        return rowNum + 1;
    }

    private static int createOrderTitle(Sheet sheet, Map<String, CellStyle> styles, int rowNum) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(30);
        Cell cell = row.createCell(0);
        cell.setCellValue("订  购  单");
        cell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 7));
        return rowNum + 1;
    }

    private static int createOrderNoRow(Sheet sheet, Map<String, CellStyle> styles, int rowNum, OrderExportDTO order) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(20);

        // 第6列（F列）显示"订单号码:"
        Cell cellF = row.createCell(5);
        cellF.setCellValue("订单号码:");
        cellF.setCellStyle(styles.get("label"));

        // 第7列（G列）显示订单号
        Cell cellG = row.createCell(6);
        cellG.setCellValue(order.getOrderNo() != null ? order.getOrderNo() : "");
        cellG.setCellStyle(styles.get("value"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 6, 7));

        return rowNum + 1;
    }

    private static int createSupplierRow(Sheet sheet, Map<String, CellStyle> styles, int rowNum, OrderExportDTO order) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(20);

        // A列: "厂商："
        Cell cellA = row.createCell(0);
        cellA.setCellValue("厂商：");
        cellA.setCellStyle(styles.get("label"));

        // B-E列: 厂商名称
        Cell cellB = row.createCell(1);
        cellB.setCellValue(order.getSupplierName() != null ? order.getSupplierName() : "");
        cellB.setCellStyle(styles.get("value"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 1, 4));

        // F列: "订货时间："
        Cell cellF = row.createCell(5);
        cellF.setCellValue("订货时间：");
        cellF.setCellStyle(styles.get("label"));

        // G列: 订货时间
        Cell cellG = row.createCell(6);
        cellG.setCellValue(order.getOrderDate() != null ? order.getOrderDate() : "");
        cellG.setCellStyle(styles.get("value"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 6, 7));

        return rowNum + 1;
    }

    private static int createContactRow(Sheet sheet, Map<String, CellStyle> styles, int rowNum, OrderExportDTO order) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(20);

        // A列: "联系人："
        Cell cellA = row.createCell(0);
        cellA.setCellValue("联系人：");
        cellA.setCellStyle(styles.get("label"));

        // B-E列: 联系人
        Cell cellB = row.createCell(1);
        cellB.setCellValue(order.getSupplierContact() != null ? order.getSupplierContact() : "");
        cellB.setCellStyle(styles.get("value"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 1, 4));

        // F列: "交货时间："
        Cell cellF = row.createCell(5);
        cellF.setCellValue("交货时间：");
        cellF.setCellStyle(styles.get("label"));


        // G列: 交货时间
        Cell cellG = row.createCell(6);
        cellG.setCellValue(order.getDeliveryDate() != null ? order.getDeliveryDate() : "");
        cellG.setCellStyle(styles.get("value"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 6, 7));

        return rowNum + 1;
    }

    private static int createTelAndTradeRow(Sheet sheet, Map<String, CellStyle> styles, int rowNum, OrderExportDTO order) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(20);

        // A列: "TEL："
        Cell cellA = row.createCell(0);
        cellA.setCellValue("TEL：");
        cellA.setCellStyle(styles.get("label"));

        // B-E列: 电话号码
        Cell cellB = row.createCell(1);
        cellB.setCellValue(order.getSupplierTel() != null ? order.getSupplierTel() : "");
        cellB.setCellStyle(styles.get("value"));
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 1, 4));

        // F列: "交易方式："
        Cell cellF = row.createCell(5);
        cellF.setCellValue("交易方式：");
        cellF.setCellStyle(styles.get("label"));

        // G列: 交易方式
        String tradeType = "RMB".equals(order.getTradeType()) ? "RMB 现结☑" : "RMB 现结□";
        Cell cellG = row.createCell(6);
        cellG.setCellValue(tradeType);
        cellG.setCellStyle(styles.get("value"));

        // H列: 交易方式
        String tradeTypeVal = "RMB".equals(order.getTradeType()) ? "月结□" : "月结☑";
        Cell cellH = row.createCell(7);
        cellH.setCellValue(tradeTypeVal);
        cellH.setCellStyle(styles.get("value"));

        return rowNum + 1;
    }

    private static int createTableHeader(Sheet sheet, Map<String, CellStyle> styles, int rowNum) {
        String[] headers = {"项次", "品名", "规格", "单位", "数量", "单价", "金额", "备注"};
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }
        return rowNum + 1;
    }

    private static int createDataRows(Sheet sheet, Map<String, CellStyle> styles, int startRow, List<OrderItemDTO> items) {
        int rowNum = startRow;

        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                OrderItemDTO item = items.get(i);
                Row row = sheet.createRow(rowNum);
                row.setHeightInPoints(18);

                // 项次
                Cell cell0 = row.createCell(0);
                cell0.setCellValue(i + 1);
                cell0.setCellStyle(styles.get("data"));

                // 品名
                Cell cell1 = row.createCell(1);
                cell1.setCellValue(item.getMaterialName() != null ? item.getMaterialName() : "");
                cell1.setCellStyle(styles.get("data"));

                // 规格
                Cell cell2 = row.createCell(2);
                cell2.setCellValue(item.getSpecification() != null ? item.getSpecification() : "");
                cell2.setCellStyle(styles.get("data"));

                // 单位
                Cell cell3 = row.createCell(3);
                cell3.setCellValue(item.getUnit() != null ? item.getUnit() : "");
                cell3.setCellStyle(styles.get("data"));

                // 数量
                Cell cell4 = row.createCell(4);
                double quantity = item.getQuantity() != null ? item.getQuantity().doubleValue() : 0;
                cell4.setCellValue(quantity);
                cell4.setCellStyle(styles.get("data"));

                // 单价
                Cell cell5 = row.createCell(5);
                double unitPrice = item.getUnitPrice() != null ? item.getUnitPrice().doubleValue() : 0;
                cell5.setCellValue(unitPrice);
                cell5.setCellStyle(styles.get("data"));

                // 金额（公式 = 数量 * 单价）
                Cell cell6 = row.createCell(6);
                cell6.setCellFormula("E" + (rowNum + 1) + "*F" + (rowNum + 1));
                cell6.setCellStyle(styles.get("data"));

                // 备注
                Cell cell7 = row.createCell(7);
                cell7.setCellValue(item.getRemark() != null ? item.getRemark() : "");
                cell7.setCellStyle(styles.get("data"));

                rowNum++;
            }
        }

        // 如果没有数据或数据为空，显示"以下空白"
        if (items == null || items.isEmpty()) {
            Row row = sheet.createRow(rowNum);
            row.setHeightInPoints(18);
            Cell cell = row.createCell(1);
            cell.setCellValue("以下空白");
            cell.setCellStyle(styles.get("data"));
            sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 1, 6));
            rowNum++;
        }

        return rowNum;
    }

    private static int createTotalRow(Sheet sheet, Map<String, CellStyle> styles, int startRow, OrderExportDTO order) {
        Row row = sheet.createRow(startRow);
        row.setHeightInPoints(22);

        // A列到F列：空单元格但要有边框
        for (int i = 0; i <= 5; i++) {
            Cell emptyCell = row.createCell(i);
            emptyCell.setCellValue("");
            // 使用数据样式（带边框）
            CellStyle emptyStyle = styles.get("data");
            emptyCell.setCellStyle(emptyStyle);
        }

        // G列: "合计:"
        Cell cellG = row.createCell(6);
        cellG.setCellValue("合计:");
        cellG.setCellStyle(styles.get("header"));

        // H列: 合计金额公式
        Cell cellH = row.createCell(7);
        // 计算金额列的范围（G列从第13行开始到当前行-1）
        final int startDataRow = 13;  // 表头在第12行，数据从第13行开始
        int endDataRow = startRow;
        cellH.setCellFormula("SUM(G" + startDataRow + ":G" + endDataRow + ")");
        cellH.setCellStyle(styles.get("data"));

        return startRow + 1;
    }

    private static int createTermsRows(Sheet sheet, Map<String, CellStyle> styles, int startRow) {
        final String terms = "交易条款：\n" +
                "1. 供方如无法遵守本订单交期，需及时通知需方调整；若延误交期给需方造成的直接经济损失将由供方负责，若因供方交货延时而导致需方客户退货，需方将无条件退回供方。\n" +
                "2. 供方的送货单上必须注明买方的订单号，品名，规格，数量，生产日期，货物需符合ROHS，REACH（255项）Non-Phtha1atc环保要求，若供应商所发货物不符合我厂要求而由此造成的一切的经济损失将由供应商负责承担.\n" +
                "3. 供方负责把需方所订货物送达需方指定地点，运费由供方承担。\n" +
                "4. 若供方所发货物不符合需方要求而由此造成的经济损失将由供方负责。需方在收到货物验收合格后按双方约定时间付款；\n" +
                "5. 供需双方在签订和履行合同过程中，要对双方商业信息负有保密责任。\n" +
                "6. 本合同双方盖章签字即生效，传真件具有同等效力，若出现纠纷，以需方所在地仲裁委员会仲裁或者需方所在地法院解决。\n" +
                "7. 供方收到需方所下订单后确认签字盖章回传，一日内没有异议视为认同。未经双方同意而作修改，涂改，添加的内容视为无效。\n" +
                "8. 货物检验后如发现品质不良，应在接到通知后3日内将货物取回，并尽快补回，逾期本公司概不负责。";

        // 交易条款需要多行显示，这里合并单元格后设置自动换行
        Row row = sheet.createRow(startRow);
        row.setHeightInPoints(220);
        Cell cell = row.createCell(0);
        cell.setCellValue(terms);
        cell.setCellStyle(styles.get("terms"));
        // 设置自动换行
        cell.getCellStyle().setWrapText(true);
        sheet.addMergedRegion(new CellRangeAddress(startRow, startRow, 0, 7));

        return startRow + 1;
    }

    private static int createCodeRow(Sheet sheet, Map<String, CellStyle> styles, int rowNum) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(20);
        Cell cell = row.createCell(6);
        cell.setCellValue("JJX-QR-024");
        cell.setCellStyle(styles.get("value"));
        return rowNum + 1;
    }

    private static void createSignatureRow(Sheet sheet, Map<String, CellStyle> styles, int rowNum) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(20);

        Cell cell0 = row.createCell(0);
        cell0.setCellValue("供应商回签：");
        cell0.setCellStyle(styles.get("label"));

        Cell cell3 = row.createCell(3);
        cell3.setCellValue("经理审核：");
        cell3.setCellStyle(styles.get("label"));

        Cell cell5 = row.createCell(6);
        cell5.setCellValue("制表人：");
        cell5.setCellStyle(styles.get("label"));
    }
}