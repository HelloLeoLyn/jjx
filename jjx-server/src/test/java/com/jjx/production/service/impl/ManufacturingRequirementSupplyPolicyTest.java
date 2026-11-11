package com.jjx.production.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.vo.RequirementLineVO;
import com.jjx.production.domain.vo.RequirementSummaryVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 供给口径策略测试（dev-20261111-006 第二步）：采购审批/部分收货/单位/仓库维度、生产工单审批与BOM有效期、
 * 卷类换算阻断。真实 MyBatis Mapper + 真实 Service/Provider，隔离 H2；不冒充真实 MySQL 事务与并发。
 */
class ManufacturingRequirementSupplyPolicyTest extends AbstractManufacturingRequirementTest {

    // ==================== 采购：审批口径与部分收货 ====================

    @Test
    void partialReceiptCountsOnlyRemainingQuantity() {
        addRootLine(101L, 100L, "1", "buy", null);
        purchase(1L, "PO001", 3, 1, LocalDate.of(2026, 11, 10));
        purchaseItem(11L, 1L, 100L, "100", "40", "PCS");

        RequirementLineVO line = line(preview(100L, null), 100L);

        assertEquals(0, line.getPurchaseSupply().compareTo(new BigDecimal("60")),
                "部分收货只计未收 100-40=60，不能整单按待收货过滤");
        assertEquals(0, line.getPlannedPurchaseSupply().compareTo(BigDecimal.ZERO));
        assertEquals("40/1", line.getGapQtyExact());
    }

    @Test
    void draftAndPendingPurchaseGoToPlannedSupplyWithoutDeductingGap() {
        addRootLine(101L, 100L, "1", "buy", null);
        purchase(1L, "PO001", 1, 0, LocalDate.of(2026, 11, 10));
        purchaseItem(11L, 1L, 100L, "80", "0", "PCS");
        purchase(2L, "PO002", 2, 0, LocalDate.of(2026, 11, 10));
        purchaseItem(12L, 2L, 100L, "50", "0", "PCS");

        RequirementSummaryVO summary = preview(100L, null);
        RequirementLineVO line = line(summary, 100L);

        assertEquals(0, line.getPurchaseSupply().compareTo(BigDecimal.ZERO), "草稿/待审批不是确定供应");
        assertEquals(0, line.getPlannedPurchaseSupply().compareTo(new BigDecimal("130")));
        assertEquals("100/1", line.getGapQtyExact(), "未批准数量不得抵扣缺口");
        assertEquals(0, summary.getPlannedPurchaseSupply().compareTo(new BigDecimal("130")));
        assertTrue(hasWarning(summary, "草稿/待审批"), summary.getWarnings().toString());
        assertTrue(line.getLimitations().stream().anyMatch(t -> t.contains("未批准")), line.getLimitations().toString());
    }

    @Test
    void rejectedAndCancelledPurchaseNeverAppear() {
        addRootLine(101L, 100L, "1", "buy", null);
        purchase(1L, "PO001", 4, 0, LocalDate.of(2026, 11, 10));
        purchaseItem(11L, 1L, 100L, "100", "0", "PCS");
        purchase(2L, "PO002", 5, 0, LocalDate.of(2026, 11, 10));
        purchaseItem(12L, 2L, 100L, "100", "0", "PCS");

        RequirementLineVO line = line(preview(100L, null), 100L);

        assertEquals(0, line.getPurchaseSupply().compareTo(BigDecimal.ZERO));
        assertEquals(0, line.getPlannedPurchaseSupply().compareTo(BigDecimal.ZERO));
        assertEquals(0, line.getWarnings().stream().filter(w -> w.contains("采购行")).count(),
                "已驳回/已取消行不应被读取或出现在 warning 中");
        assertEquals("100/1", line.getGapQtyExact());
    }

    @Test
    void overReceivedLineDoesNotOffsetOtherLines() {
        addRootLine(101L, 100L, "1", "buy", null);
        purchase(1L, "PO001", 3, 1, LocalDate.of(2026, 11, 10));
        purchaseItem(11L, 1L, 100L, "50", "80", "PCS");
        purchase(2L, "PO002", 3, 0, LocalDate.of(2026, 11, 10));
        purchaseItem(12L, 2L, 100L, "100", "0", "PCS");

        RequirementLineVO line = line(preview(100L, null), 100L);

        assertEquals(0, line.getPurchaseSupply().compareTo(new BigDecimal("100")),
                "超收行按0计，负数不得冲抵其他行");
        assertTrue(hasWarning(line, "超过订购"), line.getWarnings().toString());
    }

    @Test
    void purchaseLineUnitMismatchIsExcludedWithWarning() {
        addRootLine(101L, 100L, "1", "buy", null);
        purchase(1L, "PO001", 3, 0, LocalDate.of(2026, 11, 10));
        purchaseItem(11L, 1L, 100L, "10", "0", "包");

        RequirementLineVO line = line(preview(100L, null), 100L);

        assertEquals(0, line.getPurchaseSupply().compareTo(BigDecimal.ZERO),
                "采购行单位与库存单位不一致且无依据，不得当库存单位直接相加");
        assertTrue(hasWarning(line, "UNIT_CONVERSION_MISSING"), line.getWarnings().toString());
        assertTrue(hasWarning(line, "RM100"), "warning 必须能定位物料：" + line.getWarnings());
        assertTrue(hasWarning(line, "PO001"), "warning 必须能定位订单：" + line.getWarnings());
        assertEquals("100/1", line.getGapQtyExact());
    }

    @Test
    void purchaseLineUnitConvertedByArchiveUnitConv() {
        addRootLine(101L, 100L, "1", "buy", null);
        updateUnitConversion(100L, "包", "12");
        purchase(1L, "PO001", 3, 0, LocalDate.of(2026, 11, 10));
        purchaseItem(11L, 1L, 100L, "2", "0", "包");

        RequirementLineVO line = line(preview(100L, null), 100L);

        assertEquals(0, line.getPurchaseSupply().compareTo(new BigDecimal("24")), "2包×unit_conv 12=24");
        assertTrue(hasWarning(line, "unit_conv"), line.getWarnings().toString());
        assertEquals("76/1", line.getGapQtyExact());
    }

    // ==================== 仓库维度 ====================

    @Test
    void warehouseFilterDoesNotDeductPurchaseOrProductionSupply() {
        addRootLine(101L, 100L, "1", "buy", null);
        db.update("INSERT INTO inventory_stock_item(material_id,warehouse_id,quantity,reserved_quantity,status) " +
                "VALUES (?,?,?,?,1)", 100L, 1L, new BigDecimal("50"), BigDecimal.ZERO);
        purchase(1L, "PO001", 3, 0, LocalDate.of(2026, 11, 10));
        purchaseItem(11L, 1L, 100L, "100", "0", "PCS");
        db.update("INSERT INTO engineering_bom(bom_id,bom_code,bom_name,bom_type,product_id,output_type," +
                "output_id,approve_status,is_current,effective_date) VALUES (30,'B30','原料自制BOM'," +
                "'manufacturing',NULL,'MATERIAL',100,3,0,DATE '2026-01-01')");
        production(1L, "MO1", 30L, "60", "0", 4, 3, LocalDate.of(2026, 11, 10), null, null, "PCS");

        RequirementSummaryVO summary = preview(100L, 1L);
        RequirementLineVO line = line(summary, 100L);

        assertEquals(0, line.getAvailableForDemand().compareTo(new BigDecimal("50")), "现货只取该仓");
        assertEquals(0, line.getAllocatedPurchase().compareTo(BigDecimal.ZERO), "采购供给不得跨仓抵扣");
        assertEquals(0, line.getAllocatedProduction().compareTo(BigDecimal.ZERO), "生产供给不得跨仓抵扣");
        assertEquals(0, line.getPurchaseSupply().compareTo(new BigDecimal("100")), "仍展示采购供给数量");
        assertEquals(0, line.getProductionSupply().compareTo(new BigDecimal("60")), "仍展示生产供给数量");
        assertEquals("50/1", line.getGapQtyExact());
        assertTrue(hasWarning(summary, "无法归属仓库"), summary.getWarnings().toString());
    }

    @Test
    void unknownOrDisabledWarehouseIsRejected() {
        addRootLine(101L, 100L, "1", "buy", null);

        BusinessException missing = assertThrows(BusinessException.class, () -> preview(100L, 99L));
        assertTrue(missing.getMessage().contains("不存在"), missing.getMessage());

        BusinessException disabled = assertThrows(BusinessException.class, () -> preview(100L, 2L));
        assertTrue(disabled.getMessage().contains("停用"), disabled.getMessage());
    }

    // ==================== 生产供给 ====================

    @Test
    void productionOrderRequiresApprovedOrderAndValidBomWindow() {
        addRootLine(101L, 200L, "1", "buy", null);

        // 未批准工单：SQL 层排除
        production(1L, "MO1", 20L, "100", "0", 4, 0, LocalDate.of(2026, 11, 10), null, null, "PCS");
        assertEquals(0, line(preview(100L, null), 200L).getProductionSupply().compareTo(BigDecimal.ZERO),
                "approval_status≠APPROVED 的工单不计入生产供给");
        db.update("DELETE FROM production_order");

        // BOM 未批准
        updateProductionBom(20L, 1, LocalDate.of(2026, 1, 1), null);
        production(2L, "MO2", 20L, "100", "0", 4, 3, LocalDate.of(2026, 11, 10), null, null, "PCS");
        RequirementLineVO unapprovedBom = line(preview(100L, null), 200L);
        assertEquals(0, unapprovedBom.getProductionSupply().compareTo(BigDecimal.ZERO));
        assertTrue(hasWarning(unapprovedBom, "未批准"), unapprovedBom.getWarnings().toString());

        // BOM 生效日晚于需求日
        updateProductionBom(20L, 3, LocalDate.of(2026, 12, 1), null);
        RequirementLineVO futureBom = line(preview(100L, null), 200L);
        assertEquals(0, futureBom.getProductionSupply().compareTo(BigDecimal.ZERO));
        assertTrue(hasWarning(futureBom, "生效日期"), futureBom.getWarnings().toString());

        // BOM 已失效
        updateProductionBom(20L, 3, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 1));
        RequirementLineVO expiredBom = line(preview(100L, null), 200L);
        assertEquals(0, expiredBom.getProductionSupply().compareTo(BigDecimal.ZERO));
        assertTrue(hasWarning(expiredBom, "失效日期"), expiredBom.getWarnings().toString());
    }

    @Test
    void productionSupplyUsesPlannedMinusFinishedNotRemaining() {
        addRootLine(101L, 200L, "1", "buy", null);
        production(1L, "MO1", 20L, "100", "30", 4, 3, LocalDate.of(2026, 11, 10), null, null, "PCS");

        RequirementLineVO line = line(preview(100L, null), 200L);

        assertEquals(0, line.getProductionSupply().compareTo(new BigDecimal("70")),
                "planned 100 − finished 30 = 70；不能用 remaining_quantity 或 completed");
        assertEquals("30/1", line.getGapQtyExact());
    }

    // ==================== 卷类换算 ====================

    @Test
    void coilMaterialWithoutSavedFactorIsBlockedAndPointsTo013() {
        addRootLine(101L, 100L, "卷", "1", "buy", null);
        db.update("UPDATE inventory_material SET unit='M2' WHERE material_id=100");
        updateMaterialDims(100L, "1000", "3000");

        BusinessException error = assertThrows(BusinessException.class, () -> preview(1L, null));
        assertTrue(error.getMessage().contains("UNIT_CONVERSION_MISSING"), error.getMessage());
        assertTrue(error.getMessage().contains("013"), "必须指向 013 逐卷口径：" + error.getMessage());
    }

    @Test
    void coilMaterialWithSavedFactorUsesIt() {
        addRootLine(101L, 100L, "卷", "1", "buy", null);
        addRootLineWithFactor(101L, 100L, "卷", "1", "2.5", "实测每卷2.5㎡（031 供应商实测）");
        db.update("UPDATE inventory_material SET unit='M2' WHERE material_id=100");
        updateMaterialDims(100L, "1000", "3000");

        RequirementLineVO line = line(preview(1L, null), 100L);

        assertEquals(0, line.getLossAppliedQtyApprox().compareTo(new BigDecimal("2.5")));
        assertEquals("5/2", line.getGapQtyExact());
        assertEquals("5/2", line.getUnitFactorExact());
        assertTrue(line.getUnitFactorBasis().contains("实测"), line.getUnitFactorBasis());
    }

    @Test
    void pieceSheetKeepsArchiveDimensionConversionAndReportsSource() {
        addRootLine(101L, 100L, "片", "1", "buy", null);
        db.update("UPDATE inventory_material SET unit='M2' WHERE material_id=100");
        updateMaterialDims(100L, "1000", "3000");

        RequirementLineVO line = line(preview(1L, null), 100L);

        assertEquals(0, line.getLossAppliedQtyApprox().compareTo(new BigDecimal("3")), "1片=1×3㎡定尺");
        assertTrue(line.getUnitFactorSource().contains("width_mm"), line.getUnitFactorSource());
    }
}
