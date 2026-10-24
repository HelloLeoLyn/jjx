package com.jjx.inventory.master;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
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
    public record ImportResult(boolean preview, int successCount, List<String> errors) { }
    private static final List<String> COMMON_HEADERS = List.of("原名称", "原规格", "材质", "形态", "尺寸(mm)", "颜色", "型号", "品牌", "单位", "供应商", "是否客供专用", "是否样品专用", "别名", "业务备注", "物料ID（更新时填写）");

    public byte[] template(Long categoryId) {
        var configuration = catalog.configuration(categoryId);
        var dictionaries = catalog.dictionaries();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("物料录入");
            Row metadata = sheet.createRow(0);
            put(metadata, 0, "品类编码"); put(metadata, 1, configuration.category().categoryCode());
            put(metadata, 2, "模板版本"); put(metadata, 3, String.valueOf(configuration.schema().version()));
            put(metadata, 4, "多选用顿号分隔；空白表示未确认；尺寸单位mm");
            List<String> headers = headers(configuration.schema());
            Row header = sheet.createRow(1);
            for (int i = 0; i < headers.size(); i++) { put(header, i, headers.get(i)); sheet.setColumnWidth(i, 22 * 256); }
            sheet.createFreezePane(0, 2);
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            for (int i = 0; i < headers.size(); i++) sheet.setDefaultColumnStyle(i, textStyle);
            Sheet options = workbook.createSheet("候选");
            Map<Integer, List<String>> lists = new LinkedHashMap<>();
            lists.put(2, dictionaries.get("material_meta").stream().filter(o -> configuration.schema().metaOptions().contains(o.code())).map(MaterialMasterCatalog.DictionaryOption::label).toList());
            lists.put(3, labels(dictionaries, "form")); lists.put(5, labels(dictionaries, "color"));
            lists.put(7, labels(dictionaries, "brand")); lists.put(8, labels(dictionaries, "unit"));
            lists.put(10, List.of("是", "否")); lists.put(11, List.of("是", "否"));
            for (int i = 0; i < configuration.schema().fields().size(); i++) {
                var field = configuration.schema().fields().get(i);
                if (field.type().equals("enum")) lists.put(COMMON_HEADERS.size() + i, field.options().stream().map(MaterialAttributeSchema.Option::label).toList());
                if (field.type().equals("boolean")) lists.put(COMMON_HEADERS.size() + i, List.of("是", "否"));
            }
            int optionColumn = 0;
            for (var entry : lists.entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                for (int i = 0; i < entry.getValue().size(); i++) { Row row = options.getRow(i); if (row == null) row = options.createRow(i); put(row, optionColumn, entry.getValue().get(i)); }
                var name = workbook.createName(); name.setNameName("material_options_" + optionColumn);
                String column = org.apache.poi.ss.util.CellReference.convertNumToColString(optionColumn);
                name.setRefersToFormula("'候选'!$" + column + "$1:$" + column + "$" + entry.getValue().size());
                var validation = sheet.getDataValidationHelper().createValidation(sheet.getDataValidationHelper().createFormulaListConstraint(name.getNameName()), new CellRangeAddressList(2, 2001, entry.getKey(), entry.getKey()));
                validation.setShowErrorBox(true); validation.createErrorBox("选项错误", "请选择模板允许的值"); sheet.addValidationData(validation);
                optionColumn++;
            }
            workbook.setSheetHidden(1, true);
            return bytes(workbook);
        } catch (Exception e) { throw new BusinessException("生成导入模板失败：" + e.getMessage()); }
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importExcel(MultipartFile file, Long categoryId, boolean preview, String operator, boolean allowUpdate) {
        if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new BusinessException("请选择不超过5MB的Excel文件");
        var configuration = catalog.configuration(categoryId);
        var dictionaries = catalog.dictionaries();
        List<MaterialMasterDTO> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Long> existingKeys = new HashMap<>();
        Set<String> fileKeys = new HashSet<>();
        Set<Long> updateIds = new HashSet<>();
        for (InventoryMaterial material : materials.selectList(new LambdaQueryWrapper<InventoryMaterial>()
                .eq(InventoryMaterial::getCategoryId, categoryId).isNotNull(InventoryMaterial::getOriginalName))) existingKeys.put(identity(material), material.getMaterialId());
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (!"品类编码".equals(cell(sheet.getRow(0), 0)) || !configuration.category().categoryCode().equals(cell(sheet.getRow(0), 1))
                    || !String.valueOf(configuration.schema().version()).equals(cell(sheet.getRow(0), 3))) throw new BusinessException("文件品类或模板版本不匹配，请重新下载该品类模板");
            List<String> headers = headers(configuration.schema());
            for (int i = 0; i < headers.size(); i++) if (!headers.get(i).equals(cell(sheet.getRow(1), i))) throw new BusinessException("模板列不匹配：" + headers.get(i));
            if (sheet.getLastRowNum() > 2001) throw new BusinessException("每次最多导入2000行");
            for (int index = 2; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                try {
                    for (int extra = headers.size(); extra < row.getLastCellNum(); extra++) if (!cell(row,extra).isBlank()) throw new BusinessException("包含模板之外的列");
                    if (blank(row, headers.size())) continue;
                    MaterialMasterDTO dto = new MaterialMasterDTO(); dto.setCategoryId(categoryId);
                    String id = cell(row, 14);
                    if (!id.isBlank()) {
                        if (!allowUpdate) throw new BusinessException("更新或迁移现有物料还需要物料编辑权限");
                        try { dto.setMaterialId(Long.parseLong(id)); } catch (NumberFormatException e) { throw new BusinessException("物料ID必须为整数"); }
                        if (!updateIds.add(dto.getMaterialId())) throw new BusinessException("同一文件不能重复更新同一物料ID");
                    }
                    dto.setOriginalName(cell(row, 0)); dto.setOriginalSpecification(cell(row, 1));
                    dto.setMaterialMeta(dictionaryCode(dictionaries, "material_meta", cell(row, 2)));
                    dto.setMaterialForm(dictionaryCode(dictionaries, "form", cell(row, 3)));
                    dto.setSize(cell(row, 4)); dto.setColor(dictionaryCode(dictionaries, "color", cell(row, 5)));
                    dto.setModel(cell(row, 6)); dto.setBrand(dictionaryCode(dictionaries, "brand", cell(row, 7)));
                    dto.setUnit(dictionaryCode(dictionaries, "unit", cell(row, 8))); dto.setSupplierName(cell(row, 9));
                    dto.setIsCustomerSupplied(bool(cell(row, 10))); dto.setIsSample(bool(cell(row, 11)));
                    dto.setSearchKeywords(cell(row, 12)); dto.setRemark(cell(row, 13));
                    Map<String, Object> attributes = new LinkedHashMap<>();
                    for (int i = 0; i < configuration.schema().fields().size(); i++) {
                        var field = configuration.schema().fields().get(i);
                        String value = cell(row, COMMON_HEADERS.size() + i);
                        if (value.isBlank()) continue;
                        Object parsed = switch (field.type()) {
                            case "boolean" -> bool(value);
                            case "integer", "decimal" -> { try { yield new BigDecimal(value); } catch (NumberFormatException e) { throw new BusinessException(field.label() + "必须为数字"); } }
                            case "enum" -> optionCode(field, value);
                            case "multienum" -> Arrays.stream(value.split("[、,，]")).map(String::trim).map(v -> optionCode(field, v)).toList();
                            default -> value;
                        };
                        attributes.put(field.key(), parsed);
                    }
                    dto.setAttributeJson(attributes);
                    InventoryMaterial prepared = service.prepare(dto).material();
                    String key = identity(prepared);
                    if (!fileKeys.add(key) || existingKeys.containsKey(key) && !Objects.equals(existingKeys.get(key), dto.getMaterialId())) throw new BusinessException("与文件其他行或库内新模型物料重复");
                    rows.add(dto);
                } catch (BusinessException e) { errors.add("第" + (index + 1) + "行：" + e.getMessage()); }
                if (errors.size() >= 50) { errors.add("错误已达50条，请修正后重新预检"); break; }
            }
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("读取Excel失败：" + e.getMessage()); }
        if (!errors.isEmpty()) return new ImportResult(preview, 0, errors);
        if (rows.isEmpty()) throw new BusinessException("文件没有可导入物料");
        if (!preview) for (MaterialMasterDTO dto : rows) service.save(dto, operator);
        return new ImportResult(preview, rows.size(), List.of());
    }

    public byte[] export(Long categoryId) {
        List<InventoryMaterial> list = materials.selectList(new LambdaQueryWrapper<InventoryMaterial>()
                .isNotNull(InventoryMaterial::getOriginalName).eq(categoryId != null, InventoryMaterial::getCategoryId, categoryId)
                .orderByAsc(InventoryMaterial::getMaterialId));
        return exportRows(list);
    }

    public byte[] exportQuery(com.jjx.inventory.dto.query.MaterialQueryDTO query) {
        return exportRows(service.selectForExport(query));
    }

    private byte[] exportRows(List<InventoryMaterial> list) {
        List<String> headers = List.of("序号", "物料ID", "物料编码", "原名称", "原规格", "一级分类", "二级分类", "三级分类", "材质", "形态", "尺寸(mm)", "颜色", "型号", "品牌", "供应商", "基本单位", "是否客供专用", "是否样品专用", "属性JSON", "当前名称", "生成规格");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("物料主数据"); Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) { put(header, i, headers.get(i)); sheet.setColumnWidth(i, 20 * 256); }
            int index = 0;
            Map<Long, MaterialMasterCatalog.Configuration> configs = new HashMap<>();
            Map<String, List<MaterialMasterCatalog.DictionaryOption>> dictionaries = catalog.dictionaries();
            for (InventoryMaterial material : list) {
                var configuration = configs.computeIfAbsent(material.getCategoryId(), catalog::configuration);
                var path = configuration.path();
                Row row = sheet.createRow(++index);
                String[] values = {String.valueOf(index), material.getMaterialId().toString(), material.getMaterialCode(), material.getOriginalName(), material.getOriginalSpecification(),
                        path.get(0).categoryName(), path.size() > 1 ? path.get(1).categoryName() : "", path.size() > 2 ? path.get(2).categoryName() : "",
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

    private static List<String> headers(MaterialAttributeSchema schema) {
        List<String> result = new ArrayList<>(COMMON_HEADERS);
        schema.fields().forEach(f -> result.add(f.label() + (f.unit() == null ? "" : "(" + f.unit() + ")")));
        return result;
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
    private static List<String> labels(Map<String, List<MaterialMasterCatalog.DictionaryOption>> dictionaries, String dict) { return dictionaries.get(dict).stream().map(MaterialMasterCatalog.DictionaryOption::label).toList(); }
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
        try { return objectMapper.writeValueAsString(Arrays.asList(material.getCategoryId(), material.getOriginalName(), material.getOriginalSpecification(), material.getMaterialMeta(), material.getMaterialForm(), material.getModel(), material.getColor(), material.getBrand(), material.getSize(), material.getUnit(), service.readAttributes(material.getAttributeJson()))); }
        catch (Exception e) { throw new BusinessException("物料重复性检查失败"); }
    }
}
