package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.InboundTypeEnum;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryAlertService;
import com.jjx.inventory.service.impl.InventoryInboundServiceImpl;
import com.jjx.inventory.service.impl.InventoryStockMutationServiceImpl;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesSampleOrder;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesSampleOrderMapper;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 独立 H2 内存库、真实 Mapper/事务/库存变动链路；绝不连接业务库。 */
class SampleReturnInboundTest {
    private InventoryInboundServiceImpl service;
    private InventoryInboundOrderMapper orders;
    private InventoryInboundItemMapper items;
    private InventoryOutboundOrderMapper outboundOrders;
    private InventoryOutboundItemMapper outboundItems;
    private InventoryStockItemMapper stocks;
    private InventoryTransactionMapper transactions;
    private InventoryStockMapper summaries;
    private JdbcTemplate jdbc;
    private InventoryStockItem batch;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(source);
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(InventoryInboundOrderMapper.class, InventoryInboundItemMapper.class,
                InventoryOutboundOrderMapper.class, InventoryOutboundItemMapper.class, InventoryStockItemMapper.class,
                InventoryTransactionMapper.class, OrderMapper.class, SalesSampleOrderMapper.class)) config.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        factory.setGlobalConfig(new GlobalConfig().setBanner(false));
        var session = new SqlSessionTemplate(factory.getObject());
        for (var entity : List.of(InventoryInboundOrder.class, InventoryInboundItem.class,
                InventoryOutboundOrder.class, InventoryOutboundItem.class, InventoryStockItem.class,
                InventoryTransaction.class, SalesOrder.class, SalesSampleOrder.class)) createTable(entity);
        orders = session.getMapper(InventoryInboundOrderMapper.class);
        items = session.getMapper(InventoryInboundItemMapper.class);
        outboundOrders = session.getMapper(InventoryOutboundOrderMapper.class);
        outboundItems = session.getMapper(InventoryOutboundItemMapper.class);
        stocks = session.getMapper(InventoryStockItemMapper.class);
        transactions = session.getMapper(InventoryTransactionMapper.class);
        summaries = mock(InventoryStockMapper.class);
        // 构造器未用到的依赖留空，运行本任务及原返工退料所需的依赖单独注入。
        var constructor = InventoryInboundServiceImpl.class.getConstructors()[0];
        var target = (InventoryInboundServiceImpl) constructor.newInstance(new Object[constructor.getParameterCount()]);
        Map<String, Object> dependencies = new HashMap<>();
        dependencies.put("inboundOrderMapper", orders);
        dependencies.put("inboundItemMapper", items);
        dependencies.put("outboundOrderMapper", outboundOrders);
        dependencies.put("outboundItemMapper", outboundItems);
        dependencies.put("stockItemMapper", stocks);
        dependencies.put("stockMapper", summaries);
        dependencies.put("transactionMapper", transactions);
        dependencies.put("stockMutationService", new InventoryStockMutationServiceImpl(stocks, summaries, transactions));
        dependencies.put("salesOrderMapper", session.getMapper(OrderMapper.class));
        dependencies.put("sampleOrderMapper", session.getMapper(SalesSampleOrderMapper.class));
        dependencies.put("alertService", mock(InventoryAlertService.class));
        dependencies.put("eventPublisher", mock(EventPublisher.class));
        dependencies.put("warehouseMapper", mock(InventoryWarehouseMapper.class));
        var ncrMapper = mock(com.jjx.quality.mapper.QualityNcrMapper.class);
        var ncr = new com.jjx.quality.domain.entity.QualityNcr();
        ncr.setNcrNo("NCR-1");
        when(ncrMapper.selectById(7L)).thenReturn(ncr);
        dependencies.put("qualityNcrMapper", ncrMapper);
        var production = mock(com.jjx.production.mapper.ProductionOrderMapper.class);
        var workOrder = new com.jjx.production.domain.entity.ProductionOrder();
        workOrder.setOrderNo("WO-1");
        when(production.selectById(8L)).thenReturn(workOrder);
        dependencies.put("productionOrderMapper", production);
        dependencies.forEach((name, value) -> ReflectionTestUtils.setField(target, name, value));
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        service = (InventoryInboundServiceImpl) proxy.getProxy();
        jdbc.update("INSERT INTO sales_order(order_id,order_no,deleted) VALUES (1,'SP-1',0)");
        jdbc.update("INSERT INTO sales_sample_order(order_id,deleted) VALUES (1,0)");
        batch = new InventoryStockItem();
        batch.setInventoryItemId(31L); batch.setMaterialId(11L); batch.setMaterialCode("MAT-1");
        batch.setMaterialName("原料"); batch.setBatchNo("ORIGINAL-BATCH"); batch.setWarehouseId(2L);
        batch.setLocationId(3L); batch.setStatus(1); batch.setQuantity(new BigDecimal("10"));
        batch.setReservedQuantity(new BigDecimal("4")); batch.setUnitCost(new BigDecimal("2"));
        stocks.insert(batch);
        issue("5", "ORIGINAL-BATCH", InventoryOrderStatusEnum.COMPLETED, "sample", 3L);
    }

    @Test
    void returnsTwoToOriginalBatchAndPreviewFallsFromFiveToThree() {
        assertQty("5", preview().get(0).get("returnableQty"));
        var id = submit("2");
        var order = orders.selectById(id);
        assertEquals("SRT-SP-1-1", order.getInboundNo());
        assertEquals(InboundTypeEnum.SAMPLE_RETURN.getCode(), order.getInboundType());
        assertEquals(InventoryOrderStatusEnum.COMPLETED.getValue(), order.getOrderStatus());
        var item = items.selectByInboundId(id).get(0);
        assertEquals("ORIGINAL-BATCH", item.getBatchNo());
        assertEquals(3L, item.getLocationId());
        assertQty("2", item.getPostedQuantity());
        var stock = stocks.selectById(batch.getItemId());
        assertQty("12", stock.getQuantity());
        assertQty("8", stock.getQuantity().subtract(stock.getReservedQuantity()));
        verify(summaries).refreshSummaryByInventoryItemId(31L);
        var tx = transactions.selectList(null).get(0);
        assertEquals("INBOUND", tx.getTransactionType());
        assertEquals("sample_return", tx.getSourceType());
        assertEquals(id, tx.getSourceId());
        assertEquals("SRT-SP-1-1", tx.getSourceNo());
        assertEquals("ORIGINAL-BATCH", tx.getBatchNo());
        assertQty("10", tx.getBeforeQuantity()); assertQty("12", tx.getAfterQuantity());
        assertQty("2", tx.getQuantity());
        assertQty("3", preview().get(0).get("returnableQty"));
        submit("3");
        assertQty("0", preview().get(0).get("returnableQty"));
        assertEquals("SRT-SP-1-2", orders.selectList(null).get(1).getInboundNo());
    }

    @Test
    void overReturnAndDuplicateRowsFailBeforeAnythingIsInserted() {
        assertThrows(BusinessException.class, () -> submit("6"));
        var key = String.valueOf(preview().get(0).get("returnKey"));
        assertThrows(BusinessException.class, () -> service.createSampleReturnInbound(1L,
                List.of(request(key, "3"), request(key, "3")), null));
        assertEquals(0, orders.selectCount(null));
        assertEquals(0, items.selectCount(null));
        assertEquals(0, transactions.selectCount(null));
        assertQty("10", stocks.selectById(batch.getItemId()).getQuantity());
    }

    @Test
    void draftCancelledAndOtherSourcesNeverEnterPreview() {
        issue("30", "DRAFT", InventoryOrderStatusEnum.PENDING, "sample", 3L);
        issue("40", "CANCELLED", InventoryOrderStatusEnum.CANCELLED, "sample", 3L);
        issue("50", "OTHER", InventoryOrderStatusEnum.COMPLETED, "work_order", 3L);
        var rows = preview();
        assertEquals(1, rows.size());
        assertEquals("ORIGINAL-BATCH", rows.get(0).get("batchNo"));
        assertQty("5", rows.get(0).get("issuedQty"));
        assertThrows(BusinessException.class, () -> service.createSampleReturnInbound(1L,
                List.of(request("99999", "1")), null));
    }

    @Test
    void returnsAreAccountedByBatchAndLocationAndClientCannotReplaceIdentity() {
        issue("7", "OTHER-BATCH", InventoryOrderStatusEnum.COMPLETED, "sample", 3L);
        issue("9", "ORIGINAL-BATCH", InventoryOrderStatusEnum.COMPLETED, "sample", 4L);
        var request = new HashMap<String, Object>(request(String.valueOf(preview().get(0).get("returnKey")), "2"));
        request.put("batchNo", "FORGED"); request.put("inventoryItemId", 9999L); request.put("locationId", 999L);
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(9L);
            security.when(SecurityUtils::getDisplayName).thenReturn("测试人");
            service.createSampleReturnInbound(1L, List.of(request), null);
        }
        var rows = preview();
        assertQty("3", rows.get(0).get("returnableQty"));
        assertQty("7", rows.get(1).get("returnableQty"));
        assertQty("9", rows.get(2).get("returnableQty"));
        assertEquals("ORIGINAL-BATCH", items.selectList(null).get(0).getBatchNo());
        assertEquals(31L, items.selectList(null).get(0).getInventoryItemId());
    }

    @Test
    void postingFailureRollsBackDocumentItemsStockAndTransactions() {
        doThrow(new BusinessException("模拟汇总失败")).when(summaries).refreshSummaryByInventoryItemId(31L);
        assertThrows(BusinessException.class, () -> submit("2"));
        assertEquals(0, orders.selectCount(null)); assertEquals(0, items.selectCount(null));
        assertEquals(0, transactions.selectCount(null));
        assertQty("10", stocks.selectById(batch.getItemId()).getQuantity());
    }

    @Test
    void concurrentReturnsCannotExceedIssuedQuantity() throws Exception {
        var key = String.valueOf(preview().get(0).get("returnKey"));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CyclicBarrier(2);
            Callable<Boolean> task = () -> {
                start.await(5, TimeUnit.SECONDS);
                try (var security = mockStatic(SecurityUtils.class)) {
                    security.when(SecurityUtils::getUserId).thenReturn(9L);
                    security.when(SecurityUtils::getDisplayName).thenReturn("测试人");
                    try {
                        service.createSampleReturnInbound(1L, List.of(request(key, "4")), null);
                        return true;
                    } catch (BusinessException e) { return false; }
                }
            };
            var results = pool.invokeAll(List.of(task, task));
            assertEquals(1, results.stream().filter(result -> {
                try { return result.get(); } catch (Exception e) { throw new AssertionError(e); }
            }).count());
        }
        assertEquals(1, orders.selectCount(null));
        assertQty("1", preview().get(0).get("returnableQty"));
    }

    @Test
    void existingProductionReturnStillApprovesAndPostsToOriginalBatch() {
        jdbc.update("UPDATE inventory_outbound_order SET source_type='work_order', supplement_ncr_id=7");
        assertQty("5", service.returnPreview(7L).get(0).get("returnableQty"));
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(9L);
            var id = service.createProductionReturnInbound(8L, 7L, List.of(Map.of(
                    "materialId", 11L, "inventoryItemId", 31L, "materialCode", "MAT-1",
                    "materialName", "原料", "batchNo", "ORIGINAL-BATCH", "quantity", "2")), null);
            assertEquals("RTN-WO-1-NCR-1-1", orders.selectById(id).getInboundNo());
            assertEquals(InventoryOrderStatusEnum.COMPLETED.getValue(), orders.selectById(id).getOrderStatus());
        }
        assertQty("12", stocks.selectById(batch.getItemId()).getQuantity());
        assertQty("3", service.returnPreview(7L).get(0).get("returnableQty"));
    }

    @Test
    void warehouseAndNullLocationBothResolveToTheOriginalStockRow() {
        var other = new InventoryStockItem();
        other.setInventoryItemId(31L); other.setMaterialId(11L); other.setBatchNo("ORIGINAL-BATCH");
        other.setWarehouseId(9L); other.setLocationId(3L); other.setStatus(1);
        other.setQuantity(new BigDecimal("100")); stocks.insert(other);
        submit("2");
        assertQty("12", stocks.selectById(batch.getItemId()).getQuantity());
        assertQty("100", stocks.selectById(other.getItemId()).getQuantity());
        other.setItemId(null); other.setWarehouseId(2L); other.setLocationId(null);
        other.setQuantity(new BigDecimal("20")); stocks.insert(other);
        issue("5", "ORIGINAL-BATCH", InventoryOrderStatusEnum.COMPLETED, "sample", null);
        var key = preview().stream().filter(row -> row.get("locationId") == null)
                .findFirst().orElseThrow().get("returnKey").toString();
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(9L);
            service.createSampleReturnInbound(1L, List.of(request(key, "1")), null);
        }
        assertQty("21", stocks.selectById(other.getItemId()).getQuantity());
        assertQty("12", stocks.selectById(batch.getItemId()).getQuantity());
    }

    @Test
    void bothEndpointsRequireTheExistingInboundAddPermission() throws Exception {
        var controller = com.jjx.inventory.controller.InventoryInboundController.class;
        for (var method : List.of(controller.getMethod("sampleReturnPreview", Long.class),
                controller.getMethod("sampleReturn", Long.class, String.class, List.class))) {
            assertArrayEquals(new String[]{"inventory:inbound:add"},
                    method.getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class).value());
        }
    }

    @Test
    void repeatedIssuesOfTheSameBatchAggregateAndKeepThePreviewKey() {
        var originalKey = preview().get(0).get("returnKey");
        issue("3", "ORIGINAL-BATCH", InventoryOrderStatusEnum.COMPLETED, "sample", 3L);
        assertEquals(1, preview().size());
        assertEquals(originalKey, preview().get(0).get("returnKey"));
        assertQty("8", preview().get(0).get("returnableQty"));
        submit("4");
        assertQty("4", preview().get(0).get("returnableQty"));
    }

    @Test
    void invalidQuantitiesAndEmptySelectionDoNotCreateDocuments() {
        for (var qty : List.of("-1", "0", "0.00001", "NaN", "null")) {
            assertThrows(BusinessException.class, () -> submit(qty));
        }
        assertEquals(0, orders.selectCount(null));
    }

    private Long submit(String quantity) {
        var key = String.valueOf(preview().get(0).get("returnKey"));
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(9L);
            security.when(SecurityUtils::getDisplayName).thenReturn("测试人");
            return service.createSampleReturnInbound(1L, List.of(request(key, quantity)), "余料");
        }
    }
    private List<Map<String, Object>> preview() { return service.sampleReturnPreview(1L); }
    private static Map<String, Object> request(String key, String quantity) { return Map.of("returnKey", key, "quantity", quantity); }
    private static void assertQty(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual));
    }
    private void issue(String qty, String batchNo, InventoryOrderStatusEnum status, String source, Long location) {
        var order = new InventoryOutboundOrder();
        order.setSourceType(source); order.setSourceId(1L); order.setSourceNo("SP-1");
        order.setOutboundType("sample"); order.setWarehouseId(2L); order.setOrderStatus(status.getValue());
        outboundOrders.insert(order);
        var item = new InventoryOutboundItem();
        item.setOutboundId(order.getOutboundId()); item.setInventoryItemId(31L); item.setMaterialId(11L);
        item.setMaterialCode("MAT-1"); item.setMaterialName("原料"); item.setBatchNo(batchNo);
        item.setLocationId(location); item.setQuantity(new BigDecimal(qty)); item.setUnitPrice(new BigDecimal("2"));
        outboundItems.insert(item);
    }
    private void createTable(Class<?> entity) {
        var info = TableInfoHelper.getTableInfo(entity);
        var columns = new ArrayList<String>();
        columns.add(info.getKeyColumn() + " BIGINT AUTO_INCREMENT PRIMARY KEY");
        for (var field : info.getFieldList()) {
            var type = field.getPropertyType();
            String sqlType = type == String.class ? "VARCHAR(2048)" : type == BigDecimal.class ? "DECIMAL(18,4)"
                    : type == Long.class || type == long.class ? "BIGINT"
                    : type == Integer.class || type == int.class || type == Boolean.class ? "INT" : "TIMESTAMP";
            columns.add(field.getColumn() + " " + sqlType + (field.getColumn().equals("deleted") ? " DEFAULT 0" : ""));
        }
        jdbc.execute("CREATE TABLE " + info.getTableName() + " (" + String.join(",", columns) + ")");
    }
}
