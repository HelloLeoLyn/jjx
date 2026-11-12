package com.jjx.production.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.production.domain.dto.BomConsumptionPreviewRequest;
import com.jjx.production.domain.vo.BomConsumptionPreviewVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BOM 明细耗用只读试算（dev-20261112-007）。
 *
 * <p>真实计算器 + Mock 只读 mapper：不启动 Spring、不连业务库、不写任何表。</p>
 */
class BomConsumptionPreviewServiceTest {

    private final InventoryMaterialMapper materialMapper = mock(InventoryMaterialMapper.class);
    private final BomConsumptionPreviewService service = new BomConsumptionPreviewService(materialMapper);

    private static BomConsumptionPreviewRequest.Row row(String key, Long materialId, String unit,
            String baseQty, String moduleQty, String lossRate, String minIssueQty, String referenceRollLengthM) {
        BomConsumptionPreviewRequest.Row row = new BomConsumptionPreviewRequest.Row();
        row.setKey(key);
        row.setMaterialId(materialId);
        row.setUnit(unit);
        row.setBaseQty(decimal(baseQty));
        row.setModuleQty(decimal(moduleQty));
        row.setLossRate(decimal(lossRate));
        row.setMinIssueQty(decimal(minIssueQty));
        row.setReferenceRollLengthM(decimal(referenceRollLengthM));
        return row;
    }

    private static BomConsumptionPreviewRequest request(String outputQuantity, String outputUnit,
            BomConsumptionPreviewRequest.Row... rows) {
        BomConsumptionPreviewRequest request = new BomConsumptionPreviewRequest();
        request.setOutputQuantity(decimal(outputQuantity));
        request.setOutputUnit(outputUnit);
        request.setRows(new ArrayList<>(Arrays.asList(rows)));
        return request;
    }

    private static MaterialBuilder material(long materialId, String unit) {
        return new MaterialBuilder(materialId, unit);
    }

    /** 测试用物料档案；尺寸只通过档案给，不来自请求。 */
    private static final class MaterialBuilder {
        private final InventoryMaterial material = new InventoryMaterial();

        private MaterialBuilder(long materialId, String unit) {
            material.setMaterialId(materialId);
            material.setUnit(unit);
        }

        private MaterialBuilder widthMm(String widthMm) {
            material.setWidthMm(decimal(widthMm));
            return this;
        }

        private MaterialBuilder lengthMm(String lengthMm) {
            material.setLengthMm(decimal(lengthMm));
            return this;
        }

        private InventoryMaterial build() {
            return material;
        }
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual, "期望 " + expected + "，实际为 null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望 " + expected + "，实际 " + actual.toPlainString());
    }

    @Test
    @DisplayName("baseQty=1、moduleQty=6、Q=300、PCS：净耗 0.1667，总需求 50，精确值 50/1")
    void computesUnitAndTotalConsumptionForPieces() {
        BomConsumptionPreviewVO vo = service.preview(
                request("300", "PCS", row("r1", null, "PCS", "1", "6", null, null, null)));

        assertTrue(vo.isReadOnly(), "本接口恒为只读");
        assertEquals(1, vo.getRows().size());
        BomConsumptionPreviewVO.Row out = vo.getRows().get(0);
        assertTrue(out.isValid(), out.getMessage());
        assertEquals("r1", out.getKey());
        assertEquals("PCS", out.getUnit());
        assertEquals("0.1667", out.getUnitConsumption());
        assertEquals("50", out.getTotalConsumption());
        assertEquals("50/1", out.getExactConsumption(), "权威精确值原样输出，整数也保留 /1");
        assertNull(out.getMetres(), "件数单位不给参考米数");
        assertNull(out.getReferenceRollCount());
        assertNull(out.getArchiveUnit(), "无 materialId 不回填档案字段");
        assertNull(out.getWidthMm());
        assertNull(out.getLengthMm());
        assertFalse(out.isPitchSupported(), "非连续计量不得标记跳距可用");
        verifyNoInteractions(materialMapper);
    }

    @Test
    @DisplayName("lossRate=5：理论 52.5 → PCS 向上取整 53，精确值保留 105/2")
    void appliesLossAndRoundsStockUpForPieces() {
        BomConsumptionPreviewVO.Row out = service.preview(
                request("300", "PCS", row("r1", null, "PCS", "1", "6", "5", null, null))).getRows().get(0);

        assertTrue(out.isValid(), out.getMessage());
        assertEquals("0.1667", out.getUnitConsumption());
        assertEquals("53", out.getTotalConsumption());
        assertEquals("105/2", out.getExactConsumption(), "权威精确值不得被取整替代");
    }

    @Test
    @DisplayName("最低投料高于需求时取最低投料；最低投料超 4 位小数的行 invalid 且不影响其他行")
    void minimumIssueFloorsDemandAndOverPrecisionIsRowLevel() {
        BomConsumptionPreviewVO vo = service.preview(request("300", "PCS",
                row("floor", null, "PCS", "1", "6", null, "80", null),
                row("bad-min", null, "PCS", "1", "6", null, "1.23456", null)));

        BomConsumptionPreviewVO.Row floored = vo.getRows().get(0);
        assertTrue(floored.isValid(), floored.getMessage());
        assertEquals("80", floored.getTotalConsumption());
        assertEquals("80/1", floored.getExactConsumption());

        BomConsumptionPreviewVO.Row badMin = vo.getRows().get(1);
        assertFalse(badMin.isValid());
        assertTrue(badMin.getMessage().contains("minIssueQty"), badMin.getMessage());
        assertNull(badMin.getTotalConsumption(), "无效行不得把异常当 0");
        assertNull(badMin.getExactConsumption());
    }

    @Test
    @DisplayName("M2 + 档案宽 195/长 20000：总量非整 → 米数 4 位、参考卷长 20、卷数 ceil、可借助跳距")
    void derivesMetresAndRollsForSquareMetre() {
        when(materialMapper.selectBatchIds(any())).thenReturn(List.of(
                material(10L, "M2").widthMm("195").lengthMm("20000").build()));

        BomConsumptionPreviewVO.Row out = service.preview(
                request("100", null, row("r1", 10L, null, "1", "7", null, null, null))).getRows().get(0);

        assertTrue(out.isValid(), out.getMessage());
        assertEquals("M2", out.getUnit(), "行单位空时用档案单位兜底");
        assertEquals("M2", out.getArchiveUnit());
        assertDecimal("195", out.getWidthMm());
        assertDecimal("20000", out.getLengthMm());
        assertEquals("0.1429", out.getUnitConsumption());
        assertEquals("14.2857", out.getTotalConsumption());
        assertEquals("73.2600", out.getMetres());
        assertDecimal("20", out.getReferenceRollLengthM());
        assertEquals("4", out.getReferenceRollCount());
        assertTrue(out.isPitchSupported(), "M2 + 有效宽度 + PCS 产出可借助 004 跳距接口");
    }

    @Test
    @DisplayName("请求行单位 M 不被档案单位 PCS 覆盖：按 M 计算，档案单位只作展示")
    void requestUnitWinsOverArchiveUnit() {
        when(materialMapper.selectBatchIds(any())).thenReturn(List.of(
                material(11L, "PCS").widthMm("195").build()));

        BomConsumptionPreviewVO.Row out = service.preview(
                request("100", "PCS", row("r1", 11L, "M", "1", "1", null, null, null))).getRows().get(0);

        assertTrue(out.isValid(), out.getMessage());
        assertEquals("M", out.getUnit());
        assertEquals("PCS", out.getArchiveUnit());
        assertEquals("100.0000", out.getTotalConsumption());
        assertEquals("100.0000", out.getMetres());
        assertNull(out.getReferenceRollLengthM(), "档案没有长度时不猜参考卷长");
        assertNull(out.getReferenceRollCount());
        assertTrue(out.isPitchSupported());
    }

    @Test
    @DisplayName("产出单位不是严格 PCS（如 SET）时不标记跳距可用：不把多件套当每跳 PCS")
    void pitchSupportRequiresExactPcsOutputUnit() {
        when(materialMapper.selectBatchIds(any())).thenReturn(List.of(
                material(11L, "M").widthMm("195").build()));

        BomConsumptionPreviewVO.Row setOutput = service.preview(
                request("100", "SET", row("r1", 11L, "M", "1", "1", null, null, null))).getRows().get(0);
        assertTrue(setOutput.isValid(), setOutput.getMessage());
        assertFalse(setOutput.isPitchSupported(), "SET 产出不等于每跳 PCS");

        BomConsumptionPreviewVO.Row pcsOutput = service.preview(
                request("100", "个", row("r1", 11L, "M", "1", "1", null, null, null))).getRows().get(0);
        assertTrue(pcsOutput.isValid(), pcsOutput.getMessage());
        assertFalse(pcsOutput.isPitchSupported(),
                "产出单位须归一后严格等于 PCS；个/件等别称不再视为每跳 PCS（主会话勘误）");
    }

    @Test
    @DisplayName("未知 materialId 的行 invalid，同一请求其他行仍正常试算")
    void unknownMaterialIsIsolatedToItsRow() {
        when(materialMapper.selectBatchIds(any())).thenReturn(List.of(material(12L, "PCS").build()));

        BomConsumptionPreviewVO vo = service.preview(request("300", "PCS",
                row("ghost", 999L, "PCS", "1", "6", null, null, null),
                row("ok", 12L, null, "1", "6", null, null, null)));

        BomConsumptionPreviewVO.Row ghost = vo.getRows().get(0);
        assertFalse(ghost.isValid());
        assertTrue(ghost.getMessage().contains("999"), ghost.getMessage());

        BomConsumptionPreviewVO.Row ok = vo.getRows().get(1);
        assertTrue(ok.isValid(), ok.getMessage());
        assertEquals("PCS", ok.getUnit());
        assertEquals("50", ok.getTotalConsumption());
    }

    @Test
    @DisplayName("未知单位（BOX_UNKNOWN）该行 invalid，不扩展计算器取整策略")
    void unknownUnitIsRowLevel() {
        BomConsumptionPreviewVO.Row out = service.preview(
                request("300", "PCS", row("r1", null, "BOX_UNKNOWN", "1", "6", null, null, null)))
                .getRows().get(0);

        assertFalse(out.isValid());
        assertTrue(out.getMessage().contains("BOX_UNKNOWN"), out.getMessage());
        assertTrue(out.getMessage().contains("UNIT_POLICY_MISSING"), out.getMessage());
        assertNull(out.getTotalConsumption());
    }

    @Test
    @DisplayName("有 materialId 但档案无宽/长：米数、参考卷长、参考卷数均为 null，不猜系数")
    void noArchiveDimensionsMeansNoMetresGuess() {
        when(materialMapper.selectBatchIds(any())).thenReturn(List.of(material(13L, "M2").build()));

        BomConsumptionPreviewVO.Row out = service.preview(
                request("300", "PCS", row("r1", 13L, null, "1", "6", null, null, null))).getRows().get(0);

        assertTrue(out.isValid(), out.getMessage());
        assertEquals("M2", out.getUnit());
        assertEquals("50.0000", out.getTotalConsumption());
        assertNull(out.getMetres());
        assertNull(out.getReferenceRollLengthM());
        assertNull(out.getReferenceRollCount());
        assertFalse(out.isPitchSupported(), "无有效宽度不得标记跳距可用");
    }

    @Test
    @DisplayName("Q 5 位小数整请求拒绝；lossRate 3 位小数只让该行 invalid")
    void rejectsOverPrecisionAtTheRightLevel() {
        BusinessException quantity = assertThrows(BusinessException.class, () -> service.preview(
                request("300.12345", "PCS", row("r1", null, "PCS", "1", "6", null, null, null))));
        assertTrue(quantity.getMessage().contains("outputQuantity"), quantity.getMessage());

        BomConsumptionPreviewVO vo = service.preview(request("300", "PCS",
                row("bad-loss", null, "PCS", "1", "6", "5.125", null, null),
                row("ok", null, "PCS", "1", "6", "5", null, null)));
        assertFalse(vo.getRows().get(0).isValid());
        assertTrue(vo.getRows().get(0).getMessage().contains("lossRate"), vo.getRows().get(0).getMessage());
        assertTrue(vo.getRows().get(1).isValid(), vo.getRows().get(1).getMessage());
    }

    @Test
    @DisplayName("行数 501 / 重复 key / key 超 64 整请求拒绝；rows 空或 null 返回 readOnly 空结果")
    void enforcesRequestLevelRules() {
        List<BomConsumptionPreviewRequest.Row> tooMany = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            tooMany.add(row("k" + index, null, "PCS", "1", "6", null, null, null));
        }
        BomConsumptionPreviewRequest overLimit = request("300", "PCS");
        overLimit.setRows(tooMany);
        BusinessException rowsError = assertThrows(BusinessException.class, () -> service.preview(overLimit));
        assertTrue(rowsError.getMessage().contains("500"), rowsError.getMessage());

        assertThrows(BusinessException.class, () -> service.preview(request("300", "PCS",
                row("same", null, "PCS", "1", "6", null, null, null),
                row("same", null, "PCS", "1", "6", null, null, null))));

        assertThrows(BusinessException.class, () -> service.preview(request("300", "PCS",
                row("k".repeat(65), null, "PCS", "1", "6", null, null, null))));

        BomConsumptionPreviewVO empty = service.preview(request("300", "PCS"));
        assertTrue(empty.isReadOnly());
        assertNotNull(empty.getRows());
        assertTrue(empty.getRows().isEmpty());

        BomConsumptionPreviewRequest nullRows = request("300", "PCS");
        nullRows.setRows(null);
        BomConsumptionPreviewVO nullResult = service.preview(nullRows);
        assertTrue(nullResult.isReadOnly());
        assertTrue(nullResult.getRows().isEmpty());
        verifyNoInteractions(materialMapper);
    }

    @Test
    @DisplayName("响应 JSON 字段名与前端契约一致，不新增改名或嵌套包装")
    void serializesContractFieldNames() throws Exception {
        when(materialMapper.selectBatchIds(any())).thenReturn(List.of(
                material(10L, "M2").widthMm("195").lengthMm("20000").build()));
        BomConsumptionPreviewVO vo = service.preview(
                request("100", "PCS", row("r1", 10L, null, "1", "7", null, null, null)));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(mapper.writeValueAsString(vo));
        assertEquals(java.util.Set.of("readOnly", "rows"), fieldNames(root));
        assertTrue(root.get("readOnly").asBoolean());
        assertEquals(java.util.Set.of("key", "valid", "message", "unit", "unitConsumption", "totalConsumption",
                "exactConsumption", "metres", "referenceRollCount", "referenceRollLengthM", "archiveUnit",
                "widthMm", "lengthMm", "pitchSupported"), fieldNames(root.get("rows").get(0)));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    @DisplayName("请求 referenceRollLengthM=12.5 优先于档案 lengthMm：卷长 12.5、卷数 ceil(米数/12.5)")
    void explicitReferenceRollLengthWinsOverArchiveLength() {
        when(materialMapper.selectBatchIds(any())).thenReturn(List.of(
                material(10L, "M2").widthMm("195").lengthMm("20000").build()));

        BomConsumptionPreviewVO.Row out = service.preview(
                request("100", "PCS", row("r1", 10L, null, "1", "7", null, null, "12.5"))).getRows().get(0);

        assertTrue(out.isValid(), out.getMessage());
        assertEquals("73.2600", out.getMetres());
        assertDecimal("12.5", out.getReferenceRollLengthM());
        assertEquals("6", out.getReferenceRollCount());
    }
}
