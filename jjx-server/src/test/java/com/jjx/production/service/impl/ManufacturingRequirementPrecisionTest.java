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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D01/D03/D04 精确比例测试：真实 ServiceImpl + 真实 ProviderImpl，Mapper 为只读替身。
 * 只验证计算与反例，不冒充真实 MySQL 写入/并发。
 */
class ManufacturingRequirementPrecisionTest {

    private ManufacturingSupplySnapshotMapper mapper;
    private ManufacturingRequirementServiceImpl service;
    private ManufacturingSupplySnapshotMapper.ProductRow product;
    private ManufacturingDemandSnapshot root;
    private final List<ManufacturingDemandSnapshot.Line> rootLines = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mapper = mock(ManufacturingSupplySnapshotMapper.class);
        service = new ManufacturingRequirementServiceImpl(mapper,
                new ManufacturingSupplySnapshotProviderImpl(mapper));
        product = product(1L, "P1", "测试产品", "PCS", 10L);
        root = bom(10L, 1L, "PRODUCT", 1L, 3, LocalDate.of(2026, 1, 1), "B10");
        when(mapper.selectProduct(1L)).thenReturn(product);
        when(mapper.selectBom(10L)).thenReturn(root);
        when(mapper.selectBomLines(10L)).thenReturn(rootLines);
        when(mapper.selectPurchaseSupply(anyLong(), any(LocalDate.class), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(BigDecimal.ZERO);
        when(mapper.selectMaterialProductionSupply(anyLong(), any(LocalDate.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(BigDecimal.ZERO);
        when(mapper.selectMaterialStock(anyLong())).thenReturn(stock("0", "0"));
    }

    @Test
    void sixModuleOneBaseTimes300IsExactly50Not51() {
        addLine(101L, 10L, 100L, "PCS", "6", "1", "0", "buy", null);
        stubMaterial(100L, "RM100", "片材", "PCS", "0");

        RequirementSummaryVO summary = preview("300");

        RequirementLineVO line = line(summary, 100L);
        assertEquals("50/1", line.getGapQtyExact());
        assertEquals(0, line.getGapQtyDisplay().compareTo(new BigDecimal("50")));
        assertEquals("50/1", line.getTheoreticalQtyExact());
    }

    @Test
    void upperOnePieceAndLowerSixModuleDoesNotDivideTwice() {
        addLine(101L, 10L, 200L, "PCS", "1", "1", "0", "make", 20L);
        stubMaterial(200L, "SF200", "半成品", "PCS", "0");
        ManufacturingDemandSnapshot sub = bom(20L, null, "MATERIAL", 200L, 3,
                LocalDate.of(2026, 1, 1), "B20");
        ManufacturingDemandSnapshot.Line subLine = line(201L, 20L, 300L, "PCS",
                "6", "1", "0", "buy", null);
        when(mapper.selectBom(20L)).thenReturn(sub);
        when(mapper.selectBomLines(20L)).thenReturn(List.of(subLine));
        stubMaterial(300L, "RM300", "大料", "PCS", "0");

        RequirementSummaryVO summary = preview("300");

        assertEquals("50/1", line(summary, 300L).getGapQtyExact());
        assertEquals("300/1", line(summary, 200L).getGapQtyExact());
    }

    @Test
    void decimalModuleAndBaseUseExactRatio() {
        addLine(101L, 10L, 100L, "PCS", "2.5", "0.5", "0", "buy", null);
        stubMaterial(100L, "RM100", "片材", "PCS", "0");

        RequirementSummaryVO summary = preview("100");

        assertEquals("20/1", line(summary, 100L).getGapQtyExact());
    }

    @Test
    void fractionalIntermediateIsNotRoundedBeforeNextLayer() {
        addLine(101L, 10L, 200L, "PCS", "3", "1", "0", "make", 20L);
        stubMaterial(200L, "SF200", "半成品", "PCS", "0");
        ManufacturingDemandSnapshot sub = bom(20L, null, "MATERIAL", 200L, 3,
                LocalDate.of(2026, 1, 1), "B20");
        ManufacturingDemandSnapshot.Line subLine = line(201L, 20L, 300L, "PCS",
                "1", "3", "0", "buy", null);
        when(mapper.selectBom(20L)).thenReturn(sub);
        when(mapper.selectBomLines(20L)).thenReturn(List.of(subLine));
        stubMaterial(300L, "RM300", "大料", "PCS", "0");

        RequirementSummaryVO summary = preview("1");

        assertEquals("1/1", line(summary, 300L).getGapQtyExact(),
                "1/3 的中间值不能先 CEILING，再乘 3 应恰好为 1");
        RequirementLineVO half = line(summary, 200L);
        assertEquals("1/3", half.getGapQtyExact());
    }

    @Test
    void halfPercentLossIsNotTruncatedToZero() {
        addLine(101L, 10L, 100L, "PCS", "1", "1", "0.50", "buy", null);
        stubMaterial(100L, "RM100", "片材", "PCS", "0");

        RequirementSummaryVO summary = preview("200");

        assertEquals("201/1", line(summary, 100L).getLossAppliedQtyExact(),
                "0.50% 损耗应精确为 201/200 因子，不能当 0");
        assertEquals("201/1", line(summary, 100L).getGapQtyExact());
    }

    @Test
    void zeroModuleIsRejectedWithPath() {
        addLine(101L, 10L, 100L, "PCS", "0", "1", "0", "buy", null);
        stubMaterial(100L, "RM100", "片材", "PCS", "0");

        BusinessException error = assertThrows(BusinessException.class, () -> preview("10"));
        assertTrue(error.getMessage().contains("模数必须大于0"), error.getMessage());
        assertTrue(error.getMessage().contains("B10#10"), error.getMessage());
    }

    @Test
    void negativeLossIsRejected() {
        addLine(101L, 10L, 100L, "PCS", "1", "1", "-0.01", "buy", null);
        stubMaterial(100L, "RM100", "片材", "PCS", "0");

        BusinessException error = assertThrows(BusinessException.class, () -> preview("10"));
        assertTrue(error.getMessage().contains("损耗率不能为负数"), error.getMessage());
    }

    private RequirementSummaryVO preview(String qty) {
        RequirementPreviewRequest request = new RequirementPreviewRequest();
        request.setProductId(1L);
        request.setQuantity(new BigDecimal(qty));
        request.setDemandDate(LocalDate.of(2026, 11, 11));
        return service.preview(request);
    }

    private RequirementLineVO line(RequirementSummaryVO summary, Long materialId) {
        return summary.getLines().stream()
                .filter(line -> materialId.equals(line.getMaterialId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少物料行 " + materialId));
    }

    private void stubMaterial(Long materialId, String code, String name, String unit, String safeStock) {
        when(mapper.selectMaterial(materialId)).thenReturn(material(materialId, code, name, unit, safeStock));
    }

    private void addLine(Long itemId, Long bomId, Long materialId, String unit, String module,
                         String base, String loss, String sourceType, Long subBomId) {
        rootLines.add(line(itemId, bomId, materialId, unit, module, base, loss, sourceType, subBomId));
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
                                            int approveStatus, LocalDate effectiveDate, String code) {
        ManufacturingDemandSnapshot bom = new ManufacturingDemandSnapshot();
        bom.setBomId(bomId);
        bom.setBomCode(code);
        bom.setBomName(code);
        bom.setBomType("manufacturing".equals(outputType) ? "manufacturing" : "engineering");
        bom.setProductId(productId);
        bom.setOutputType(outputType);
        bom.setOutputId(outputId);
        bom.setApproveStatus(approveStatus);
        bom.setEffectiveDate(effectiveDate);
        bom.setIsCurrent(1);
        return bom;
    }

    private ManufacturingSupplySnapshotMapper.ProductRow product(Long id, String code, String name,
                                                                 String unit, Long currentBomId) {
        ManufacturingSupplySnapshotMapper.ProductRow row = new ManufacturingSupplySnapshotMapper.ProductRow();
        row.setProductId(id);
        row.setProductCode(code);
        row.setProductName(name);
        row.setUnit(unit);
        row.setCurrentBomId(currentBomId);
        return row;
    }

    private ManufacturingSupplySnapshotMapper.MaterialRow material(Long id, String code, String name,
                                                                   String unit, String safeStock) {
        ManufacturingSupplySnapshotMapper.MaterialRow row = new ManufacturingSupplySnapshotMapper.MaterialRow();
        row.setMaterialId(id);
        row.setMaterialCode(code);
        row.setMaterialName(name);
        row.setUnit(unit);
        row.setSafeStock(new BigDecimal(safeStock));
        row.setStatus(1);
        return row;
    }

    private ManufacturingSupplySnapshotMapper.StockRow stock(String total, String reserved) {
        ManufacturingSupplySnapshotMapper.StockRow row = new ManufacturingSupplySnapshotMapper.StockRow();
        row.setTotalQuantity(new BigDecimal(total));
        row.setTotalReserved(new BigDecimal(reserved));
        return row;
    }
}
