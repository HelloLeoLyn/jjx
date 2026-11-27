package com.jjx.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialMasterRequiredFieldsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MaterialMasterCatalog catalog = mock(MaterialMasterCatalog.class);
    private final InventoryMaterialMapper materials = mock(InventoryMaterialMapper.class);
    private final MaterialMasterService service = new MaterialMasterService(catalog, mock(MaterialMasterMapper.class), materials, null, null, null, null, json);

    private MaterialMasterDTO draft() {
        var dto = new MaterialMasterDTO(); dto.setCategoryId(22L);
        dto.setMaterialName("其他基材名称"); dto.setSupplierMaterialName("供应商料号ABC");
        return dto;
    }
    private void configure() throws Exception {
        var category = new MaterialMasterCatalog.Category(22L,"RAW_BASE_OTHER","其他基材",1L,3,"R","原材料/基材/其他基材",true,1,1);
        var schema = MaterialAttributeSchema.parse("{\"version\":1,\"meta_options\":[\"PU\",\"PP\"],\"meta_required\":false,\"default_unit\":\"PCS\",\"fields\":[]}",json);
        var template = new InventoryAttributeTemplate(); template.setTemplateId(5L);
        when(catalog.optionalConfiguration(22L)).thenReturn(new MaterialMasterCatalog.Configuration(category, List.of(category), template, schema));
        when(catalog.dictionaries()).thenReturn(Map.of("unit",List.of(new MaterialMasterCatalog.DictionaryOption("PCS","片"))));
    }
    @Test void legacyGradeRequiredSettingDoesNotBlockNewMaterialWithoutMetaOrForm() throws Exception {
        configure(); when(materials.selectRequiredFields("RAW_BASE_OTHER")).thenReturn(List.of("material_grade"));
        var material = service.prepare(draft()).material();
        assertEquals("其他基材名称",material.getMaterialName());
        assertEquals("供应商料号ABC",material.getSupplierMaterialName());
        assertTrue(material.getMaterialGrade()==null || material.getMaterialGrade().isBlank());
    }
    @Test void otherRequiredFieldsAndMaterialNameRemainEnforced() throws Exception {
        configure(); when(materials.selectRequiredFields("RAW_BASE_OTHER")).thenReturn(List.of("material_grade","color"));
        assertTrue(assertThrows(BusinessException.class,()->service.prepare(draft())).getMessage().contains("颜色不能为空"));
        var blank = draft(); blank.setMaterialName(" ");
        assertTrue(assertThrows(BusinessException.class,()->service.prepare(blank)).getMessage().contains("物料名称"));
    }
}
