package com.jjx.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.dto.ProductWorkSpecDTO;
import com.jjx.product.domain.vo.*;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.mapper.ProductWorkSpecMapper;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.*;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductWorkSpecProjectMaterialsTest {
    final ObjectMapper json = new ObjectMapper();
    ProductWorkSpecService service;
    ProductWorkSpecMapper mapper;
    SysOperLogMapper logs;
    EngineeringBomVO bom;
    AtomicReference<String> stored;
    MockedStatic<SecurityUtils> actor;

    @BeforeEach void setup() {
        mapper = mock(ProductWorkSpecMapper.class); logs = mock(SysOperLogMapper.class);
        stored = new AtomicReference<>("{\"preparedById\":7,\"preparedByName\":\"工程甲\"}");
        when(mapper.exists(1L)).thenReturn(1);
        when(mapper.lock(1L)).thenAnswer(call -> Map.of("work_spec_json", stored.get()));
        when(mapper.read(1L)).thenAnswer(call -> stored.get());
        when(mapper.write(eq(1L), anyString())).thenAnswer(call -> { stored.set(call.getArgument(1)); return 1; });
        bom = new EngineeringBomVO(); bom.setItems(new ArrayList<>(List.of(bomRow(11L, "M1", "面材"), bomRow(12L, "M2", "底胶"))));
        var product = new ProductVo(); product.setProductId(1L);
        var full = new ProductFullVO(); full.setProduct(product); full.setBom(bom);
        var products = mock(IProductService.class); when(products.getFullProductDetail(1L)).thenReturn(full);
        service = new ProductWorkSpecService(mapper, mock(SysAttachmentMapper.class), mock(SysAttachmentRefMapper.class), products, json, mock(ProductWorkSpecChangeService.class), logs);
        actor = mockStatic(SecurityUtils.class);
        actor.when(SecurityUtils::getUserId).thenReturn(7L);
        actor.when(SecurityUtils::getUsername).thenReturn("eng7");
        actor.when(SecurityUtils::getDisplayName).thenReturn("工程甲");
    }
    @AfterEach void close() { actor.close(); }
    EngineeringBomItem bomRow(long id, String code, String name) {
        var row = new EngineeringBomItem(); row.setMaterialId(id); row.setMaterialCode(code); row.setMaterialName(name); row.setQuantity(BigDecimal.ONE); row.setUnit("PCS"); return row;
    }
    ProductWorkSpecDTO dto(String code, String name) {
        var dto = new ProductWorkSpecDTO(); dto.setRevision(service.get(1L).path("revision").asText());
        var row = new ProductWorkSpecDTO.ProjectMaterial(); row.setMaterialCode(code); row.setMaterialName(name); row.setQuantity(BigDecimal.ONE); row.setUnit("PCS"); row.setSortOrder(0);
        row.setSourceBom(service.materialBomSource(json.valueToTree(bom.getItems().get(0))));
        dto.setProjectMaterials(List.of(row)); return dto;
    }
    @Test void subsetNeedsAcknowledgementAndLogsNamesActorAndTimeWithoutChangingBom() throws Exception {
        String bomBefore = json.writeValueAsString(bom);
        var dto = dto("M1", "面材"); String before = stored.get();
        assertThrows(BusinessException.class, () -> service.save(1L, dto));
        assertEquals(before, stored.get());
        dto.setProjectMaterialsAcknowledgement(service.inspectProjectMaterials(1L, json.valueToTree(dto.getProjectMaterials())).path("token").asText());
        ObjectNode saved = service.save(1L, dto);
        assertEquals("面材", saved.path("projectMaterials").get(0).path("materialName").asText());
        assertEquals(bomBefore, json.writeValueAsString(bom));
        var events = org.mockito.ArgumentCaptor.forClass(SysOperLog.class); verify(logs, atLeast(2)).insert(events.capture());
        assertTrue(events.getAllValues().stream().anyMatch(log -> log.getDetail().contains("底胶") && log.getAction().contains("提醒确认")));
        assertTrue(events.getAllValues().stream().anyMatch(log -> log.getDetail().contains("面材") && log.getAction().equals("保存草稿")));
        assertTrue(events.getAllValues().stream().allMatch(log -> "工程甲".equals(log.getRealName()) && log.getCreateTime() != null));
    }
    @Test void outsideBomAndRemovedRowsAreHardErrorsWithRowNumber() {
        var dto = dto("OUTSIDE", "外部材料");
        String message = assertThrows(BusinessException.class, () -> service.save(1L, dto)).getMessage();
        assertTrue(message.contains("第 1 行")); assertTrue(message.contains("BOM"));
        verify(mapper, never()).write(anyLong(), anyString());
    }
    @Test void bomChangesNeverOverwriteMaterialsAndInvalidatesAcknowledgement() {
        var dto = dto("M1", "面材");
        String token = service.inspectProjectMaterials(1L, json.valueToTree(dto.getProjectMaterials())).path("token").asText();
        bom.getItems().get(0).setQuantity(BigDecimal.TEN);
        var check = service.inspectProjectMaterials(1L, json.valueToTree(dto.getProjectMaterials()));
        assertNotEquals(token, check.path("token").asText());
        assertTrue(check.path("warnings").toString().contains("BOM 内容已变化"));
        dto.setProjectMaterialsAcknowledgement(token);
        assertThrows(BusinessException.class, () -> service.save(1L, dto));
        assertEquals(BigDecimal.ONE, dto.getProjectMaterials().get(0).getQuantity());
    }
    @Test void explicitEmptyListIsPreservedAndLegacyNullDoesNotBackfill() {
        assertFalse(service.get(1L).has("projectMaterials"));
        var dto = dto("M1", "面材"); dto.setProjectMaterials(List.of());
        dto.setProjectMaterialsAcknowledgement(service.inspectProjectMaterials(1L, json.valueToTree(dto.getProjectMaterials())).path("token").asText());
        assertTrue(service.save(1L, dto).path("projectMaterials").isEmpty());
        assertTrue(service.get(1L).path("projectMaterials").isArray());
    }
    @Test void materialEditsInvalidateApprovalAndAppearInVersionDiff() throws Exception {
        var dto = dto("M1", "面材"); dto.setProjectMaterialsAcknowledgement(service.inspectProjectMaterials(1L, json.valueToTree(dto.getProjectMaterials())).path("token").asText());
        var first = service.save(1L, dto);
        ObjectNode raw = (ObjectNode) json.readTree(stored.get());
        raw.put("confirmedSource", first.path("sourceRevision").asText()); raw.put("confirmedBy", "工程甲");
        stored.set(raw.toString()); assertTrue(service.get(1L).path("approved").asBoolean());
        dto.setRevision(service.get(1L).path("revision").asText()); dto.getProjectMaterials().get(0).setSpecification("新规格");
        dto.setProjectMaterialsAcknowledgement(service.inspectProjectMaterials(1L, json.valueToTree(dto.getProjectMaterials())).path("token").asText());
        assertFalse(service.save(1L, dto).path("approved").asBoolean());
        var versions = mock(ProductWorkSpecVersionService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(versions, "json", json);
        List<?> diff = ReflectionTestUtils.invokeMethod(versions, "compareSnapshots", raw, json.readTree(stored.get()));
        assertTrue(diff.toString().contains("项目材料"));
        assertEquals("新规格", service.get(1L).path("projectMaterials").get(0).path("specification").asText());
    }

    @Test void paramsOverrideIsLiteralAndFreezeDoesNotAlterDraft() throws Exception {
        var row = json.readTree("{\"widthMm\":100,\"lengthMm\":200,\"moduleQty\":2,\"split\":\"3\",\"params\":\"\"}");
        assertEquals("100*200mm跳3=2PCS", service.projectMaterialParams(row));
        ((ObjectNode) row).put("params", "人工规格\n<样张> & 2模");
        assertEquals("人工规格\n<样张> & 2模", service.projectMaterialParams(row));
        ObjectNode spec = json.createObjectNode(); spec.putArray("projectMaterials").add(row);
        ObjectNode frozen = spec.deepCopy(); service.freezeProjectMaterials(frozen);
        ((ObjectNode) row).put("params", ""); ((ObjectNode) row).put("widthMm", 50);
        assertEquals("人工规格\n<样张> & 2模", frozen.path("projectMaterials").get(0).path("params").asText());
        assertEquals("50*200mm跳3=2PCS", service.projectMaterialParams(row));
        var dto = dto("M1", "面材"); dto.getProjectMaterials().get(0).setParams("x".repeat(501));
        assertTrue(service.checkProjectMaterialsInput(1L,dto).path("errors").toString().contains("500"));
    }
    @Test void confirmationSurvivesJsonKeyOrderAndPrintDoesNotChangeDraft() throws Exception {
        var dto = dto("M1", "面材");
        dto.setProjectMaterialsAcknowledgement(service.checkProjectMaterialsInput(1L, dto).path("token").asText());
        service.save(1L, dto);
        ObjectNode state = (ObjectNode) json.readTree(stored.get());
        var material = (ObjectNode) state.path("projectMaterials").get(0);
        var reversed = json.createObjectNode(); var keys = new ArrayList<String>(); material.fieldNames().forEachRemaining(keys::add);
        Collections.reverse(keys); keys.forEach(key -> reversed.set(key, material.get(key)));
        state.withArray("projectMaterials").set(0, reversed); stored.set(state.toString());
        var current = service.get(1L); String before = stored.get();
        String token = current.path("projectMaterialsCheck").path("token").asText();
        assertEquals(dto.getProjectMaterialsAcknowledgement(), token);
        service.acknowledgeProjectMaterials(1L, current.path("revision").asText(), token, "打印");
        assertEquals(before, stored.get());
        service.acknowledgeProjectMaterials(1L, current.path("revision").asText(), token, "定版");
        service.requireProjectMaterialsAcknowledged(1L, (ObjectNode)json.readTree(stored.get()), "定版");
        bom.setItems(List.of());
        assertThrows(BusinessException.class, () -> service.requireProjectMaterialsAcknowledged(1L, (ObjectNode)json.readTree(stored.get()), "定版"));
    }
}
