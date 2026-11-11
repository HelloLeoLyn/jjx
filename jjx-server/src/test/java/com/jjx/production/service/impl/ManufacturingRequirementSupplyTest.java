package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.production.controller.ManufacturingRequirementController;
import com.jjx.production.domain.dto.RequirementPreviewRequest;
import com.jjx.production.domain.vo.RequirementLineVO;
import com.jjx.production.domain.vo.RequirementSummaryVO;
import com.jjx.production.mapper.ManufacturingSupplySnapshotMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 供给读取与只读性测试：真实 MyBatis Mapper + 真实 ServiceImpl/ProviderImpl，隔离 H2（MySQL 模式）。
 * 不冒充真实 MySQL 写入/并发，也不冒充生产闭环。
 */
class ManufacturingRequirementSupplyTest {

    private static final LocalDate DEMAND_DATE = LocalDate.of(2026, 11, 11);

    private JdbcTemplate db;
    private ManufacturingRequirementServiceImpl service;

    @BeforeEach
    void setUp() {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("d-supply",
                new SpringManagedTransactionFactory(), source));
        configuration.addMapper(ManufacturingSupplySnapshotMapper.class);
        var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        db = new JdbcTemplate(source);
        createSchema();
        seedBase();
        var mapper = session.getMapper(ManufacturingSupplySnapshotMapper.class);
        service = new ManufacturingRequirementServiceImpl(mapper,
                new ManufacturingSupplySnapshotProviderImpl(mapper));
    }

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
    void purchaseSupplyUsesApprovalEnumOnTimeAndRemainingQuantity() {
        addRootLine(101L, 100L, "1", "buy", null);

        purchase(1L, 1, 0, LocalDate.of(2026, 11, 10));
        purchaseItem(11L, 1L, 100L, "100", "0");
        purchase(2L, 3, 0, LocalDate.of(2026, 11, 20));
        purchaseItem(12L, 2L, 100L, "100", "0");
        purchase(3L, 4, 0, LocalDate.of(2026, 11, 9));
        purchaseItem(13L, 3L, 100L, "100", "0");
        purchase(4L, 2, 0, LocalDate.of(2026, 11, 9));
        purchaseItem(14L, 4L, 100L, "50", "20");
        purchase(5L, 3, 0, null);
        purchaseItem(15L, 5L, 100L, "100", "0");

        RequirementLineVO line = line(preview(300L, null), 100L);

        assertEquals(0, line.getPurchaseSupply().compareTo(new BigDecimal("130")),
                "草稿100 + 待审批30；已驳回/迟到/无日期不计");
        assertEquals("170/1", line.getGapQtyExact());
    }

    @Test
    void materialProductionSupplyUsesBomOutputIdentityAndExcludesAllocatedOrders() {
        addRootLine(101L, 200L, "1", "make", 20L);
        production(1L, null, 20L, "40", 4, LocalDate.of(2026, 11, 10), null, null);
        production(2L, null, 20L, "50", 4, LocalDate.of(2026, 11, 10), 9L, null);
        production(3L, null, 20L, "60", 4, LocalDate.of(2026, 11, 10), null, 9L);
        production(4L, null, 20L, "70", 4, LocalDate.of(2026, 11, 20), null, null);
        production(5L, 200L, 10L, "999", 4, LocalDate.of(2026, 11, 10), null, null);

        RequirementLineVO half = line(preview(100L, null), 200L);

        assertEquals(0, half.getProductionSupply().compareTo(new BigDecimal("40")),
                "只认工单绑定BOM的MATERIAL产出身份；不把 product_id 当物料，也排除已分配工单");
        assertEquals("60/1", half.getGapQtyExact());
        assertEquals("60/1", line(preview(100L, null), 300L).getGapQtyExact());
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

    private RequirementSummaryVO preview(Long qty, Long warehouseId) {
        RequirementPreviewRequest request = new RequirementPreviewRequest();
        request.setProductId(1L);
        request.setQuantity(new BigDecimal(qty));
        request.setDemandDate(DEMAND_DATE);
        request.setWarehouseId(warehouseId);
        return service.preview(request);
    }

    private RequirementLineVO line(RequirementSummaryVO summary, Long materialId) {
        return summary.getLines().stream()
                .filter(line -> materialId.equals(line.getMaterialId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少物料行 " + materialId));
    }

    private int count(String table) {
        Integer value = db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    private void createSchema() {
        db.execute("CREATE TABLE product(product_id BIGINT PRIMARY KEY, product_code VARCHAR(50), " +
                "product_name VARCHAR(200), unit VARCHAR(20), current_bom_id BIGINT)");
        db.execute("CREATE TABLE engineering_bom(bom_id BIGINT PRIMARY KEY, bom_code VARCHAR(50), " +
                "bom_name VARCHAR(100), bom_type VARCHAR(20), product_id BIGINT, output_type VARCHAR(20), " +
                "output_id BIGINT, approve_status INT, is_current TINYINT, effective_date DATE, " +
                "expiry_date DATE, create_time TIMESTAMP)");
        db.execute("CREATE TABLE engineering_bom_item(item_id BIGINT PRIMARY KEY, bom_id BIGINT, " +
                "parent_material_id BIGINT, material_id BIGINT, material_code VARCHAR(50), " +
                "material_name VARCHAR(200), unit VARCHAR(20), module_qty DECIMAL(12,4), " +
                "base_qty DECIMAL(12,4), loss_rate DECIMAL(5,2), min_issue_qty DECIMAL(12,4), " +
                "source_type VARCHAR(20), sub_bom_id BIGINT, unit_factor DECIMAL(18,8), " +
                "unit_factor_basis VARCHAR(200), unit_factor_source VARCHAR(100), width_mm DECIMAL(10,2), " +
                "length_mm DECIMAL(12,2), item_order INT, review_status TINYINT)");
        db.execute("CREATE TABLE inventory_material(material_id BIGINT PRIMARY KEY, " +
                "material_code VARCHAR(50), material_name VARCHAR(200), unit VARCHAR(20), unit_alt VARCHAR(20), " +
                "unit_conv DECIMAL(10,4), safe_stock DECIMAL(12,4), width_mm DECIMAL(10,2), " +
                "length_mm DECIMAL(12,2), status TINYINT)");
        db.execute("CREATE TABLE inventory_stock(material_id BIGINT, total_quantity DECIMAL(12,4), " +
                "total_reserved DECIMAL(12,4))");
        db.execute("CREATE TABLE inventory_stock_item(material_id BIGINT, warehouse_id BIGINT, " +
                "quantity DECIMAL(12,4), reserved_quantity DECIMAL(12,4), status TINYINT)");
        db.execute("CREATE TABLE purchase_order(order_id BIGINT PRIMARY KEY, receipt_status TINYINT, " +
                "approval_status TINYINT, expected_delivery_date DATE)");
        db.execute("CREATE TABLE purchase_order_item(item_id BIGINT PRIMARY KEY, order_id BIGINT, " +
                "material_id BIGINT, quantity DECIMAL(12,4), received_quantity DECIMAL(12,4))");
        db.execute("CREATE TABLE production_order(order_id BIGINT PRIMARY KEY, order_type VARCHAR(20), " +
                "order_status TINYINT, product_id BIGINT, bom_id BIGINT, remaining_quantity DECIMAL(18,4), " +
                "plan_end_date DATE, sales_order_id BIGINT, parent_order_id BIGINT)");
    }

    private void seedBase() {
        db.update("INSERT INTO product(product_id,product_code,product_name,unit,current_bom_id) " +
                "VALUES (1,'P1','测试产品','PCS',10)");
        db.update("INSERT INTO engineering_bom(bom_id,bom_code,bom_name,bom_type,product_id,output_type," +
                        "output_id,approve_status,is_current,effective_date) VALUES (10,'B10','产品BOM','engineering'," +
                        "1,'PRODUCT',1,3,1,DATE '2026-01-01')");
        db.update("INSERT INTO engineering_bom(bom_id,bom_code,bom_name,bom_type,product_id,output_type," +
                        "output_id,approve_status,is_current,effective_date) VALUES (20,'B20','半成品BOM','manufacturing'," +
                        "NULL,'MATERIAL',200,3,0,DATE '2026-01-01')");
        material(100L, "RM100", "原料", "PCS");
        material(200L, "SF200", "半成品", "PCS");
        material(300L, "RM300", "下层原料", "PCS");
        db.update("INSERT INTO engineering_bom_item(item_id,bom_id,material_id,material_code,material_name,unit," +
                        "module_qty,base_qty,loss_rate,source_type,item_order,review_status) " +
                        "VALUES (201,20,300,'RM300','下层原料','PCS',1,1,0,'buy',1,3)");
    }

    private void material(Long id, String code, String name, String unit) {
        db.update("INSERT INTO inventory_material(material_id,material_code,material_name,unit,status) " +
                "VALUES (?,?,?,?,1)", id, code, name, unit);
    }

    private void addRootLine(Long itemId, Long materialId, String base, String sourceType, Long subBomId) {
        db.update("INSERT INTO engineering_bom_item(item_id,bom_id,material_id,material_code,material_name,unit," +
                        "module_qty,base_qty,loss_rate,source_type,sub_bom_id,item_order,review_status) " +
                        "VALUES (?,10,?,?,?,?,1,?,0,?,?,?,3)",
                itemId, materialId, "M" + materialId, "物料" + materialId, "PCS",
                new BigDecimal(base), sourceType, subBomId, itemId.intValue());
    }

    private void updateSafeStock(Long materialId, String safeStock) {
        db.update("UPDATE inventory_material SET safe_stock=? WHERE material_id=?",
                new BigDecimal(safeStock), materialId);
    }

    private void purchase(Long orderId, int approvalStatus, int receiptStatus, LocalDate expectedDate) {
        db.update("INSERT INTO purchase_order(order_id,receipt_status,approval_status,expected_delivery_date) " +
                "VALUES (?,?,?,?)", orderId, receiptStatus, approvalStatus, expectedDate);
    }

    private void purchaseItem(Long itemId, Long orderId, Long materialId, String qty, String received) {
        db.update("INSERT INTO purchase_order_item(item_id,order_id,material_id,quantity,received_quantity) " +
                "VALUES (?,?,?,?,?)", itemId, orderId, materialId,
                new BigDecimal(qty), new BigDecimal(received));
    }

    private void production(Long orderId, Long productId, Long bomId, String remaining, int status,
                             LocalDate planEnd, Long salesOrderId, Long parentOrderId) {
        db.update("INSERT INTO production_order(order_id,order_type,order_status,product_id,bom_id," +
                        "remaining_quantity,plan_end_date,sales_order_id,parent_order_id) " +
                        "VALUES (?,'WORK_ORDER',?,?,?,?,?,?,?)",
                orderId, status, productId, bomId, new BigDecimal(remaining), planEnd, salesOrderId, parentOrderId);
    }
}
