package com.jjx.production.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.RequirementPreviewRequest;
import com.jjx.production.domain.model.ManufacturingDemandSnapshot;
import com.jjx.production.domain.vo.RequirementLineVO;
import com.jjx.production.domain.vo.RequirementSummaryVO;
import com.jjx.production.mapper.ManufacturingSupplySnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * N01/N02/N03/N06 与跨方案/反例测试：真实 ServiceImpl + ProviderImpl，Mapper 为只读替身。
 */
class ManufacturingRequirementNettingTest {

    private static final LocalDate DEMAND_DATE = LocalDate.of(2026, 11, 11);

    private ManufacturingSupplySnapshotMapper mapper;
    private ManufacturingRequirementServiceImpl service;
    private final Map<Long, ManufacturingDemandSnapshot> boms = new HashMap<>();
    private final Map<Long, List<ManufacturingDemandSnapshot.Line>> lines = new HashMap<>();
    private final Map<Long, ManufacturingSupplySnapshotMapper.MaterialRow> materials = new HashMap<>();
    private final Map<Long, ManufacturingSupplySnapshotMapper.StockRow> stocks = new HashMap<>();
    private final Map<Long, BigDecimal> purchaseSupply = new HashMap<>();
    private final Map<Long, BigDecimal> productionSupply = new HashMap<>();

    @BeforeEach
    void setUp() {
        mapper = mock(ManufacturingSupplySnapshotMapper.class);
        service = new ManufacturingRequirementServiceImpl(mapper,
                new ManufacturingSupplySnapshotProviderImpl(mapper));

        ManufacturingSupplySnapshotMapper.ProductRow product = new ManufacturingSupplySnapshotMapper.ProductRow();
        product.setProductId(1L);
        product.setProductCode("P1");
        product.setProductName("产品");
        product.setUnit("PCS");
        product.setCurrentBomId(10L);
        when(mapper.selectProduct(1L)).thenReturn(product);

        when(mapper.selectBom(anyLong())).thenAnswer(inv -> boms.get(inv.getArgument(0)));
        when(mapper.selectBomLines(anyLong())).thenAnswer(inv ->
                lines.getOrDefault(inv.getArgument(0), List.of()));
        when(mapper.selectMaterial(anyLong())).thenAnswer(inv -> materials.get(inv.getArgument(0)));
        when(mapper.selectMaterialStock(anyLong())).thenAnswer(inv ->
                stocks.getOrDefault(inv.getArgument(0), stock("0", "0")));
        when(mapper.selectPurchaseSupply(anyLong(), any(LocalDate.class), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> purchaseSupply.getOrDefault(inv.getArgument(0), BigDecimal.ZERO));
        when(mapper.selectMaterialProductionSupply(anyLong(), any(LocalDate.class), anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> productionSupply.getOrDefault(inv.getArgument(0), BigDecimal.ZERO));

        boms.put(10L, bom(10L, 1L, "PRODUCT", 1L, 3, "B10"));
    }

    @Test
    void stockAndReservationGiveAvailable140AndGap160() {
        line(10L, 200L, "PCS", "1", "1", "0", "buy", null);
        material(200L, "PCS", "0");
        stock(200L, "200", "60");

        RequirementLineVO line = line(preview("300"), 200L);

        assertEquals(0, line.getAvailableForDemand().compareTo(new BigDecimal("140")));
        assertEquals("160/1", line.getGapQtyExact());
    }

    @Test
    void onTimeUnallocatedPurchaseSupplies60AndGapBecomes100() {
        line(10L, 200L, "PCS", "1", "1", "0", "buy", null);
        material(200L, "PCS", "0");
        stock(200L, "200", "60");
        purchaseSupply.put(200L, new BigDecimal("60"));

        RequirementLineVO line = line(preview("300"), 200L);

        assertEquals("100/1", line.getGapQtyExact());
        assertEquals(0, line.getPurchaseSupply().compareTo(new BigDecimal("60")));
    }

    @Test
    void onTimeUnallocatedProductionSupplies60AndGapBecomes100() {
        line(10L, 200L, "PCS", "1", "1", "0", "buy", null);
        material(200L, "PCS", "0");
        stock(200L, "200", "60");
        productionSupply.put(200L, new BigDecimal("60"));

        RequirementLineVO line = line(preview("300"), 200L);

        assertEquals("100/1", line.getGapQtyExact());
        assertEquals(0, line.getProductionSupply().compareTo(new BigDecimal("60")));
    }

    @Test
    void twoBranchesShareStock140AndTotalGapIs60() {
        line(10L, 200L, "PCS", "1", "100", "0", "buy", null);
        line(10L, 200L, "PCS", "1", "100", "0", "buy", null);
        material(200L, "PCS", "0");
        stock(200L, "140", "0");

        RequirementLineVO line = line(preview("1"), 200L);

        assertEquals("60/1", line.getGapQtyExact());
        assertEquals(2, line.getSources().size());
        assertEquals("0/1", line.getSources().get(0).getGapQtyExact());
        assertEquals("60/1", line.getSources().get(1).getGapQtyExact());
        assertEquals(0, line.getAllocatedStock().compareTo(new BigDecimal("140")));
    }

    @Test
    void sameMaterialAtDifferentDepthsIsAggregatedBeforeNetting() {
        // 产品 -> 200(make B20)，产品 -> 400(make B40) -> 600(make B60) -> 200(buy)
        line(10L, 200L, "PCS", "1", "100", "0", "make", 20L);
        line(10L, 400L, "PCS", "1", "1", "0", "make", 40L);
        material(200L, "PCS", "0");
        material(400L, "PCS", "0");
        material(600L, "PCS", "0");
        stock(200L, "140", "0");

        boms.put(20L, bom(20L, null, "MATERIAL", 200L, 3, "B20"));
        lines.put(20L, List.of(line(201L, 20L, 500L, "PCS", "1", "10", "0", "buy", null)));
        boms.put(40L, bom(40L, null, "MATERIAL", 400L, 3, "B40"));
        lines.put(40L, List.of(line(401L, 40L, 600L, "PCS", "1", "1", "0", "make", 60L)));
        boms.put(60L, bom(60L, null, "MATERIAL", 600L, 3, "B60"));
        lines.put(60L, List.of(line(601L, 60L, 200L, "PCS", "1", "100", "0", "buy", null)));
        material(500L, "PCS", "0");

        RequirementLineVO line = line(preview("1"), 200L);

        assertEquals("60/1", line.getGapQtyExact());
        assertEquals(2, line.getSources().size());
        assertEquals(0, line.getAllocatedStock().compareTo(new BigDecimal("140")));
    }

    @Test
    void sameMaterialWithTwoSchemesExpandsEachGapByItsOwnSubBom() {
        line(10L, 200L, "PCS", "1", "100", "0", "make", 20L);
        line(10L, 200L, "PCS", "1", "100", "0", "make", 21L);
        material(200L, "PCS", "0");
        material(300L, "PCS", "0");
        material(400L, "PCS", "0");
        stock(200L, "60", "0");

        boms.put(20L, bom(20L, null, "MATERIAL", 200L, 3, "B20"));
        lines.put(20L, List.of(line(201L, 20L, 300L, "PCS", "1", "1", "0", "buy", null)));
        boms.put(21L, bom(21L, null, "MATERIAL", 200L, 3, "B21"));
        lines.put(21L, List.of(line(211L, 21L, 400L, "PCS", "1", "1", "0", "buy", null)));

        RequirementSummaryVO summary = preview("1");

        assertEquals("40/1", line(summary, 300L).getGapQtyExact());
        assertEquals("100/1", line(summary, 400L).getGapQtyExact());
        RequirementLineVO shared = line(summary, 200L);
        assertEquals("140/1", shared.getGapQtyExact());
        assertTrue(shared.getWarnings().stream().anyMatch(w -> w.contains("多个制造方案")));
    }

    @Test
    void supplyAboveDemandNeverProducesNegativeGap() {
        line(10L, 200L, "PCS", "1", "10", "0", "buy", null);
        material(200L, "PCS", "0");
        stock(200L, "100", "0");
        purchaseSupply.put(200L, new BigDecimal("100"));

        RequirementLineVO line = line(preview("1"), 200L);

        assertEquals("0/1", line.getGapQtyExact());
        assertEquals(0, line.getGapQtyDisplay().compareTo(BigDecimal.ZERO));
    }

    @Test
    void missingUnitConversionIsBlockedWithPath() {
        line(10L, 200L, "M", "1", "10", "0", "buy", null);
        material(200L, "M2", "0");

        BusinessException error = assertThrows(BusinessException.class, () -> preview("1"));
        assertTrue(error.getMessage().contains("UNIT_CONVERSION_MISSING"), error.getMessage());
    }

    @Test
    void rollWidthWithoutBatchLengthIsBlockedFor013() {
        line(10L, 200L, "M", "1", "10", "0", "buy", null);
        material(200L, "M2", "0");
        materials.get(200L).setWidthMm(new BigDecimal("195.00"));

        BusinessException error = assertThrows(BusinessException.class, () -> preview("1"));
        assertTrue(error.getMessage().contains("013"), error.getMessage());
    }

    @Test
    void deepCycleIsRejectedWithPath() {
        line(10L, 200L, "PCS", "1", "1", "0", "make", 20L);
        material(200L, "PCS", "0");
        material(300L, "PCS", "0");
        boms.put(20L, bom(20L, null, "MATERIAL", 200L, 3, "B20"));
        lines.put(20L, List.of(line(201L, 20L, 300L, "PCS", "1", "1", "0", "make", 30L)));
        boms.put(30L, bom(30L, null, "MATERIAL", 300L, 3, "B30"));
        lines.put(30L, List.of(line(301L, 30L, 200L, "PCS", "1", "1", "0", "make", 20L)));

        BusinessException error = assertThrows(BusinessException.class, () -> preview("1"));
        assertTrue(error.getMessage().contains("循环引用"), error.getMessage());
        assertTrue(error.getMessage().contains("B30#30"), error.getMessage());
    }

    @Test
    void danglingSubBomIsRejected() {
        line(10L, 200L, "PCS", "1", "1", "0", "make", 99L);
        material(200L, "PCS", "0");

        BusinessException error = assertThrows(BusinessException.class, () -> preview("1"));
        assertTrue(error.getMessage().contains("悬空引用"), error.getMessage());
    }

    @Test
    void makeWithoutSubBomIsRejectedEvenWhenStockCouldCover() {
        line(10L, 200L, "PCS", "1", "1", "0", "make", null);
        material(200L, "PCS", "0");
        stock(200L, "999", "0");

        BusinessException error = assertThrows(BusinessException.class, () -> preview("1"));
        assertTrue(error.getMessage().contains("MAKE_WITHOUT_SCHEME"), error.getMessage());
    }

    @Test
    void unapprovedRootBomIsRejected() {
        boms.put(10L, bom(10L, 1L, "PRODUCT", 1L, 1, "B10"));

        BusinessException error = assertThrows(BusinessException.class, () -> preview("1"));
        assertTrue(error.getMessage().contains("未处于已批准状态"), error.getMessage());
    }

    private RequirementSummaryVO preview(String qty) {
        RequirementPreviewRequest request = new RequirementPreviewRequest();
        request.setProductId(1L);
        request.setQuantity(new BigDecimal(qty));
        request.setDemandDate(DEMAND_DATE);
        return service.preview(request);
    }

    private RequirementLineVO line(RequirementSummaryVO summary, Long materialId) {
        return summary.getLines().stream()
                .filter(line -> materialId.equals(line.getMaterialId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少物料行 " + materialId));
    }

    private void line(Long bomId, Long materialId, String unit, String module, String base,
                      String loss, String sourceType, Long subBomId) {
        lines.computeIfAbsent(bomId, key -> new ArrayList<>())
                .add(line(1000L + lines.size() * 10 + materialId, bomId, materialId, unit,
                        module, base, loss, sourceType, subBomId));
    }

    private ManufacturingDemandSnapshot.Line line(Long itemId, Long bomId, Long materialId, String unit,
                                                  String module, String base, String loss,
                                                  String sourceType, Long subBomId) {
        ManufacturingDemandSnapshot.Line line = new ManufacturingDemandSnapshot.Line();
        line.setItemId(itemId);
        line.setBomId(bomId);
        line.setMaterialId(materialId);
        line.setMaterialCode("M" + materialId);
        line.setMaterialName("物料" + materialId);
        line.setUnit(unit);
        line.setModuleQty(new BigDecimal(module));
        line.setBaseQty(new BigDecimal(base));
        line.setLossRate(new BigDecimal(loss));
        line.setSourceType(sourceType);
        line.setSubBomId(subBomId);
        line.setReviewStatus(3);
        line.setItemOrder(itemId.intValue());
        return line;
    }

    private ManufacturingDemandSnapshot bom(Long bomId, Long productId, String outputType, Long outputId,
                                            int approveStatus, String code) {
        ManufacturingDemandSnapshot bom = new ManufacturingDemandSnapshot();
        bom.setBomId(bomId);
        bom.setBomCode(code);
        bom.setBomName(code);
        bom.setProductId(productId);
        bom.setOutputType(outputType);
        bom.setOutputId(outputId);
        bom.setApproveStatus(approveStatus);
        bom.setEffectiveDate(LocalDate.of(2026, 1, 1));
        return bom;
    }

    private void material(Long materialId, String unit, String safeStock) {
        ManufacturingSupplySnapshotMapper.MaterialRow row = new ManufacturingSupplySnapshotMapper.MaterialRow();
        row.setMaterialId(materialId);
        row.setMaterialCode("M" + materialId);
        row.setMaterialName("物料" + materialId);
        row.setUnit(unit);
        row.setSafeStock(new BigDecimal(safeStock));
        row.setStatus(1);
        materials.put(materialId, row);
    }

    private void stock(Long materialId, String total, String reserved) {
        stocks.put(materialId, stock(total, reserved));
    }

    private ManufacturingSupplySnapshotMapper.StockRow stock(String total, String reserved) {
        ManufacturingSupplySnapshotMapper.StockRow row = new ManufacturingSupplySnapshotMapper.StockRow();
        row.setTotalQuantity(new BigDecimal(total));
        row.setTotalReserved(new BigDecimal(reserved));
        return row;
    }
}
