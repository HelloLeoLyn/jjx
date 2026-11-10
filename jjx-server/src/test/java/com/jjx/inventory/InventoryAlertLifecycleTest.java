package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 隔离 H2 + 真实 Mapper/事务/库存入口，不写业务库。 */
class InventoryAlertLifecycleTest {
    org.mockito.MockedStatic<SecurityUtils> security;
    @AfterEach void cleanup() { if (security != null) security.close(); }
    JdbcTemplate jdbc;
    InventoryInboundServiceImpl inbound;
    InventoryAlertServiceImpl alerts;
    InventoryInboundOrderMapper orders;
    InventoryInboundItemMapper items;
    InventoryStockItemMapper stocks;
    InventoryTransactionMapper transactions;
    InventoryStockMapper summaries;
    InventoryAlertLogMapper logs;
    DataSourceTransactionManager manager;
    InventoryInboundOrder order;

    @BeforeEach void setup() throws Exception {
        security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(1L);
        security.when(SecurityUtils::getUsername).thenReturn("test");
        security.when(SecurityUtils::getDisplayName).thenReturn("test");
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds); manager = new DataSourceTransactionManager(ds);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(InventoryInboundOrderMapper.class, InventoryInboundItemMapper.class,
                InventoryStockItemMapper.class, InventoryTransactionMapper.class, InventoryAlertLogMapper.class)) config.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setConfiguration(config); factory.setDataSource(ds);
        var session = new SqlSessionTemplate(factory.getObject());
        for (var entity : List.of(InventoryInboundOrder.class, InventoryInboundItem.class,
                InventoryStockItem.class, InventoryTransaction.class, InventoryAlertLog.class)) createTable(entity);
        orders = session.getMapper(InventoryInboundOrderMapper.class); items = session.getMapper(InventoryInboundItemMapper.class);
        stocks = session.getMapper(InventoryStockItemMapper.class); transactions = session.getMapper(InventoryTransactionMapper.class);
        logs = session.getMapper(InventoryAlertLogMapper.class); summaries = mock(InventoryStockMapper.class);
        inbound = construct(InventoryInboundServiceImpl.class);
        Map<String,Object> dependencies = Map.of("inboundOrderMapper",orders,"inboundItemMapper",items,
                "stockItemMapper",stocks,"transactionMapper",transactions,"stockMapper",summaries,
                "alertLogMapper",logs,"transactionManager",manager,
                "stockMutationService",new InventoryStockMutationServiceImpl(stocks,summaries,transactions));
        dependencies.forEach((k,v) -> ReflectionTestUtils.setField(inbound,k,v));
        var proxy = new ProxyFactory(inbound); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        inbound = (InventoryInboundServiceImpl) proxy.getProxy();
        alerts = construct(InventoryAlertServiceImpl.class); ReflectionTestUtils.setField(alerts,"alertLogMapper",logs);
        order = new InventoryInboundOrder(); order.setInboundNo("IN-ALERT-TEST"); order.setSourceType("MANUAL");
        order.setInboundType("other"); order.setWarehouseId(1L); order.setOrderStatus(InventoryOrderStatusEnum.DRAFT.getValue()); orders.insert(order);
        var item = new InventoryInboundItem(); item.setInboundId(order.getInboundId()); item.setInventoryItemId(31L);
        item.setMaterialId(11L); item.setMaterialCode("MAT-11"); item.setMaterialName("原料"); item.setBatchNo("BATCH-ALERT");
        item.setQuantity(new BigDecimal("2")); item.setUnitPrice(BigDecimal.ONE); items.insert(item);
        seed(1,11,0); seed(2,11,2); seed(3,11,3); seed(4,12,0);
        jdbc.update("UPDATE inventory_alert_log SET process_remark='既有备注' WHERE alert_id=2");
    }
    @Test void commitResolvesOnlyInboundMaterialAndPreservesStockResult() {
        assertTrue(inbound.confirm(order.getInboundId(),1L,"test"));
        assertEquals(InventoryOrderStatusEnum.COMPLETED.getValue(),orders.selectById(order.getInboundId()).getOrderStatus());
        assertEquals(0,new BigDecimal("2").compareTo(stocks.selectList(null).get(0).getQuantity()));
        assertEquals(1,transactions.selectCount(null));
        assertEquals(3,logs.selectById(1L).getStatus()); assertEquals(3,logs.selectById(2L).getStatus());
        assertTrue(logs.selectById(2L).getProcessRemark().startsWith("既有备注；"));
        assertTrue(logs.selectById(1L).getProcessRemark().contains("IN-ALERT-TEST"));
        assertNull(logs.selectById(3L).getProcessRemark()); assertEquals(0,logs.selectById(4L).getStatus());
    }
    @Test void postingFailureRollsBackAndNeverResolvesAlerts() {
        doThrow(new IllegalStateException("模拟入库失败")).when(summaries).refreshSummaryByInventoryItemId(31L);
        assertThrows(IllegalStateException.class,() -> inbound.confirm(order.getInboundId(),1L,"test"));
        assertEquals(InventoryOrderStatusEnum.DRAFT.getValue(),orders.selectById(order.getInboundId()).getOrderStatus());
        assertEquals(0,stocks.selectCount(null)); assertEquals(0,transactions.selectCount(null));
        assertEquals(0,logs.selectById(1L).getStatus()); assertEquals(2,logs.selectById(2L).getStatus());
    }
    @Test void laterOuterRollbackNeverRunsResolution() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            assertTrue(inbound.confirm(order.getInboundId(),1L,"test"));
            assertEquals(0,logs.selectById(1L).getStatus()); tx.setRollbackOnly();
        });
        assertEquals(InventoryOrderStatusEnum.DRAFT.getValue(),orders.selectById(order.getInboundId()).getOrderStatus());
        assertEquals(0,logs.selectById(1L).getStatus()); assertEquals(0,transactions.selectCount(null));
    }
    @Test void resolutionSqlFailureCannotUndoSuccessfulInbound() {
        jdbc.execute("ALTER TABLE inventory_alert_log ADD CONSTRAINT deny_resolve CHECK (alert_id <> 1 OR status <> 3)");
        assertTrue(inbound.confirm(order.getInboundId(),1L,"test"));
        assertEquals(InventoryOrderStatusEnum.COMPLETED.getValue(),orders.selectById(order.getInboundId()).getOrderStatus());
        assertEquals(1,transactions.selectCount(null));
        assertEquals(0,logs.selectById(1L).getStatus()); assertEquals(2,logs.selectById(2L).getStatus());
    }
    @Test void purchaseBackwritePersistsOrderNumberAndReturnsThreeStateLabels() {
        assertTrue(alerts.batchProcessAlert(List.of(1L, 3L), List.of(), "PO-STRUCTURED", "采购回写"));
        var linked = logs.selectById(1L);
        assertEquals(2, linked.getStatus());
        assertEquals("PO-STRUCTURED", linked.getRelatedOrderNo());
        assertTrue(linked.getProcessRemark().contains("生成采购订单 PO-STRUCTURED"));
        assertEquals(3, logs.selectById(3L).getStatus());
        assertNull(logs.selectById(3L).getRelatedOrderNo());
        var result = alerts.page(new com.jjx.inventory.dto.query.AlertQueryDTO()).getRecords();
        var vo = result.stream().filter(row -> row.getAlertId().equals(1L)).findFirst().orElseThrow();
        assertEquals("PO-STRUCTURED", vo.getRelatedOrderNo());
        assertEquals("已转采购单", vo.getStatusName());
        assertTrue(result.stream().anyMatch(row -> "未处理".equals(row.getStatusName())));
        assertTrue(result.stream().anyMatch(row -> "已解除".equals(row.getStatusName())));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_alert_log WHERE status=1", Integer.class));
    }
    @Test void legacyReportingServiceContractNeverWritesReportedState() {
        assertTrue(alerts.markRead(1L)); assertTrue(alerts.batchMarkRead(List.of(1L,2L)));
        assertEquals(0,logs.selectById(1L).getStatus()); assertEquals(2,logs.selectById(2L).getStatus());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_alert_log WHERE status=1",Integer.class));
    }
    private void seed(long id,long materialId,int status) {
        jdbc.update("INSERT INTO inventory_alert_log(alert_id,material_id,status) VALUES(?,?,?)",id,materialId,status);
    }
    private static <T> T construct(Class<T> type) throws Exception {
        var c = type.getConstructors()[0];
        return type.cast(c.newInstance(Arrays.stream(c.getParameterTypes()).map(t -> mock(t)).toArray()));
    }
    private void createTable(Class<?> entity) {
        var info = TableInfoHelper.getTableInfo(entity); var columns = new ArrayList<String>();
        columns.add(info.getKeyColumn()+" BIGINT AUTO_INCREMENT PRIMARY KEY");
        for (var field : info.getFieldList()) {
            var type = field.getPropertyType();
            String sqlType = type==String.class ? "VARCHAR(2048)" : type==BigDecimal.class ? "DECIMAL(18,4)"
                    : type==Long.class || type==long.class ? "BIGINT"
                    : type==Integer.class || type==int.class || type==Boolean.class ? "INT" : "TIMESTAMP";
            columns.add(field.getColumn()+" "+sqlType+(field.getColumn().equals("deleted") ? " DEFAULT 0" : ""));
        }
        jdbc.execute("CREATE TABLE "+info.getTableName()+" ("+String.join(",",columns)+")");
    }
}
