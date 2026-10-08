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
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;

/** 可编辑的 QR-026 送货单；只读本次发货快照，不用整单明细替代分批发货。 */
@Service
@RequiredArgsConstructor
public class SalesDeliveryExcelService {
    private final ISalesDeliveryService deliveryService;
    private final OrderMapper orderMapper;
    private final SysConfigService configService;

    public byte[] export(SalesDeliveryVO delivery) throws IOException {
        if (delivery.getItems() == null || delivery.getItems().isEmpty()) {
            throw new BusinessException("该送货单缺少本次发货明细，请核对后再导出");
        }
        SalesOrder order = delivery.getOrderId() == null ? null : orderMapper.selectOne(
                Wrappers.<SalesOrder>lambdaQuery().select(SalesOrder::getOrderNo)
                        .eq(SalesOrder::getOrderId, delivery.getOrderId()));
        return buildWorkbook(delivery, order == null ? "" : order.getOrderNo(),
                configService.getValue("company_name"), configService.getValue("company_address"));
    }

    public SalesDeliveryVO getDelivery(Long deliveryId) {
        SalesDeliveryVO delivery = deliveryService.getById(deliveryId);
        if (delivery == null) throw new BusinessException("送货单不存在");
        return delivery;
    }

    static byte[] buildWorkbook(SalesDeliveryVO delivery, String orderNo,
                                String companyName, String companyAddress) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("送货单(QR-026)");
            int[] widths = {6, 16, 13, 7, 9, 10, 11, 15, 10};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            sheet.setDisplayGridlines(false);
            sheet.setDefaultRowHeightInPoints(28);
            CellStyle text = style(workbook, 11, false, HorizontalAlignment.LEFT, false, null);
            CellStyle center = style(workbook, 11, false, HorizontalAlignment.CENTER, true, null);
            CellStyle bordered = style(workbook, 11, false, HorizontalAlignment.LEFT, true, null);
            CellStyle quantity = style(workbook, 11, false, HorizontalAlignment.RIGHT, true, "0");
            CellStyle money = style(workbook, 11, false, HorizontalAlignment.RIGHT, true, "#,##0.00");
            CellStyle heading = style(workbook, 11, true, HorizontalAlignment.CENTER, true, null);
            merged(sheet, 0, 0, 8, companyName, style(workbook, 20, false, HorizontalAlignment.CENTER, false, null), 38);
            merged(sheet, 1, 0, 8, "送　货　单", style(workbook, 16, true, HorizontalAlignment.CENTER, false, null), 30);
            merged(sheet, 2, 0, 8, "公司地址：" + value(companyAddress), text, 32);
            merged(sheet, 3, 0, 4, "客户：" + value(delivery.getCustomerName()), text, 30);
            merged(sheet, 3, 5, 8, "送货单号：" + value(delivery.getDeliveryNo()), text, 30);
            merged(sheet, 4, 0, 4, "联系人：" + value(delivery.getContactPerson()) + "　" + value(delivery.getContactPhone()), text, 30);
            merged(sheet, 4, 5, 8, "日期：" + (delivery.getDeliveryDate() == null ? "" :
                    new SimpleDateFormat("yyyy-MM-dd").format(delivery.getDeliveryDate())), text, 30);
            merged(sheet, 5, 0, 8, "收货地址：" + value(delivery.getDeliveryAddress()), text, 40);
            merged(sheet, 6, 0, 8, "承运商：" + value(delivery.getCarrier()) + "　物流单号：" + value(delivery.getTrackingNo()), text, 30);
            String[] headers = {"NO", "品名(料号)", "规格", "单位", "数量", "单价", "金额", "订单号码", "备注"};
            for (int col = 0; col < headers.length; col++) cell(sheet, 7, col, headers[col], heading);
            List<SalesDeliveryItem> items = delivery.getItems() == null ? List.of() : delivery.getItems();
            for (int i = 0; i < Math.max(5, items.size()); i++) {
                int row = i + 8;
                sheet.createRow(row).setHeightInPoints(48);
                for (int col = 0; col < 9; col++) cell(sheet, row, col, "", bordered);
                if (i >= items.size()) continue;
                SalesDeliveryItem item = items.get(i);
                cell(sheet, row, 0, i + 1, center);
                String name = value(item.getProductName());
                String code = value(item.getProductCode());
                cell(sheet, row, 1, name.isBlank() ? code : name + (code.isBlank() || code.equals(name) ? "" : "\n" + code), bordered);
                cell(sheet, row, 2, item.getSpecification(), bordered);
                cell(sheet, row, 3, item.getUnit(), center);
                cell(sheet, row, 4, item.getQuantity(), quantity);
                cell(sheet, row, 5, item.getUnitPrice(), money);
                cell(sheet, row, 6, item.getAmount(), money);
                cell(sheet, row, 7, orderNo, bordered);
                cell(sheet, row, 8, item.getRemark(), bordered);
                // Excel 对换行单元格不会可靠地自动调整行高，预估长规格所需高度。
                int lines = 1;
                for (int col = 0; col < 9; col++) {
                    Cell current = sheet.getRow(row).getCell(col);
                    if (current.getCellType() == CellType.STRING) {
                        lines = Math.max(lines, wrappedLines(current.getStringCellValue(), widths[col] - 1));
                    }
                }
                sheet.getRow(row).setHeightInPoints(Math.min(409, Math.max(48, lines * 16 + 12)));
            }
            int row = 8 + Math.max(5, items.size());
            String[] labels = {"单据合计金额（原值）", "运费", "保价费", "其他费用"};
            Number[] amounts = {delivery.getTotalAmount(), delivery.getFreightAmount(), delivery.getInsuranceAmount(), delivery.getOtherCharges()};
            for (int i = 0; i < labels.length; i++, row++) {
                merged(sheet, row, 0, 5, labels[i], text, 26);
                merged(sheet, row, 6, 8, "", money, 26);
                cell(sheet, row, 6, amounts[i], money);
            }
            merged(sheet, row++, 0, 8, "备注：" + value(delivery.getRemark()), text, 40);
            merged(sheet, row++, 0, 8, "如上列貨品有不符问题，请在10天内通知。方便我司处理，过期恕不负责。", text, 32);
            merged(sheet, row, 0, 4, "送货单位经手人：________________", text, 40);
            merged(sheet, row, 5, 8, "收货单位经手人：________________", text, 40);
            sheet.createFreezePane(0, 8);
            sheet.setRepeatingRows(new CellRangeAddress(0, 7, -1, -1));
            workbook.setPrintArea(0, 0, 8, 0, row);
            sheet.setFitToPage(true);
            sheet.setHorizontallyCenter(true);
            PrintSetup setup = sheet.getPrintSetup();
            setup.setPaperSize(PrintSetup.A4_PAPERSIZE);
            setup.setLandscape(false);
            setup.setFitWidth((short) 1);
            setup.setFitHeight((short) 0);
            sheet.setMargin(PageMargin.LEFT, 15d / 25.4);
            sheet.setMargin(PageMargin.RIGHT, 15d / 25.4);
            sheet.setMargin(PageMargin.TOP, 12d / 25.4);
            sheet.setMargin(PageMargin.BOTTOM, 12d / 25.4);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static String value(String value) { return value == null ? "" : value; }

    private static int wrappedLines(String text, int width) {
        int lines = 0;
        for (String part : text.split("\n", -1)) {
            int units = part.codePoints().map(code -> code >= 0x2E80 ? 2 : 1).sum();
            lines += Math.max(1, (units + width - 1) / width);
        }
        return lines;
    }

    private static CellStyle style(Workbook workbook, int size, boolean bold, HorizontalAlignment align,
                                   boolean border, String numberFormat) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) size);
        font.setBold(bold);
        style.setFont(font);
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        if (border) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
        if (numberFormat != null) style.setDataFormat(workbook.createDataFormat().getFormat(numberFormat));
        return style;
    }

    private static void merged(Sheet sheet, int row, int firstCol, int lastCol, String value, CellStyle style, float height) {
        Row existing = sheet.getRow(row);
        if (existing == null) existing = sheet.createRow(row);
        existing.setHeightInPoints(height);
        for (int col = firstCol; col <= lastCol; col++) cell(sheet, row, col, "", style);
        cell(sheet, row, firstCol, value, style);
        sheet.addMergedRegion(new CellRangeAddress(row, row, firstCol, lastCol));
    }

    private static void cell(Sheet sheet, int rowIndex, int col, Object value, CellStyle style) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellStyle(style);
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(value == null ? "" : value.toString());
    }
}
