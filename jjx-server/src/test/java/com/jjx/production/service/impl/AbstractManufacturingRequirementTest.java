package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.jjx.production.domain.dto.RequirementPreviewRequest;
import com.jjx.production.domain.vo.RequirementLineVO;
import com.jjx.production.domain.vo.RequirementSummaryVO;
import com.jjx.production.mapper.ManufacturingSupplySnapshotMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 供给口径测试基类（dev-20261111-006）：真实 MyBatis Mapper + 真实 ServiceImpl/ProviderImpl，隔离 H2（MySQL 模式）。
 *
 * <p>H2 只提供表结构与 SQL 语义，不冒充真实 MySQL 写入、并发或生产闭环；库存写入类断言只看“没有新增行”。</p>
 */
abstract class AbstractManufacturingRequirementTest {

    static final LocalDate DEMAND_DATE = LocalDate.of(2026, 11, 11);

    JdbcTemplate db;
    ManufacturingRequirementServiceImpl service;
    ManufacturingSupplySnapshotMapper mapper;

    @BeforeEach
    void setUpRequirementFixture() {
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
        mapper = session.getMapper(ManufacturingSupplySnapshotMapper.class);
        service = new ManufacturingRequirementServiceImpl(mapper,
                new ManufacturingSupplySnapshotProviderImpl(mapper),
                new com.jjx.product.service.BomVersionRules(
                        org.mockito.Mockito.mock(com.jjx.product.mapper.EngineeringBomMapper.class)));
    }

    // ==================== 请求与断言工具 ====================

    RequirementSummaryVO preview(Long qty, Long warehouseId) {
        return preview(new BigDecimal(qty), warehouseId, DEMAND_DATE);
    }

    RequirementSummaryVO preview(BigDecimal qty, Long warehouseId, LocalDate demandDate) {
        RequirementPreviewRequest request = new RequirementPreviewRequest();
        request.setProductId(1L);
        request.setQuantity(qty);
        request.setDemandDate(demandDate);
        request.setWarehouseId(warehouseId);
        return service.preview(request);
    }

    RequirementLineVO line(RequirementSummaryVO summary, Long materialId) {
        return summary.getLines().stream()
                .filter(line -> materialId.equals(line.getMaterialId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少物料行 " + materialId));
    }

    int count(String table) {
        Integer value = db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    boolean hasWarning(RequirementLineVO line, String fragment) {
        return line.getWarnings().stream().anyMatch(warning -> warning.contains(fragment));
    }

    boolean hasWarning(RequirementSummaryVO summary, String fragment) {
        return summary.getWarnings().stream().anyMatch(warning -> warning.contains(fragment));
    }

    // ==================== 表结构 ====================

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
        db.execute("CREATE TABLE inventory_warehouse(warehouse_id BIGINT PRIMARY KEY, " +
                "warehouse_code VARCHAR(50), warehouse_name VARCHAR(100), status CHAR(1))");
        db.execute("CREATE TABLE purchase_order(order_id BIGINT PRIMARY KEY, order_no VARCHAR(50), " +
                "supplier_name VARCHAR(200), receipt_status TINYINT, approval_status TINYINT, " +
                "expected_delivery_date DATE)");
        db.execute("CREATE TABLE purchase_order_item(item_id BIGINT PRIMARY KEY, order_id BIGINT, " +
                "material_id BIGINT, quantity DECIMAL(12,4), received_quantity DECIMAL(12,4), unit VARCHAR(20))");
        db.execute("CREATE TABLE production_order(order_id BIGINT PRIMARY KEY, order_no VARCHAR(50), " +
                "order_type VARCHAR(20), order_status TINYINT, approval_status TINYINT, product_id BIGINT, " +
                "product_unit VARCHAR(20), bom_id BIGINT, planned_quantity DECIMAL(18,4), " +
                "completed_quantity DECIMAL(18,4), finished_quantity DECIMAL(18,4), " +
                "remaining_quantity DECIMAL(18,4), plan_end_date DATE, sales_order_id BIGINT, " +
                "parent_order_id BIGINT)");
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
        warehouse(1L, "WH01", "原料仓", "1");
        warehouse(2L, "WH02", "停用仓", "0");
    }

    // ==================== 造数工具 ====================

    void material(Long id, String code, String name, String unit) {
        db.update("INSERT INTO inventory_material(material_id,material_code,material_name,unit,status) " +
                "VALUES (?,?,?,?,1)", id, code, name, unit);
    }

    void warehouse(Long id, String code, String name, String status) {
        db.update("INSERT INTO inventory_warehouse(warehouse_id,warehouse_code,warehouse_name,status) " +
                "VALUES (?,?,?,?)", id, code, name, status);
    }

    void updateSafeStock(Long materialId, String safeStock) {
        db.update("UPDATE inventory_material SET safe_stock=? WHERE material_id=?",
                new BigDecimal(safeStock), materialId);
    }

    void updateUnitConversion(Long materialId, String unitAlt, String unitConv) {
        db.update("UPDATE inventory_material SET unit_alt=?, unit_conv=? WHERE material_id=?",
                unitAlt, unitConv == null ? null : new BigDecimal(unitConv), materialId);
    }

    void updateMaterialDims(Long materialId, String widthMm, String lengthMm) {
        db.update("UPDATE inventory_material SET width_mm=?, length_mm=? WHERE material_id=?",
                widthMm == null ? null : new BigDecimal(widthMm),
                lengthMm == null ? null : new BigDecimal(lengthMm), materialId);
    }

    void addRootLine(Long itemId, Long materialId, String base, String sourceType, Long subBomId) {
        addRootLine(itemId, materialId, "PCS", base, sourceType, subBomId);
    }

    void addRootLine(Long itemId, Long materialId, String unit, String base, String sourceType, Long subBomId) {
        db.update("INSERT INTO engineering_bom_item(item_id,bom_id,material_id,material_code,material_name,unit," +
                        "module_qty,base_qty,loss_rate,source_type,sub_bom_id,item_order,review_status) " +
                        "VALUES (?,10,?,?,?,?,1,?,0,?,?,?,3)",
                itemId, materialId, "M" + materialId, "物料" + materialId, unit,
                new BigDecimal(base), sourceType, subBomId, itemId.intValue());
    }

    void addRootLineWithFactor(Long itemId, Long materialId, String unit, String base, String factor, String basis) {
        db.update("UPDATE engineering_bom_item SET unit=?, unit_factor=?, unit_factor_basis=?, unit_factor_source=? " +
                        "WHERE item_id=?",
                unit, factor == null ? null : new BigDecimal(factor), basis,
                factor == null ? null : "engineering_bom_item.unit_factor", itemId);
    }

    void purchase(Long orderId, String orderNo, int approvalStatus, int receiptStatus, LocalDate expectedDate) {
        db.update("INSERT INTO purchase_order(order_id,order_no,supplier_name,receipt_status,approval_status," +
                "expected_delivery_date) VALUES (?,?,?,?,?,?)",
                orderId, orderNo, "供应商" + orderId, receiptStatus, approvalStatus, expectedDate);
    }

    void purchaseItem(Long itemId, Long orderId, Long materialId, String qty, String received, String unit) {
        db.update("INSERT INTO purchase_order_item(item_id,order_id,material_id,quantity,received_quantity,unit) " +
                "VALUES (?,?,?,?,?,?)", itemId, orderId, materialId,
                new BigDecimal(qty), new BigDecimal(received), unit);
    }

    void production(Long orderId, String orderNo, Long bomId, String planned, String finished, int orderStatus,
                    int approvalStatus, LocalDate planEnd, Long salesOrderId, Long parentOrderId, String productUnit) {
        db.update("INSERT INTO production_order(order_id,order_no,order_type,order_status,approval_status," +
                        "product_id,product_unit,bom_id,planned_quantity,completed_quantity,finished_quantity," +
                        "remaining_quantity,plan_end_date,sales_order_id,parent_order_id) " +
                        "VALUES (?,?,'WORK_ORDER',?,?,NULL,?,?,?,?,?,?,?,?,?)",
                orderId, orderNo, orderStatus, approvalStatus, productUnit, bomId,
                new BigDecimal(planned), new BigDecimal(finished), new BigDecimal(finished),
                new BigDecimal(planned), planEnd, salesOrderId, parentOrderId);
    }

    void updateProductionBom(Long bomId, Integer approveStatus, LocalDate effectiveDate, LocalDate expiryDate) {
        db.update("UPDATE engineering_bom SET approve_status=?, effective_date=?, expiry_date=? WHERE bom_id=?",
                approveStatus, effectiveDate, expiryDate, bomId);
    }
}
