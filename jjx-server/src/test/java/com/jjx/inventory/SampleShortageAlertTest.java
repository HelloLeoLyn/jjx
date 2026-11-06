package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.mapper.InventoryAlertLogMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.service.impl.InventoryAlertServiceImpl;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesSampleBom;
import com.jjx.sales.domain.entity.SalesSampleOrder;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesSampleBomMapper;
import com.jjx.sales.mapper.SalesSampleOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 MyBatis + Spring 事务 + 隔离 H2；不改业务库，不替代运行态验收。 */
@ExtendWith(MockitoExtension.class)
class SampleShortageAlertTest {
    @Mock OrderMapper orderMapper;
    @Mock SalesSampleOrderMapper sampleOrderMapper;
    @Mock InventoryMaterialMapper materialMapper;
    @Mock InventoryStockMapper stockMapper;
    @InjectMocks InventoryAlertServiceImpl target;
    InventoryAlertServiceImpl service;
    SalesSampleBomMapper boms;
    JdbcTemplate jdbc;
    SalesSampleOrder sample;

    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:sample_alert_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE inventory_alert_log (alert_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "alert_type VARCHAR(50),order_no VARCHAR(100),involved_orders INT,alert_level VARCHAR(50),"
                + "material_id BIGINT NOT NULL,material_code VARCHAR(100) NOT NULL,material_name VARCHAR(100) NOT NULL,current_stock DECIMAL(20,4),"
                + "safe_stock DECIMAL(20,4),max_stock DECIMAL(20,4),expiry_date DATE,last_outbound_date DATE,"
                + "alert_message VARCHAR(1000),alert_time TIMESTAMP,status INT,reported_by VARCHAR(100),reported_time TIMESTAMP,"
                + "processed_by VARCHAR(100),processed_time TIMESTAMP,process_remark VARCHAR(500),suggestion VARCHAR(500),"
                + "create_by VARCHAR(100),create_time TIMESTAMP,update_by VARCHAR(100),update_time TIMESTAMP,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sales_sample_bom (bom_id BIGINT AUTO_INCREMENT PRIMARY KEY,order_id BIGINT,round_no INT,"
                + "layer_name VARCHAR(100),material_name VARCHAR(100),specification VARCHAR(100),quantity DECIMAL(20,4),"
                + "unit VARCHAR(20),material_id BIGINT,material_code VARCHAR(100),project_ids VARCHAR(100),module_qty DECIMAL(20,4),"
                + "split VARCHAR(100),base_qty DECIMAL(20,4),remark VARCHAR(100),create_by VARCHAR(100),create_time TIMESTAMP)");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(InventoryAlertLogMapper.class);
        config.addMapper(SalesSampleBomMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setConfiguration(config);
        factory.setDataSource(ds);
        var session = new SqlSessionTemplate(factory.getObject());
        boms = session.getMapper(SalesSampleBomMapper.class);
        ReflectionTestUtils.setField(target, "sampleBomMapper", boms);
        ReflectionTestUtils.setField(target, "alertLogMapper", session.getMapper(InventoryAlertLogMapper.class));
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (InventoryAlertServiceImpl) proxy.getProxy();
        sample = new SalesSampleOrder();
        sample.setOrderId(7L);
        sample.setSampleRound(2);
        sample.setSampleQty(2);
    }

    void source() {
        var order = new SalesOrder();
        order.setOrderId(7L);
        order.setOrderNo("SP7");
        when(orderMapper.selectByIdForUpdate(7L)).thenReturn(order);
        when(sampleOrderMapper.selectByOrderId(7L)).thenReturn(sample);
    }

    void bom(Long orderId, int round, Long materialId, String base, String module, String quantity) {
        var row = new SalesSampleBom();
        row.setOrderId(orderId);
        row.setRoundNo(round);
        row.setMaterialId(materialId);
        row.setMaterialCode("M" + materialId);
        row.setMaterialName("材料" + materialId);
        row.setBaseQty(base == null ? null : new BigDecimal(base));
        row.setModuleQty(module == null ? null : new BigDecimal(module));
        row.setQuantity(new BigDecimal(quantity));
        boms.insert(row);
    }

    void material(Long id, String available) {
        var material = new InventoryMaterial();
        material.setMaterialId(id);
        material.setMaterialCode("M" + id);
        when(materialMapper.selectById(id)).thenReturn(material);
        var stock = new InventoryStock();
        stock.setAvailableQuantity(new BigDecimal(available));
        when(stockMapper.selectByMaterialId(id)).thenReturn(stock);
    }

    void seed(String type, String orderNo, int status) {
        jdbc.update("INSERT INTO inventory_alert_log(alert_type,order_no,status,material_id,material_code,material_name) VALUES(?,?,?,11,'M11','材料11')",
                type, orderNo, status);
    }

    int active() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_alert_log WHERE alert_type='sample_shortage' "
                + "AND order_no='SP7' AND status IN (0,1)", Integer.class);
    }

    @Test void repeatedRefreshInvalidatesOldAlertsAndReplenishmentClearsOnlyThisSample() {
        source();
        bom(7L, 2, 11L, "1", "1", "99");
        bom(7L, 1, 12L, "99", "1", "99"); // 旧轮不参与
        bom(8L, 2, 12L, "99", "1", "99"); // 其他单不参与
        material(11L, "0");
        seed("order_shortage", "SP7", 0);
        seed("sample_shortage", "SP8", 0);
        seed("sample_shortage", "SP7", 2);
        seed("sample_shortage", "SP7", 3);
        service.checkSampleShortage(7L);
        assertEquals(1, active());
        var alert = jdbc.queryForMap("SELECT * FROM inventory_alert_log WHERE order_no='SP7' AND alert_type='sample_shortage' AND status=0");
        assertEquals("M11", alert.get("material_code"));
        assertEquals("材料11", alert.get("material_name"));
        assertTrue(alert.get("alert_message").toString().contains("需求2 可用0 缺口2"));
        assertEquals("建议补货 2", alert.get("suggestion"));
        jdbc.update("UPDATE inventory_alert_log SET status=1 WHERE alert_type='sample_shortage' AND order_no='SP7' AND status=0");
        service.checkSampleShortage(7L);
        assertEquals(1, active());
        material(11L, "2");
        service.checkSampleShortage(7L);
        assertEquals(0, active());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_alert_log WHERE alert_type='order_shortage' AND status=0", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_alert_log WHERE order_no='SP8' AND status=0", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_alert_log WHERE order_no='SP7' AND status=2", Integer.class));
        verify(materialMapper, never()).selectById(12L);
        verify(orderMapper, times(3)).selectByIdForUpdate(7L);
    }

    @ParameterizedTest
    @CsvSource({"3,4,99,0,2", "1,4,99,0,1", "1,3,99,0,1", ",4,0.6,1,1", "0,4,0.6,0,2", "-1,4,0.6,0,2"})
    void demandUsesExistingQuantityRuleWithoutInTransit(String base, String module, String qty, String available, String gap) {
        source();
        bom(7L, 2, 11L, base, module, qty);
        material(11L, available);
        service.checkSampleShortage(7L);
        assertEquals(1, active());
        assertEquals("建议补货 " + gap, jdbc.queryForObject("SELECT suggestion FROM inventory_alert_log", String.class));
    }

    @Test void failedRebuildRollsBackInvalidationAndPartialNewAlerts() {
        source();
        bom(7L, 2, 11L, "1", "1", "1");
        bom(7L, 2, 12L, "1", "1", "1");
        material(11L, "0");
        material(12L, "0");
        seed("sample_shortage", "SP7", 1);
        jdbc.execute("ALTER TABLE inventory_alert_log ADD CONSTRAINT reject_material CHECK (material_id <> 12)");
        assertThrows(RuntimeException.class, () -> service.checkSampleShortage(7L));
        assertEquals(1, active());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_alert_log", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM inventory_alert_log", Integer.class));
    }

    @Test void listLookupOnlyReturnsUnprocessedSampleAlertsOnRequestedPage() {
        seed("sample_shortage", "SP7", 1);
        seed("sample_shortage", "SP8", 2);
        seed("sample_shortage", "SP9", 0);
        seed("order_shortage", "SP8", 0);
        assertEquals(java.util.Set.of("SP7"), service.sampleShortageOrderNos(List.of("SP7", "SP8")));
        assertEquals(java.util.Set.of(), service.sampleShortageOrderNos(List.of()));
    }

    @Test void unmatchedHistoricalMaterialDoesNotBreakMatchedMaterialAlerts() {
        source();
        bom(7L, 2, 11L, "1", "1", "1");
        bom(7L, 2, null, "1", "1", "1");
        material(11L, "0");
        service.checkSampleShortage(7L);
        assertEquals(1, active());
    }

    @Test void emptyCurrentBomInvalidatesPreviousAlertsWithoutNewOnes() {
        source();
        seed("sample_shortage", "SP7", 0);
        service.checkSampleShortage(7L);
        assertEquals(0, active());
        verifyNoInteractions(stockMapper, materialMapper);
    }
}
