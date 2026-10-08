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

    static final String TEMPLATE = "/templates/sales/qr026-delivery.xlsx";
    private static final int ROWS_PER_SHEET = 5;

    static byte[] buildWorkbook(SalesDeliveryVO delivery, String orderNo,
                                String companyName, String companyAddress) throws IOException {
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
                    cell(sheet, 2, 0, companyName);
                    cell(sheet, 4, 6, "地址:" + value(companyAddress));
                    cell(sheet, 5, 1, delivery.getCustomerName());
                    cell(sheet, 5, 2, "");
                    cell(sheet, 5, 7, "N O:" + value(delivery.getDeliveryNo()));
                    cell(sheet, 6, 0, "Attm:" + value(delivery.getContactPerson()) + "  " + value(delivery.getContactPhone()));
                    cell(sheet, 6, 7, "DATE:" + (delivery.getDeliveryDate() == null ? "" :
                            new SimpleDateFormat("yyyy-MM-dd").format(delivery.getDeliveryDate())));
                    cell(sheet, 14, 2, delivery.getDeliveryPersonName());
                    for (int line = 0; line < ROWS_PER_SHEET; line++) {
                        int row = 8 + line;
                        for (int col = 0; col < 9; col++) cell(sheet, row, col, null);
                        int index = page * ROWS_PER_SHEET + line;
                        if (index >= items.size()) continue;
                        SalesDeliveryItem item = items.get(index);
                        cell(sheet, row, 0, index + 1);
                        // 客户料号缺省用产品名称，完整产品编码/品名另存发货数据表。
                        cell(sheet, row, 1, materialNo(item));
                        cell(sheet, row, 2, item.getSpecification());
                        cell(sheet, row, 3, item.getUnit());
                        cell(sheet, row, 4, item.getQuantity());
                        cell(sheet, row, 5, item.getUnitPrice());
                        cell(sheet, row, 6, item.getAmount());
                        cell(sheet, row, 7, value(item.getOrderNo()).isBlank() ? orderNo : item.getOrderNo());
                        cell(sheet, row, 8, item.getRemark());
                    }
                    // 原模板纸型为 WPS 自定义编号；统一可识别的 A4，沿用已确认的页边距。
                    sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
                    sheet.getPrintSetup().setLandscape(false);
                    sheet.getPrintSetup().setFitWidth((short) 1);
                    sheet.getPrintSetup().setFitHeight((short) 1);
                    sheet.setFitToPage(true);
                    sheet.setMargin(PageMargin.LEFT, 15d / 25.4);
                    sheet.setMargin(PageMargin.RIGHT, 15d / 25.4);
                    sheet.setMargin(PageMargin.TOP, 12d / 25.4);
                    sheet.setMargin(PageMargin.BOTTOM, 12d / 25.4);
                    workbook.setPrintArea(page, 0, 8, 0, 15);
                }
                addSourceData(workbook, delivery, items, orderNo, companyName, companyAddress);
                workbook.setActiveSheet(0);
                workbook.write(output);
                return output.toByteArray();
            }
        }
    }

    /** 模板外的业务数据单独保存，避免改变主表单布局或丢失费用、收货地址。 */
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
        String[] headers = {"序号", "料号", "品名", "规格", "单位", "数量", "单价", "金额", "订单号码", "备注", "客户料号"};
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

    /** 只更新内容，不替换模板已有的单元格样式。文本不会被解释为公式。 */
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
