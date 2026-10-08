package com.jjx.sales.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.SalesDeliveryItem;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.vo.SalesDeliveryVO;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import org.apache.poi.ss.util.CellRangeAddress;

/** 可编辑的 QR-026 送货单；只读本次发货快照，不用整单明细替代分批发货。 */
@Service
@RequiredArgsConstructor
public class SalesDeliveryExcelService {
    private final ISalesDeliveryService deliveryService;
    private final OrderMapper orderMapper;
    private final SysConfigService configService;

    public byte[] export(SalesDeliveryVO delivery) throws IOException {
        return export(delivery, false, true);
    }

    public byte[] export(SalesDeliveryVO delivery, boolean showAmount, boolean showWeight) throws IOException {
        if (delivery.getItems() == null || delivery.getItems().isEmpty()) {
            throw new BusinessException("该送货单缺少本次发货明细，请核对后再导出");
        }
        SalesOrder order = delivery.getOrderId() == null ? null : orderMapper.selectOne(
                Wrappers.<SalesOrder>lambdaQuery().select(SalesOrder::getOrderNo)
                        .eq(SalesOrder::getOrderId, delivery.getOrderId()));
        return buildWorkbook(delivery, order == null ? "" : order.getOrderNo(),
                configService.getValue("company_name"), configService.getValue("company_address"),
                configService.getValue("company_phone"), configService.getValue("company_fax"), showAmount, showWeight);
    }

    public SalesDeliveryVO getDelivery(Long deliveryId) {
        SalesDeliveryVO delivery = deliveryService.getById(deliveryId);
        if (delivery == null) throw new BusinessException("送货单不存在");
        return delivery;
    }

    static final String TEMPLATE = "/templates/sales/qr026-delivery.xlsx";
    private static final int ROWS_PER_SHEET = 6;

    static byte[] buildWorkbook(SalesDeliveryVO delivery, String orderNo,
                                String companyName, String companyAddress) throws IOException {
        return buildWorkbook(delivery, orderNo, companyName, companyAddress, "", "", false, true);
    }

    static byte[] buildWorkbook(SalesDeliveryVO delivery, String orderNo, String companyName,
                                String companyAddress, String companyPhone, String companyFax,
                                boolean showAmount, boolean showWeight) throws IOException {
        try (InputStream template = SalesDeliveryExcelService.class.getResourceAsStream(TEMPLATE)) {
            if (template == null) throw new IOException("QR-026送货单模板缺失");
            try (XSSFWorkbook workbook = new XSSFWorkbook(template);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                // 仅保留原表单；原模板中的空白工作表不参与导出。
                while (workbook.getNumberOfSheets() > 1) workbook.removeSheetAt(1);
                workbook.setSheetName(0, "送货单-1");
                List<SalesDeliveryItem> items = delivery.getItems() == null ? List.of() : delivery.getItems();
                int pages = Math.max(1, (items.size() + ROWS_PER_SHEET - 1) / ROWS_PER_SHEET);
                // 在填值前复制空模板，避免上一页数据带入下一页。
                for (int page = 1; page < pages; page++) {
                    workbook.cloneSheet(0, "送货单-" + (page + 1));
                }
                for (int page = 0; page < pages; page++) {
                    Sheet sheet = workbook.getSheetAt(page);
                    fillLatestForm(workbook, sheet, delivery, items, page, pages, orderNo,
                            companyName, companyPhone, companyFax, showAmount, showWeight);
                }
                addSourceData(workbook, delivery, items, orderNo, companyName, companyAddress);
                workbook.setActiveSheet(0);
                workbook.write(output);
                return output.toByteArray();
            }
        }
    }

    /** 对齐最新实物单：六行、销售单号与客户订单号码分别显示；无真实客户订单/单重时留空。 */
    private static void fillLatestForm(XSSFWorkbook workbook, Sheet sheet, SalesDeliveryVO delivery,
                                      List<SalesDeliveryItem> items, int page, int pages, String orderNo,
                                      String companyName, String companyPhone, String companyFax,
                                      boolean showAmount, boolean showWeight) {
        List<String> headers = new ArrayList<>(List.of("NO", "物料料号", "品名规格", "单位", "数量", "销售单号"));
        List<Integer> widths = new ArrayList<>(List.of(5, 17, 23, 6, 8, 14));
        if (showAmount) { headers.add("金额"); widths.add(9); }
        headers.add("订单号码"); widths.add(13);
        if (showWeight) { headers.add("单重 g"); widths.add(7); }
        int lastCol = headers.size() - 1;
        // 保留仓库模板资产；导出副本按最新照片重排，不再复用旧的单价/备注列。
        while (sheet.getNumMergedRegions() > 0) sheet.removeMergedRegion(0);
        for (Row row : sheet) for (Cell current : row) current.setBlank();
        CellStyle plain = formStyle(workbook, false, false, 11);
        CellStyle meta = formStyle(workbook, false, false, 11);
        meta.setAlignment(HorizontalAlignment.LEFT);
        CellStyle heading = formStyle(workbook, true, false, 20);
        CellStyle title = formStyle(workbook, true, false, 16);
        CellStyle table = formStyle(workbook, false, true, 11);
        CellStyle tableHeader = formStyle(workbook, true, true, 11);
        CellStyle amount = formStyle(workbook, false, true, 11);
        amount.setAlignment(HorizontalAlignment.RIGHT);
        amount.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        for (int row = 0; row <= 16; row++) {
            Row current = sheet.getRow(row);
            if (current == null) current = sheet.createRow(row);
            current.setHeightInPoints(row >= 8 && row <= 13 ? 27 : 20);
            for (int col = 0; col <= lastCol; col++) {
                cell(sheet, row, col, null);
                current.getCell(col).setCellStyle(plain);
            }
        }
        mergedCell(sheet, 2, 0, lastCol, companyName, heading);
        sheet.getRow(2).setHeightInPoints(30);
        mergedCell(sheet, 3, 0, lastCol, (value(companyPhone).isBlank() ? "" : "TEL：" + companyPhone)
                + (value(companyFax).isBlank() ? "" : "　FAX：" + companyFax), plain);
        mergedCell(sheet, 4, 2, 5, "送　货　单", title);
        mergedCell(sheet, 4, 6, lastCol, "编号：JJX-QR-026", plain);
        sheet.getRow(4).setHeightInPoints(28);
        mergedCell(sheet, 5, 0, 5, "TO：" + value(delivery.getCustomerName()), meta);
        mergedCell(sheet, 5, 6, lastCol, "NO：" + value(delivery.getDeliveryNo()), plain);
        mergedCell(sheet, 6, 0, 5, "ATTN：" + value(delivery.getContactPerson()) + " " + value(delivery.getContactPhone()), meta);
        mergedCell(sheet, 6, 6, lastCol, "DATE：" + (delivery.getDeliveryDate() == null ? "" :
                new SimpleDateFormat("yyyy-MM-dd").format(delivery.getDeliveryDate())), plain);
        for (int col = 0; col <= lastCol; col++) {
            cell(sheet, 7, col, headers.get(col));
            sheet.getRow(7).getCell(col).setCellStyle(tableHeader);
            sheet.setColumnWidth(col, widths.get(col) * 256);
        }
        sheet.getRow(7).setHeightInPoints(27);
        for (int line = 0; line < ROWS_PER_SHEET; line++) {
            int row = 8 + line;
            for (int col = 0; col <= lastCol; col++) sheet.getRow(row).getCell(col).setCellStyle(table);
            int index = page * ROWS_PER_SHEET + line;
            if (index >= items.size()) continue;
            SalesDeliveryItem item = items.get(index);
            List<Object> values = new ArrayList<>();
            values.add(index + 1); values.add(materialNo(item)); values.add(productDescription(item));
            values.add(item.getUnit()); values.add(item.getQuantity());
            values.add(value(item.getOrderNo()).isBlank() ? orderNo : item.getOrderNo());
            if (showAmount) values.add(item.getAmount());
            values.add(null); // 当前订单没有客户采购订单号字段，不以系统销售单号冒充。
            if (showWeight) values.add(null); // 当前产品没有单重字段，不导入mock单重。
            for (int col = 0; col < values.size(); col++) cell(sheet, row, col, values.get(col));
            if (showAmount) sheet.getRow(row).getCell(6).setCellStyle(amount);
            int wrappedLines = Math.max(1, (productDescription(item).length() + 13) / 14);
            sheet.getRow(row).setHeightInPoints(Math.max(27, wrappedLines * 16));
        }
        if (page == pages - 1) {
            String remark = value(delivery.getRemark()).isBlank()
                    ? "以上货品有不符问题，请在10天内通知，方便我司处理，过期恕不负责。" : delivery.getRemark();
            CellStyle note = formStyle(workbook, false, true, 11);
            note.setAlignment(HorizontalAlignment.LEFT);
            mergedCell(sheet, 14, 0, lastCol, "备注：" + remark, note);
            sheet.getRow(14).setHeightInPoints(Math.max(30, ((remark.length() + 49) / 50) * 16));
            mergedCell(sheet, 15, 0, 4, "送货单位经手人：" + value(delivery.getDeliveryPersonName()) + "________", plain);
            mergedCell(sheet, 15, 5, lastCol, "收货单位经手人：" + value(delivery.getReceiverName()) + "________", plain);
            sheet.getRow(15).setHeightInPoints(30);
        }
        if (pages > 1) mergedCell(sheet, 16, 0, lastCol, "第 " + (page + 1) + " / " + pages + " 页", plain);
        sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.getPrintSetup().setLandscape(false);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 1);
        sheet.setFitToPage(true);
        sheet.setMargin(PageMargin.LEFT, 15d / 25.4);
        sheet.setMargin(PageMargin.RIGHT, 15d / 25.4);
        sheet.setMargin(PageMargin.TOP, 12d / 25.4);
        sheet.setMargin(PageMargin.BOTTOM, 12d / 25.4);
        workbook.setPrintArea(page, 0, lastCol, 0, 16);
    }

    private static CellStyle formStyle(XSSFWorkbook workbook, boolean bold, boolean border, int size) {
        Font font = workbook.createFont(); font.setFontName("宋体"); font.setFontHeightInPoints((short) size); font.setBold(bold);
        CellStyle style = workbook.createCellStyle(); style.setFont(font); style.setWrapText(true);
        style.setAlignment(HorizontalAlignment.CENTER); style.setVerticalAlignment(VerticalAlignment.CENTER);
        if (border) { style.setBorderTop(BorderStyle.THIN); style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN); style.setBorderRight(BorderStyle.THIN); }
        return style;
    }

    private static void mergedCell(Sheet sheet, int row, int firstCol, int lastCol, String text, CellStyle style) {
        for (int col = firstCol; col <= lastCol; col++) {
            cell(sheet, row, col, null); sheet.getRow(row).getCell(col).setCellStyle(style);
        }
        if (lastCol > firstCol) sheet.addMergedRegion(new CellRangeAddress(row, row, firstCol, lastCol));
        cell(sheet, row, firstCol, text);
    }

    private static String productDescription(SalesDeliveryItem item) {
        String name = value(item.getProductName()), spec = value(item.getSpecification());
        return name.isBlank() ? spec : spec.isBlank() || name.equals(spec) ? name : name + " " + spec;
    }

    /** 完整业务数据另存一页，打印隐藏金额时也不丢失价格、费用与收货地址。 */
    private static void addSourceData(XSSFWorkbook workbook, SalesDeliveryVO delivery, List<SalesDeliveryItem> items,
                                      String orderNo, String companyName, String companyAddress) {
        Sheet sheet = workbook.createSheet("发货数据");
        Object[][] fields = {
                {"公司", companyName}, {"公司地址", companyAddress},
                {"送货单号", delivery.getDeliveryNo()}, {"源订单号", delivery.getOrderNos() == null || delivery.getOrderNos().isEmpty() ? orderNo : String.join("、", delivery.getOrderNos())},
                {"客户", delivery.getCustomerName()}, {"联系人", delivery.getContactPerson()},
                {"联系电话", delivery.getContactPhone()}, {"收货地址", delivery.getDeliveryAddress()},
                {"发货日期", delivery.getDeliveryDate() == null ? "" : new SimpleDateFormat("yyyy-MM-dd").format(delivery.getDeliveryDate())},
                {"承运商", delivery.getCarrier()}, {"物流单号", delivery.getTrackingNo()},
                {"单据合计金额（原值）", delivery.getTotalAmount()}, {"运费", delivery.getFreightAmount()},
                {"保价费", delivery.getInsuranceAmount()}, {"其他费用", delivery.getOtherCharges()},
                {"备注", delivery.getRemark()}, {"送货经手人", delivery.getDeliveryPersonName()}
        };
        for (int row = 0; row < fields.length; row++) {
            cell(sheet, row, 0, fields[row][0]);
            cell(sheet, row, 1, fields[row][1]);
        }
        int row = fields.length + 1;
        String[] headers = {"序号", "料号", "品名", "规格", "单位", "数量", "单价", "金额", "销售单号", "备注", "客户料号"};
        for (int col = 0; col < headers.length; col++) cell(sheet, row, col, headers[col]);
        for (int i = 0; i < items.size(); i++) {
            SalesDeliveryItem item = items.get(i);
            Object[] values = {i + 1, item.getProductCode(), item.getProductName(), item.getSpecification(),
                    item.getUnit(), item.getQuantity(), item.getUnitPrice(), item.getAmount(), value(item.getOrderNo()).isBlank() ? orderNo : item.getOrderNo(), item.getRemark(), materialNo(item)};
            for (int col = 0; col < values.length; col++) cell(sheet, row + i + 1, col, values[col]);
        }
        for (int col = 0; col < headers.length; col++) sheet.setColumnWidth(col, (col == 3 ? 36 : 22) * 256);
    }

    private static String materialNo(SalesDeliveryItem item) { return value(item.getCustomerMaterialNo()).isBlank() ? item.getProductName() : item.getCustomerMaterialNo(); }

    private static String value(String value) { return value == null ? "" : value; }

    /** 写入纯文本/数值，客户内容不会被解释为公式。 */
    private static void cell(Sheet sheet, int rowIndex, int col, Object value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else if (value == null) cell.setBlank();
        else cell.setCellValue(value.toString());
    }
}
