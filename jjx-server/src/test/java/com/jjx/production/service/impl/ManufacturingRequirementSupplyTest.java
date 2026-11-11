package com.jjx.production.service.impl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.production.controller.ManufacturingRequirementController;
import com.jjx.production.domain.dto.RequirementPreviewRequest;
import com.jjx.production.domain.vo.RequirementLineVO;
import com.jjx.production.domain.vo.RequirementSummaryVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 供给读取与只读性测试：真实 MyBatis Mapper + 真实 ServiceImpl/ProviderImpl，隔离 H2（MySQL 模式）。
 * 不冒充真实 MySQL 写入/并发，也不冒充生产闭环。
 */
class ManufacturingRequirementSupplyTest extends AbstractManufacturingRequirementTest {

    @Test
    void stockComponentsDeductReservationAndSafeStockOnce() {
        addRootLine(101L, 100L, "1", "buy", null);
        updateSafeStock(100L, "10");
        db.update("INSERT INTO inventory_stock(material_id,total_quantity,total_reserved) VALUES (?,?,?)",
                100L, new BigDecimal("200"), new BigDecimal("60"));

        RequirementLineVO line = line(preview(300L, null), 100L);

        assertEquals(0, line.getStockOnHand().compareTo(new BigDecimal("200")));
        assertEquals(0, line.getStockReserved().compareTo(new BigDecimal("60")));
        assertEquals(0, line.getSafeStock().compareTo(new BigDecimal("10")));
        assertEquals(0, line.getAvailableForDemand().compareTo(new BigDecimal("130")));
        assertEquals("170/1", line.getGapQtyExact());
    }

    @Test
    void materialProductionSupplyUsesBomOutputIdentityAndExcludesAllocatedOrders() {
        addRootLine(101L, 200L, "1", "make", 20L);
        production(1L, "MO1", 20L, "40", "0", 4, 3, LocalDate.of(2026, 11, 10), null, null, "PCS");
        production(2L, "MO2", 20L, "50", "0", 4, 3, LocalDate.of(2026, 11, 10), 9L, null, "PCS");
        production(3L, "MO3", 20L, "60", "0", 4, 3, LocalDate.of(2026, 11, 10), null, 9L, "PCS");
        production(4L, "MO4", 20L, "70", "0", 4, 3, LocalDate.of(2026, 11, 20), null, null, "PCS");
        production(5L, "MO5", 10L, "999", "0", 4, 3, LocalDate.of(2026, 11, 10), null, null, "PCS");

        RequirementLineVO half = line(preview(100L, null), 200L);

        assertEquals(0, half.getProductionSupply().compareTo(new BigDecimal("40")),
                "只认工单绑定BOM的MATERIAL产出身份；不把 product_id 当物料，也排除已分配工单");
        assertEquals("60/1", half.getGapQtyExact());
        assertTrue(half.getMaterialProductionAdapterApplied(), "半成品经 BOM 产出身份兼容读适配");
        assertEquals("60/1", line(preview(100L, null), 300L).getGapQtyExact());
        assertFalse(line(preview(100L, null), 300L).getMaterialProductionAdapterApplied(),
                "无 MATERIAL 产出 BOM 的物料不得标记为已使用兼容读适配");
    }

    @Test
    void warehouseDimensionUsesBatchRowsAndDoesNotHardDeductSafeStock() {
        addRootLine(101L, 100L, "1", "buy", null);
        updateSafeStock(100L, "10");
        db.update("INSERT INTO inventory_stock(material_id,total_quantity,total_reserved) VALUES (?,?,?)",
                100L, new BigDecimal("200"), new BigDecimal("60"));
        db.update("INSERT INTO inventory_stock_item(material_id,warehouse_id,quantity,reserved_quantity,status) " +
                "VALUES (?,?,?,?,1)", 100L, 1L, new BigDecimal("50"), new BigDecimal("5"));
        db.update("INSERT INTO inventory_stock_item(material_id,warehouse_id,quantity,reserved_quantity,status) " +
                "VALUES (?,?,?,?,1)", 100L, 2L, new BigDecimal("70"), new BigDecimal("0"));

        RequirementLineVO line = line(preview(100L, 1L), 100L);

        assertEquals(0, line.getAvailableForDemand().compareTo(new BigDecimal("45")));
        assertFalse(line.getSafeStockDeducted());
        assertTrue(line.getWarnings().stream().anyMatch(w -> w.contains("未分摊")), line.getWarnings().toString());
        assertEquals("55/1", line.getGapQtyExact());
    }

    @Test
    void previewEndpointReusesViewPermissionsAndHasNoFakeConfirm() throws Exception {
        Method preview = ManufacturingRequirementController.class.getMethod("preview",
                RequirementPreviewRequest.class);
        SaCheckPermission permission = preview.getAnnotation(SaCheckPermission.class);
        assertNotNull(permission);
        assertEquals(2, permission.value().length);
        assertTrue(Arrays.asList(permission.value()).contains("production:order:view"));
        assertTrue(Arrays.asList(permission.value()).contains("inventory:alert:view"));
        long postMappings = Arrays.stream(ManufacturingRequirementController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(
                        org.springframework.web.bind.annotation.PostMapping.class))
                .count();
        assertEquals(1L, postMappings, "只暴露只读 preview，不暴露假 confirm 成功接口");
    }

    @Test
    void repeatedPreviewDoesNotWriteStockOrDocuments() {
        addRootLine(101L, 100L, "1", "buy", null);
        db.update("INSERT INTO inventory_stock(material_id,total_quantity,total_reserved) VALUES (?,?,?)",
                100L, new BigDecimal("200"), new BigDecimal("60"));

        int stockRows = count("inventory_stock");
        int stockItemRows = count("inventory_stock_item");
        int purchaseRows = count("purchase_order");
        int productionRows = count("production_order");
        int itemRows = count("engineering_bom_item");

        RequirementSummaryVO first = preview(300L, null);
        RequirementSummaryVO second = preview(300L, null);

        assertEquals("160/1", line(first, 100L).getGapQtyExact());
        assertEquals("160/1", line(second, 100L).getGapQtyExact());
        assertEquals(stockRows, count("inventory_stock"));
        assertEquals(stockItemRows, count("inventory_stock_item"));
        assertEquals(purchaseRows, count("purchase_order"));
        assertEquals(productionRows, count("production_order"));
        assertEquals(itemRows, count("engineering_bom_item"));
        assertEquals(0, db.queryForObject("SELECT total_quantity FROM inventory_stock WHERE material_id=100",
                BigDecimal.class).compareTo(new BigDecimal("200")));
    }
}
