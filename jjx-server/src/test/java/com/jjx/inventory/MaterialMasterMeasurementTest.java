package com.jjx.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.*;
import com.jjx.inventory.measurement.MeasurementPolicy;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialMasterMeasurementTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MaterialMasterCatalog catalog = mock(MaterialMasterCatalog.class);
    private final InventoryMaterialMapper materials = mock(InventoryMaterialMapper.class);
    private final MaterialMasterService service = new MaterialMasterService(catalog, mock(MaterialMasterMapper.class), materials, null, null, null, null, json);
    private final MeasurementPolicy roll = new MeasurementPolicy("ROLL", "㎡", MeasurementPolicy.Mode.ROLL_AREA, null);
    private MaterialMasterDTO draft() {
        var dto = new MaterialMasterDTO(); dto.setCategoryId(2L); dto.setMaterialName("卷材测试");
        return dto;
    }
    private void configure(MeasurementPolicy policy) throws Exception {
        var template = new InventoryAttributeTemplate(); template.setTemplateId(71L);
        var category = new MaterialMasterCatalog.Category(2L,"RAW_BASE_PC","PC",1L,3,"R","原材料/基材/PC",true,1,1);
        String schema = "{\"version\":1,\"meta_options\":[],\"default_unit\":\"PCS\",\"fields\":[],\"measurement_defaults\":" + json.writeValueAsString(policy) + "}";
        when(catalog.optionalConfiguration(2L)).thenReturn(new MaterialMasterCatalog.Configuration(category, List.of(category), template, MaterialAttributeSchema.parse(schema,json)));
        when(catalog.dictionaries()).thenReturn(Map.of("unit",List.of(new MaterialMasterCatalog.DictionaryOption("PCS","片"),new MaterialMasterCatalog.DictionaryOption("㎡","平方米"),new MaterialMasterCatalog.DictionaryOption("ROLL","卷"),new MaterialMasterCatalog.DictionaryOption("M","米"))));
    }
    @Test void newMaterialUsesCategoryPolicyAndRollAreaLedger() throws Exception {
        configure(roll);
        var material = service.prepare(draft()).material();
        assertEquals("㎡",material.getUnit());
        assertEquals(roll,MeasurementPolicy.parse(material.getMeasurementPolicyJson()));
    }
    @Test void newRollWeightMaterialUsesPriceLedgerForRemainingRollCost() throws Exception {
        configure(roll);
        var dto = draft();
        dto.setMeasurementPolicy(new MeasurementPolicy("ROLL","KG",MeasurementPolicy.Mode.MEASURED_WEIGHT,null));
        when(catalog.dictionaries()).thenReturn(Map.of("unit", List.of(new MaterialMasterCatalog.DictionaryOption("KG","公斤"))));
        assertEquals("KG", service.prepare(dto).material().getUnit());
        // 型号/名称预览与保存共用库存单位选择，隐藏的旧默认PCS不能渲染进新档案。
        var configuration = catalog.optionalConfiguration(2L);
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(configuration.schema());
        node.set("name_pattern", json.readTree("{\"fields\":[\"unit\"],\"separator\":\"\",\"empty\":\"skip\"}"));
        when(catalog.optionalConfiguration(2L)).thenReturn(new MaterialMasterCatalog.Configuration(configuration.category(), configuration.path(), configuration.template(), MaterialAttributeSchema.parse(node.toString(), json)));
        dto.setUnit("PCS");
        assertEquals("KG", service.renderNaming(2L, dto));
    }

    @Test void explicitNullInCopiedLegacyMaterialDoesNotApplyNewCategoryDefault() throws Exception {
        configure(roll);
        var dto = json.readValue("{\"categoryId\":2,\"materialName\":\"旧档案副本\",\"unit\":\"PCS\",\"measurementPolicy\":null}",MaterialMasterDTO.class);
        assertTrue(dto.isMeasurementPolicySupplied());
        var material = service.prepare(dto).material();
        assertNull(material.getMeasurementPolicyJson());
        assertEquals("PCS",material.getUnit());
    }
    @Test void editKeepsUnconfiguredHistoryDespiteNewCategoryDefault() throws Exception {
        configure(roll);
        var old = new InventoryMaterial(); old.setMaterialId(12L); old.setUnit("PCS");
        when(materials.selectById(12L)).thenReturn(old);
        var dto = draft(); dto.setMaterialId(12L);
        var material = service.prepare(dto).material();
        assertNull(material.getMeasurementPolicyJson()); assertEquals("PCS",material.getUnit());
    }
    @Test void editRetainsSavedPolicyInsteadOfChangedCategoryDefault() throws Exception {
        configure(roll);
        var old = new InventoryMaterial(); old.setMaterialId(12L); old.setUnit("PCS");
        var sheet = new MeasurementPolicy("PCS","㎡",MeasurementPolicy.Mode.PIECE_AREA,null);
        old.setMeasurementPolicyJson(sheet.toJson()); when(materials.selectById(12L)).thenReturn(old);
        var dto = draft(); dto.setMaterialId(12L);
        assertEquals(sheet,MeasurementPolicy.parse(service.prepare(dto).material().getMeasurementPolicyJson()));
        dto.setMeasurementPolicy(roll);
        assertThrows(BusinessException.class,()->service.prepare(dto));
    }
    @Test void explicitPolicyOverridesCategoryAndRejectsIncompatibleMode() throws Exception {
        configure(roll); var dto = draft();
        dto.setMeasurementPolicy(new MeasurementPolicy("PCS","PCS",MeasurementPolicy.Mode.SAME,null));
        assertEquals("PCS",service.prepare(dto).material().getUnit());
        dto.setMeasurementPolicy(new MeasurementPolicy("PCS","KG",MeasurementPolicy.Mode.PIECE_AREA,null));
        assertThrows(BusinessException.class,()->service.prepare(dto));
    }
    @Test void importAcceptsExplicitPolicyAndOldTemplateWithoutGuessing() throws Exception {
        when(catalog.dictionaries()).thenReturn(Map.of("unit", List.of(new MaterialMasterCatalog.DictionaryOption("ROLL","卷"), new MaterialMasterCatalog.DictionaryOption("㎡","平方米"), new MaterialMasterCatalog.DictionaryOption("PCS","片"))));
        var spy = spy(service);
        var excel = new MaterialMasterExcelService(catalog, spy, materials, json, mock(com.jjx.inventory.classify.MaterialClassifyService.class));
        for (boolean explicit : List.of(true, false)) {
            byte[] bytes;
            try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var out = new java.io.ByteArrayOutputStream()) {
                var sheet = book.createSheet(); var head = sheet.createRow(0); var row = sheet.createRow(1);
                String[] headers = explicit ? new String[]{"物料名称","宽(mm)","长(mm)","厚(mm)","尺寸单位","实物单位","采购计价单位","换算方式"}
                        : new String[]{"物料名称","宽(mm)","长(mm)","厚(mm)","尺寸单位","单位"};
                String[] values = explicit ? new String[]{"卷材","1220","100000","","mm","ROLL","㎡","ROLL_AREA"}
                        : new String[]{"旧档案","285","315","","mm","PCS"};
                for (int i=0;i<headers.length;i++) { head.createCell(i).setCellValue(headers[i]); row.createCell(i).setCellValue(values[i]); }
                book.write(out); bytes=out.toByteArray();
            }
            var result=excel.importExcel(new org.springframework.mock.web.MockMultipartFile("file","test.xlsx","application/octet-stream",bytes),null,true,"test",false);
            assertEquals(List.of(),result.errors()); assertEquals(1,result.successCount());
            var captured = org.mockito.ArgumentCaptor.forClass(MaterialMasterDTO.class);
            verify(spy).prepare(captured.capture());
            if (explicit) assertEquals(roll,captured.getValue().getMeasurementPolicy());
            else assertNull(captured.getValue().getMeasurementPolicy());
            clearInvocations(spy);
        }
    }
}
