package com.jjx.inventory.master;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import com.jjx.inventory.classify.MaterialClassifyService;
import com.jjx.inventory.classify.MaterialClassSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;

/** 同一模板生成录入列与导入校验；整表预检后才进入写入。 */
@Service
@RequiredArgsConstructor
public class MaterialMasterExcelService {
    private final MaterialMasterCatalog catalog;
    private final MaterialMasterService service;
    private final InventoryMaterialMapper materials;
    private final ObjectMapper objectMapper;
    private final MaterialClassifyService classifyService;
    public record ImportResult(boolean preview, int successCount, List<String> errors) { }
    private static final List<String> COMMON_HEADERS = List.of("物料名称", "规格", "材质", "形态", "宽(mm)", "长(mm)", "厚(mm)", "尺寸单位", "颜色", "型号", "品牌", "单位", "供应商", "是否客供专用", "是否样品专用", "别名", "业务备注", "物料ID（更新时填写）", "标准单价", "采购周期(天)", "安全库存", "最高库存", "再订货点", "保质期(天)", "预警提前天数", "批次控制");

    /** 品类列逐行选择；属性列为启用品类模板字段的并集，不绑定单个品类。 */
    public byte[] template(Long ignoredCategoryId) {
        List<MaterialMasterCatalog.Category> categories = catalog.categories().stream().filter(MaterialMasterCatalog.Category::selectable).toList();
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        columns.add("品类"); columns.addAll(COMMON_HEADERS);
        for (var category : categories) catalog.configuration(category.categoryId()).schema().fields().stream().filter(f -> !f.key().equals("thickness_mm")).forEach(f -> columns.add(fieldHeader(f)));
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("物料录入");
            put(sheet.createRow(0), 0, "物料导入1A：品类填编码；空品类可选自动归类，未命中进入未归类；按每行品类校验属性");
            Row header = sheet.createRow(1); int column = 0;
            for (String label : columns) { put(header, column, label); sheet.setColumnWidth(column++, 22 * 256); }
            sheet.createFreezePane(0, 2);
            CellStyle textStyle = workbook.createCellStyle(); textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            for (int i = 0; i < columns.size(); i++) sheet.setDefaultColumnStyle(i, textStyle);
            Sheet options = workbook.createSheet("品类说明");
            String[] labels = {"品类编码", "品类名称", "必填属性列", "允许材质编码"};
            options.createRow(0);
            // 第一行是说明表头；每行列出一个可选品类及其模板约束。
            Row optionHeader = options.getRow(0);
            for (int i = 0; i < labels.length; i++) put(optionHeader, i, labels[i]);
            int index = 0;
            for (var category : categories) {
                var config = catalog.configuration(category.categoryId()); Row row = options.createRow(++index);
                put(row, 0, category.categoryCode()); put(row, 1, category.pathName());
                put(row, 2, String.join("、", config.schema().fields().stream().filter(MaterialAttributeSchema.Field::required).map(MaterialMasterExcelService::fieldHeader).toList()));
                put(row, 3, String.join("、", config.schema().metaOptions()));
            }
            if (!categories.isEmpty()) {
                var name = workbook.createName(); name.setNameName("material_categories");
                name.setRefersToFormula("'品类说明'!$A$2:$A$" + (categories.size() + 1));
                var validation = sheet.getDataValidationHelper().createValidation(sheet.getDataValidationHelper().createFormulaListConstraint(name.getNameName()), new CellRangeAddressList(2, 2001, 0, 0));
                // 允许空白以进入未归类队列，实际有效性仍由服务器逐行校验。
                validation.setEmptyCellAllowed(true); validation.setShowErrorBox(true); sheet.addValidationData(validation);
            }
            return bytes(workbook);
        } catch (Exception e) { throw new BusinessException("生成导入模板失败：" + e.getMessage()); }
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importExcel(MultipartFile file, Long categoryId, boolean preview, String operator, boolean allowUpdate) {
        return importExcel(file, categoryId, preview, operator, allowUpdate, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importExcel(MultipartFile file, Long legacyCategoryId, boolean preview, String operator, boolean allowUpdate, boolean autoClassify) {
        if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new BusinessException("请选择不超过5MB的Excel文件");
        var dictionaries = catalog.dictionaries();
        List<MaterialMasterDTO> rows = new ArrayList<>(); List<MaterialClassSource> sources = new ArrayList<>();
        List<String> errors = new ArrayList<>(); Map<String, Long> existingKeys = new HashMap<>();
        Set<String> fileKeys = new HashSet<>(); Set<Long> updateIds = new HashSet<>();
        for (InventoryMaterial material : materials.selectList(new LambdaQueryWrapper<InventoryMaterial>()))
            existingKeys.put(identity(material), material.getMaterialId());
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean oldCategoryTemplate = "品类编码".equals(cell(sheet.getRow(0), 0));
            boolean unified = cell(sheet.getRow(0), 0).startsWith("物料导入1A");
            int headerIndex = oldCategoryTemplate || unified ? 1 : 0;
            Long fallbackCategoryId = legacyCategoryId;
            if (oldCategoryTemplate) {
                var config = classifyService.categoryByCode(cell(sheet.getRow(0), 1));
                if (legacyCategoryId != null && !legacyCategoryId.equals(config.category().categoryId())) throw new BusinessException("文件品类与指定品类不匹配");
                if (!String.valueOf(config.schema().version()).equals(cell(sheet.getRow(0), 3))) throw new BusinessException("模板版本不匹配，请重新下载导入模板");
                fallbackCategoryId = config.category().categoryId();
            }
            Row header = sheet.getRow(headerIndex);
            if (header == null) throw new BusinessException("缺【物料名称】列");
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                String label = headerAlias(cell(header, i));
                if (!label.isBlank() && columns.putIfAbsent(label, i) != null) throw new BusinessException("重复列：" + label);
            }
            for (String label : List.of("宽(mm)", "长(mm)", "厚(mm)", "尺寸单位")) {
                if (!columns.containsKey(label)) throw new BusinessException("缺【" + label + "】列，请重新下载导入模板");
            }
            if (sheet.getLastRowNum() - headerIndex > 2000) throw new BusinessException("每次最多导入2000行");
            for (int index = headerIndex + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                try {
                    if (blank(row, Math.max(header.getLastCellNum(), row.getLastCellNum()))) continue;
                    for (int i = 0; i < row.getLastCellNum(); i++) if (!cell(row, i).isBlank() && cell(header, i).isBlank()) throw new BusinessException("第" + (i + 1) + "列缺表头");
                    MaterialMasterDTO dto = new MaterialMasterDTO();
                    dto.setMaterialName(requiredColumn(row, columns, "物料名称"));
                    // Excel规格只供本次自动归类匹配；保存规格统一由模板生成。
                    String inputSpecification = value(row, columns, "规格");
                    String category = value(row, columns, "品类");
                    MaterialClassSource source = MaterialClassSource.IMPORT;
                    if (!category.isBlank()) dto.setCategoryId(resolveCategory(category));
                    else if (fallbackCategoryId != null) dto.setCategoryId(fallbackCategoryId);
                    else if (autoClassify) {
                        var match = classifyService.match(dto.getMaterialName(), inputSpecification);
                        if (match != null) { dto.setCategoryId(match.categoryId()); source = MaterialClassSource.RULE; }
                    }
                    String id = value(row, columns, "物料ID（更新时填写）");
                    if (!id.isBlank()) {
                        if (!allowUpdate) throw new BusinessException("更新或迁移现有物料还需要物料编辑权限");
                        try { dto.setMaterialId(Long.parseLong(id)); } catch (NumberFormatException e) { throw new BusinessException("物料ID必须为整数"); }
                        if (dto.getMaterialId() <= 0 || !updateIds.add(dto.getMaterialId())) throw new BusinessException("物料ID须为正整数且不能重复");
                        var current = materials.selectById(dto.getMaterialId());
                        if (current != null && Boolean.TRUE.equals(current.getClassConfirmed())) throw new BusinessException("人工确认物料请在编辑入口修改，导入不能覆盖");
                    }
                    dto.setMaterialMeta(dictionaryCode(dictionaries, "material_meta", value(row, columns, "材质")));
                    dto.setMaterialForm(dictionaryCode(dictionaries, "form", value(row, columns, "形态")));
                    dto.setWidthMm(decimal(value(row, columns, "宽(mm)"), "宽(mm)"));
                    dto.setLengthMm(decimal(value(row, columns, "长(mm)"), "长(mm)"));
                    dto.setThicknessMm(decimal(value(row, columns, "厚(mm)"), "厚(mm)"));
                    dto.setSizeUnit(value(row, columns, "尺寸单位"));
                    dto.setColor(dictionaryCode(dictionaries, "color", value(row, columns, "颜色")));
                    dto.setModel(value(row, columns, "型号")); dto.setBrand(dictionaryCode(dictionaries, "brand", value(row, columns, "品牌")));
                    dto.setUnit(dictionaryCode(dictionaries, "unit", value(row, columns, "单位"))); dto.setSupplierName(value(row, columns, "供应商"));
                    dto.setIsCustomerSupplied(bool(value(row, columns, "是否客供专用"))); dto.setIsSample(bool(value(row, columns, "是否样品专用")));
                    dto.setSearchKeywords(value(row, columns, "别名")); dto.setRemark(value(row, columns, "业务备注"));
                    dto.setStandardPrice(decimal(value(row, columns, "标准单价"), "标准单价"));
                    dto.setSafeStock(decimal(value(row, columns, "安全库存"), "安全库存"));
                    dto.setMaxStock(decimal(value(row, columns, "最高库存"), "最高库存"));
                    dto.setReorderPoint(decimal(value(row, columns, "再订货点"), "再订货点"));
                    dto.setLeadTime(integer(value(row, columns, "采购周期(天)"), "采购周期(天)"));
                    dto.setShelfLife(integer(value(row, columns, "保质期(天)"), "保质期(天)"));
                    dto.setExpiryAlertDays(integer(value(row, columns, "预警提前天数"), "预警提前天数"));
                    String batchControl = value(row, columns, "批次控制");
                    if (!batchControl.isBlank()) dto.setBatchControl(bool(batchControl));
                    Map<String, Object> attributes = new LinkedHashMap<>(); Set<String> allowedHeaders = new HashSet<>(COMMON_HEADERS); allowedHeaders.add("品类");
                    if (dto.getCategoryId() != null) {
                        var schema = catalog.configuration(dto.getCategoryId()).schema();
                        if (dto.getMaterialMeta() != null && !schema.metaOptions().contains(dto.getMaterialMeta())) throw new BusinessException("【材质】越界：" + value(row, columns, "材质"));
                        for (var field : schema.fields()) {
                            if (field.key().equals("thickness_mm")) continue;
                            String label = fieldHeader(field); allowedHeaders.add(label);
                            String raw = value(row, columns, label);
                            if (!raw.isBlank()) attributes.put(field.key(), parseField(field, raw));
                        }
                        // 模板默认值/显示条件与保存入口保持一致；报错仍明确缺哪一列。
                        Map<String, Object> effective = new LinkedHashMap<>(attributes);
                        for (var field : schema.fields()) if (!effective.containsKey(field.key()) && field.defaultValue() != null && !field.defaultValue().isNull())
                            effective.put(field.key(), objectMapper.convertValue(field.defaultValue(), Object.class));
                        for (var field : schema.fields()) if (field.required() && MaterialAttributeSchema.visible(field, effective) && !effective.containsKey(field.key()))
                            throw new BusinessException("缺【" + fieldHeader(field) + "】列或值");
                        if (schema.metaRequired() && dto.getMaterialMeta() == null && schema.defaultMeta() == null && schema.metaOptions().size() != 1)
                            throw new BusinessException("缺【材质】列或值");
                    }
                    for (String column : columns.keySet()) if (!allowedHeaders.contains(column) && !value(row, columns, column).isBlank())
                        throw new BusinessException("该品类不支持列：" + column);
                    dto.setAttributeJson(attributes);
                    InventoryMaterial prepared = service.prepare(dto).material(); String key = identity(prepared);
                    if (!fileKeys.add(key) || existingKeys.containsKey(key) && !Objects.equals(existingKeys.get(key), dto.getMaterialId())) throw new BusinessException("与文件其他行或库内物料重复");
                    rows.add(dto); sources.add(source);
                } catch (BusinessException e) { errors.add("第" + (index + 1) + "行：" + e.getMessage()); }
            }
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("读取Excel失败：" + e.getMessage()); }
        if (!errors.isEmpty()) return new ImportResult(preview, 0, List.copyOf(errors));
        if (rows.isEmpty()) throw new BusinessException("文件没有可导入物料");
        if (!preview) for (int i = 0; i < rows.size(); i++) service.save(rows.get(i), operator, sources.get(i));
        return new ImportResult(preview, rows.size(), List.of());
    }

    private Long resolveCategory(String value) {
        var matches = catalog.categories().stream().filter(c -> c.selectable() && (c.categoryCode().equals(value) || c.categoryName().equals(value) || c.pathName().equals(value))).toList();
        if (matches.size() != 1) throw new BusinessException("【品类】不存在、已停用或名称不唯一：" + value);
        return matches.get(0).categoryId();
    }
    private static String fieldHeader(MaterialAttributeSchema.Field field) { return field.label() + (field.unit() == null ? "" : "(" + field.unit() + ")"); }
    private static String headerAlias(String value) {
        return switch (value) {
            case "名称", "原名称", "材料" -> "物料名称";
            case "原规格" -> "规格";
            case "品类编码" -> "品类";
            case "尺寸" -> "尺寸(mm)";
            case "备注" -> "业务备注";
            default -> value;
        };
    }
    private static String value(Row row, Map<String, Integer> columns, String label) { return columns.containsKey(label) ? cell(row, columns.get(label)) : ""; }
    private static String requiredColumn(Row row, Map<String, Integer> columns, String label) {
        String value = value(row, columns, label);
        if (value.isBlank()) throw new BusinessException("缺【" + label + "】列或值");
        return value;
    }
    private static BigDecimal decimal(String value, String label) {
        if (value.isBlank()) return null;
        try { return new BigDecimal(value); }
        catch (NumberFormatException e) { throw new BusinessException("【" + label + "】必须为数字"); }
    }
    private static Integer integer(String value, String label) {
        BigDecimal number = decimal(value, label);
        if (number == null) return null;
        try { return number.intValueExact(); }
        catch (ArithmeticException e) { throw new BusinessException("【" + label + "】必须为有效整数"); }
    }

    /** 旧列表导入端点继续返回可下载的逐行错误结构。 */
    public static com.jjx.inventory.dto.vo.MaterialImportResultVO legacyResult(ImportResult result) {
        var response = new com.jjx.inventory.dto.vo.MaterialImportResultVO();
        response.setSuccessCount(result.successCount());
        for (String error : result.errors()) {
            var row = java.util.regex.Pattern.compile("^第(\\d+)行：").matcher(error);
            int index = row.find() ? Integer.parseInt(row.group(1)) : 0;
            var column = java.util.regex.Pattern.compile("【([^】]+)】").matcher(error);
            response.addFail(index, null, column.find() ? column.group(1) : null, error);
        }
        return response;
    }

    private static Object parseField(MaterialAttributeSchema.Field field, String value) {
        return switch (field.type()) {
            case "boolean" -> bool(value);
            case "integer", "decimal" -> { try { yield new BigDecimal(value); } catch (NumberFormatException e) { throw new BusinessException("【" + fieldHeader(field) + "】必须为数字"); } }
            case "enum" -> optionCode(field, value);
            case "multienum" -> Arrays.stream(value.split("[、,，]")).map(String::trim).map(v -> optionCode(field, v)).toList();
            default -> value;
        };
    }

    public byte[] export(Long categoryId) {
        List<InventoryMaterial> list = materials.selectList(new LambdaQueryWrapper<InventoryMaterial>()
                .eq(categoryId != null, InventoryMaterial::getCategoryId, categoryId)
                .orderByAsc(InventoryMaterial::getMaterialId));
        return exportRows(list);
    }

    public byte[] exportQuery(com.jjx.inventory.dto.query.MaterialQueryDTO query) {
        return exportRows(service.selectForExport(query));
    }

    private byte[] exportRows(List<InventoryMaterial> list) {
        List<String> headers = List.of("序号", "物料ID", "物料编码", "一级分类", "二级分类", "三级分类", "材质", "形态", "尺寸(mm)", "颜色", "型号", "品牌", "供应商", "基本单位", "是否客供专用", "是否样品专用", "属性JSON", "当前名称", "生成规格");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("物料主数据"); Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) { put(header, i, headers.get(i)); sheet.setColumnWidth(i, 20 * 256); }
            int index = 0;
            Map<Long, MaterialMasterCatalog.Configuration> configs = new HashMap<>();
            Map<String, List<MaterialMasterCatalog.DictionaryOption>> dictionaries = catalog.dictionaries();
            for (InventoryMaterial material : list) {
                var configuration = material.getCategoryId() == null ? null : configs.computeIfAbsent(material.getCategoryId(), catalog::configuration);
                var path = configuration == null ? List.<MaterialMasterCatalog.Category>of() : configuration.path();
                Row row = sheet.createRow(++index);
                String[] values = {String.valueOf(index), material.getMaterialId().toString(), material.getMaterialCode(),
                        path.isEmpty() ? "" : path.get(0).categoryName(), path.size() > 1 ? path.get(1).categoryName() : "", path.size() > 2 ? path.get(2).categoryName() : "",
                        dictionaryLabel(dictionaries, "material_meta", material.getMaterialMeta()), dictionaryLabel(dictionaries, "form", material.getMaterialForm()), material.getSize(),
                        dictionaryLabel(dictionaries, "color", material.getColor()), material.getModel(), dictionaryLabel(dictionaries, "brand", material.getBrand()), material.getSupplierName(), material.getUnit(),
                        Boolean.TRUE.equals(material.getIsCustomerSupplied()) ? "是" : "否", Boolean.TRUE.equals(material.getIsSample()) ? "是" : "否", material.getAttributeJson(), material.getMaterialName(), material.getSpecification()};
                for (int i = 0; i < values.length; i++) put(row, i, values[i]);
            }
            sheet.createFreezePane(0, 1);
            return bytes(workbook);
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("导出失败：" + e.getMessage()); }
    }

    private static String optionCode(MaterialAttributeSchema.Field field, String value) {
        return field.options().stream().filter(o -> o.code().equals(value) || o.label().equals(value)).map(MaterialAttributeSchema.Option::code).findFirst().orElseThrow(() -> new BusinessException(field.label() + "不在允许选项内：" + value));
    }
    private static String dictionaryCode(Map<String, List<MaterialMasterCatalog.DictionaryOption>> dictionaries, String dict, String value) {
        if (value.isBlank()) return null;
        List<String> matches = dictionaries.getOrDefault(dict, List.of()).stream().filter(o -> o.code().equals(value) || o.label().equals(value)).map(MaterialMasterCatalog.DictionaryOption::code).distinct().toList();
        if (matches.size() != 1) throw new BusinessException("字典值不存在或名称不唯一：" + value);
        return matches.get(0);
    }
    private static String dictionaryLabel(Map<String, List<MaterialMasterCatalog.DictionaryOption>> dictionaries, String dict, String value) {
        return dictionaries.getOrDefault(dict, List.of()).stream().filter(o -> o.code().equals(value)).map(MaterialMasterCatalog.DictionaryOption::label).findFirst().orElse(value);
    }
    private static boolean bool(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) { case "", "否", "false", "0" -> false; case "是", "true", "1" -> true; default -> throw new BusinessException("是否字段只能填写是或否"); };
    }
    private static String cell(Row row, int column) {
        if (row == null || row.getCell(column) == null) return "";
        Cell cell = row.getCell(column);
        if (cell.getCellType() == CellType.FORMULA) throw new BusinessException("导入单元格不允许使用公式");
        return new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
    }
    private static boolean blank(Row row, int count) { for (int i = 0; i < count; i++) if (!cell(row, i).isBlank()) return false; return true; }
    private static void put(Row row, int column, String value) { row.createCell(column).setCellValue(value == null ? "" : value); }
    private static byte[] bytes(Workbook workbook) throws Exception { ByteArrayOutputStream out = new ByteArrayOutputStream(); workbook.write(out); return out.toByteArray(); }
    private String identity(InventoryMaterial material) {
        try { return objectMapper.writeValueAsString(Arrays.asList(material.getCategoryId(), material.getMaterialName(), material.getMaterialMeta(), material.getMaterialForm(), material.getModel(), material.getColor(), material.getBrand(), material.getSize(), material.getUnit(), service.readAttributes(material.getAttributeJson()))); }
        catch (Exception e) { throw new BusinessException("物料重复性检查失败"); }
    }
}
