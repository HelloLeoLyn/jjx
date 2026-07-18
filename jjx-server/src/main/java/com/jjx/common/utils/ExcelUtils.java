package com.jjx.common.utils;

import com.jjx.common.annotation.ExcelColumn;
import com.jjx.common.core.excel.ExcelColumnMeta;
import com.jjx.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Excel 通用工具类（静态方法版）
 *
 * @author example
 * @version 2.0
 */
public final class ExcelUtils {

    private static final Logger log = LoggerFactory.getLogger(ExcelUtils.class);

    // 私有构造器，防止实例化
    private ExcelUtils() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    // ==================== 常量 ====================
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String EMPTY_STRING = "";
    private static final int MAX_IMPORT_ROWS = 10000;

    // ==================== 1. 导入Excel ====================

    /**
     * 从 MultipartFile 导入 Excel
     *
     * @param file 上传的文件
     * @param clazz 目标 DTO 类型
     * @return 解析后的数据列表
     */
    public static <T> List<T> importExcel(MultipartFile file, Class<T> clazz) {
        validateFile(file);

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<ExcelColumnMeta> columnMetas = getColumnMetas(clazz);
            Map<Integer, String> columnMapping = buildColumnMapping(sheet, columnMetas);

            List<T> resultList = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            int totalRows = sheet.getPhysicalNumberOfRows();
            for (int rowNum = 1; rowNum < totalRows; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (isRowEmpty(row)) {
                    continue;
                }

                try {
                    T instance = parseRowToObject(row, columnMapping, clazz);
                    validateRequiredFields(instance, columnMetas);
                    resultList.add(instance);
                } catch (Exception e) {
                    errors.add(String.format("第%d行: %s", rowNum + 1, e.getMessage()));
                }
            }

            if (!errors.isEmpty()) {
                throw new BusinessException("导入失败:\n" + String.join("\n", errors));
            }

            log.info("导入成功，共 {} 条数据", resultList.size());
            return resultList;

        } catch (Exception e) {
            log.error("导入失败", e);
            throw new BusinessException("导入失败: " + e.getMessage());
        }
    }

    /**
     * 校验文件
     */
    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            throw new BusinessException("文件格式错误，仅支持 .xlsx 或 .xls");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException("文件大小不能超过10MB");
        }
    }

    /**
     * 校验必填字段
     */
    private static <T> void validateRequiredFields(T instance, List<ExcelColumnMeta> columnMetas)
            throws Exception {
        for (ExcelColumnMeta meta : columnMetas) {
            if (meta.isRequired()) {
                Field field = instance.getClass().getDeclaredField(meta.getFieldName());
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                    throw new Exception(meta.getHeaderName() + " 不能为空");
                }
            }
        }
    }

    /**
     * 解析行数据为对象
     */
    private static <T> T parseRowToObject(Row row, Map<Integer, String> columnMapping, Class<T> clazz)
            throws Exception {
        T instance = clazz.getDeclaredConstructor().newInstance();

        for (Map.Entry<Integer, String> entry : columnMapping.entrySet()) {
            int colIndex = entry.getKey();
            String fieldName = entry.getValue();
            Cell cell = row.getCell(colIndex);

            if (cell == null) {
                continue;
            }

            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = convertCellValue(cell, field.getType());
            field.set(instance, value);
        }

        return instance;
    }

    /**
     * 单元格值转换
     */
    private static Object convertCellValue(Cell cell, Class<?> targetType) {
        String cellValue = getCellStringValue(cell);

        if (targetType == String.class) {
            return cellValue;
        } else if (targetType == Integer.class || targetType == int.class) {
            try {
                return (int) Double.parseDouble(cellValue);
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (targetType == BigDecimal.class) {
            try {
                return new BigDecimal(cellValue);
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (targetType == Date.class) {
            try {
                return cell.getDateCellValue();
            } catch (Exception e) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                try {
                    return sdf.parse(cellValue);
                } catch (Exception ex) {
                    return null;
                }
            }
        }

        return cellValue;
    }

    /**
     * 获取单元格字符串值
     */
    private static String getCellStringValue(Cell cell) {
        if (cell == null) return EMPTY_STRING;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN);
                    return sdf.format(cell.getDateCellValue());
                }
                return new BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return EMPTY_STRING;
        }
    }

    /**
     * 判断行是否为空
     */
    private static boolean isRowEmpty(Row row) {
        if (row == null) return true;

        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !cell.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 构建列映射
     */
    private static Map<Integer, String> buildColumnMapping(Sheet sheet, List<ExcelColumnMeta> columnMetas) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new BusinessException("Excel表头为空");
        }

        Map<String, String> headerToField = new HashMap<>();
        for (ExcelColumnMeta meta : columnMetas) {
            headerToField.put(meta.getHeaderName(), meta.getFieldName());
        }

        Map<Integer, String> mapping = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String header = getCellStringValue(cell);
                if (headerToField.containsKey(header)) {
                    mapping.put(i, headerToField.get(header));
                }
            }
        }

        return mapping;
    }

    // ==================== 2. 下载模板 ====================

    /**
     * 下载导入模板
     *
     * @param response HttpServletResponse
     * @param clazz DTO 类型
     * @param templateName 模板名称
     */
    public static void downloadTemplate(HttpServletResponse response,
                                        Class<?> clazz,
                                        String templateName) {
        try {
            List<ExcelColumnMeta> columnMetas = getColumnMetas(clazz);

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet(templateName);

                // 创建表头
                writeTemplateHeader(sheet, columnMetas);

                // 添加示例数据
                addExampleData(sheet, columnMetas);

                // 添加说明行
                addInstructionRow(sheet, columnMetas);

                // 设置列宽
                autoSizeColumns(sheet, columnMetas.size());

                // 输出文件
                writeToResponse(workbook, response, templateName + "_模板");
            }

            log.info("模板下载成功: {}", templateName);
        } catch (Exception e) {
            log.error("模板下载失败", e);
            throw new BusinessException("模板下载失败: " + e.getMessage());
        }
    }

    /**
     * 写入模板表头
     */
    private static void writeTemplateHeader(Sheet sheet, List<ExcelColumnMeta> columnMetas) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());

        for (int i = 0; i < columnMetas.size(); i++) {
            Cell cell = headerRow.createCell(i);
            ExcelColumnMeta meta = columnMetas.get(i);
            String headerName = meta.isRequired() ? meta.getHeaderName() + "(*)" : meta.getHeaderName();
            cell.setCellValue(headerName);
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * 添加示例数据
     */
    private static void addExampleData(Sheet sheet, List<ExcelColumnMeta> columnMetas) {
        Row exampleRow = sheet.createRow(1);
        CellStyle exampleStyle = createExampleStyle(sheet.getWorkbook());

        for (int i = 0; i < columnMetas.size(); i++) {
            Cell cell = exampleRow.createCell(i);
            ExcelColumnMeta meta = columnMetas.get(i);
            String exampleValue = getExampleValue(meta);
            cell.setCellValue(exampleValue);
            cell.setCellStyle(exampleStyle);
        }
    }

    /**
     * 获取示例值
     */
    private static String getExampleValue(ExcelColumnMeta meta) {
        if (meta.getComment() != null && !meta.getComment().isEmpty()) {
            return "示例：" + meta.getComment();
        }

        switch (meta.getFieldName()) {
            case "materialCode":
                return "MTR-20240001";
            case "materialName":
                return "304不锈钢板";
            case "specification":
                return "厚度2mm×宽度1.5m";
            case "inquiryQuantity":
                return "1000";
            case "unitPrice":
                return "99.50";
            case "expectedDeliveryDate":
                return "2024-12-31";
            case "supplier":
                return "XX科技有限公司";
            default:
                return "示例数据";
        }
    }

    /**
     * 添加说明行
     */
    private static void addInstructionRow(Sheet sheet, List<ExcelColumnMeta> columnMetas) {
        Row tipRow = sheet.createRow(3);
        Cell tipCell = tipRow.createCell(0);
        tipCell.setCellValue("说明：标(*)列为必填项，第2行为示例数据请删除后填写实际数据");

        CellStyle tipStyle = sheet.getWorkbook().createCellStyle();
        Font tipFont = sheet.getWorkbook().createFont();
        tipFont.setColor(IndexedColors.RED.getIndex());
        tipFont.setItalic(true);
        tipStyle.setFont(tipFont);
        tipCell.setCellStyle(tipStyle);

        // 合并单元格作为说明区域
        if (columnMetas.size() > 1) {
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, columnMetas.size() - 1));
        }
    }

    // ==================== 3. 导出数据 ====================

    /**
     * 导出数据到 Excel
     *
     * @param response HttpServletResponse
     * @param dataList 数据列表
     * @param clazz DTO 类型
     * @param fileName 文件名
     */
    public static <T> void export(HttpServletResponse response,
                                  List<T> dataList,
                                  Class<T> clazz,
                                  String fileName) {
        if (dataList == null || dataList.isEmpty()) {
            throw new BusinessException("导出数据为空");
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(fileName);
            List<ExcelColumnMeta> columnMetas = getColumnMetas(clazz);

            // 写入表头
            writeExportHeader(sheet, columnMetas);

            // 写入数据
            writeExportData(sheet, dataList, columnMetas);

            // 设置列宽
            autoSizeColumns(sheet, columnMetas.size());

            // 输出文件
            writeToResponse(workbook, response, fileName);

            log.info("导出成功: {}, 共{}条", fileName, dataList.size());
        } catch (Exception e) {
            log.error("导出失败", e);
            throw new BusinessException("导出失败: " + e.getMessage());
        }
    }

    /**
     * 写入导出表头
     */
    private static void writeExportHeader(Sheet sheet, List<ExcelColumnMeta> columnMetas) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());

        for (int i = 0; i < columnMetas.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columnMetas.get(i).getHeaderName());
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * 写入导出数据
     */
    private static <T> void writeExportData(Sheet sheet, List<T> dataList, List<ExcelColumnMeta> columnMetas)
            throws Exception {
        CellStyle dataStyle = createDataStyle(sheet.getWorkbook());

        for (int rowIndex = 0; rowIndex < dataList.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            T item = dataList.get(rowIndex);

            for (int colIndex = 0; colIndex < columnMetas.size(); colIndex++) {
                Cell cell = row.createCell(colIndex);
                ExcelColumnMeta meta = columnMetas.get(colIndex);

                Field field = item.getClass().getDeclaredField(meta.getFieldName());
                field.setAccessible(true);
                Object value = field.get(item);

                setCellValue(cell, value);
                cell.setCellStyle(dataStyle);
            }
        }
    }

    /**
     * 设置单元格值
     */
    private static void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue(EMPTY_STRING);
        } else if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Date) {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN);
            cell.setCellValue(sdf.format((Date) value));
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取列元数据
     */
    private static List<ExcelColumnMeta> getColumnMetas(Class<?> clazz) {
        List<ExcelColumnMeta> metas = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation != null) {
                String headerName = annotation.value().isEmpty() ? field.getName() : annotation.value();
                ExcelColumnMeta meta = new ExcelColumnMeta(
                        headerName,
                        field.getName(),
                        annotation.order(),
                        annotation.required(),
                        annotation.comment(),
                        field.getType()
                );
                metas.add(meta);
            }
        }

        metas.sort(Comparator.comparingInt(ExcelColumnMeta::getOrder));
        return metas;
    }

    /**
     * 创建表头样式
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);

        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    /**
     * 创建示例数据样式
     */
    private static CellStyle createExampleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        Font font = workbook.createFont();
        font.setColor(IndexedColors.BLUE.getIndex());
        font.setItalic(true);
        style.setFont(font);

        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    /**
     * 创建数据样式
     */
    private static CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * 自动调整列宽
     */
    private static void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            if (width < 3000) {
                sheet.setColumnWidth(i, 3000);
            } else if (width > 15000) {
                sheet.setColumnWidth(i, 15000);
            }
        }
    }

    /**
     * 输出到响应流
     */
    private static void writeToResponse(Workbook workbook, HttpServletResponse response, String fileName)
            throws Exception {
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
                .replaceAll("\\+", "%20");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName + ".xlsx");

        try (OutputStream os = response.getOutputStream()) {
            workbook.write(os);
            os.flush();
        }
    }
}