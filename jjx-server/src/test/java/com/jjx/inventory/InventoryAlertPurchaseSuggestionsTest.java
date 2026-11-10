package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.mapper.InventoryAlertLogMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.purchase.mapper.PurchaseOrderItemMapper;
import com.jjx.inventory.service.impl.InventoryAlertServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 MyBatis + 隔离 H2 验证建议查询，不写业务库。 */
@ExtendWith(MockitoExtension.class)
class InventoryAlertPurchaseSuggestionsTest {
    @Mock InventoryMaterialMapper materialMapper;
    @Mock InventoryStockMapper stockMapper;
    @Mock PurchaseOrderItemMapper purchaseOrderItemMapper;
    @InjectMocks InventoryAlertServiceImpl service;
    JdbcTemplate jdbc;
    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:suggestions_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE inventory_alert_log (alert_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "alert_type VARCHAR(50),order_no VARCHAR(100),related_order_no VARCHAR(50),involved_orders INT,alert_level VARCHAR(50),"
                + "material_id BIGINT NOT NULL,material_code VARCHAR(100) NOT NULL,material_name VARCHAR(100) NOT NULL,current_stock DECIMAL(20,4),"
                + "safe_stock DECIMAL(20,4),max_stock DECIMAL(20,4),expiry_date DATE,last_outbound_date DATE,"
                + "alert_message VARCHAR(1000),alert_time TIMESTAMP,status INT,reported_by VARCHAR(100),reported_time TIMESTAMP,"
                + "processed_by VARCHAR(100),processed_time TIMESTAMP,process_remark VARCHAR(500),suggestion VARCHAR(500),"
                + "create_by VARCHAR(100),create_time TIMESTAMP,update_by VARCHAR(100),update_time TIMESTAMP,remark VARCHAR(500))");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(InventoryAlertLogMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setConfiguration(config);
        factory.setDataSource(ds);
        var session = new SqlSessionTemplate(factory.getObject());
        ReflectionTestUtils.setField(service, "alertLogMapper", session.getMapper(InventoryAlertLogMapper.class));
        when(stockMapper.selectLowStock()).thenReturn(List.of());
    }
    void seed(long id, long mid, String code, String type, String order, int status, String suggestion) {
        jdbc.update("INSERT INTO inventory_alert_log(alert_id,material_id,material_code,material_name,"
                + "alert_type,order_no,status,suggestion,current_stock) VALUES(?,?,?,?,?,?,?,?,0)",
                id, mid, code, code, type, order, status, suggestion);
    }
    @Test void sampleShortagesReachSuggestionsWithExactGapAndSource() {
        seed(1, 109, "AUX000109", "sample_shortage", "SP261107001", 0, "建议补货 1");
        seed(2, 105, "RM000105", "sample_shortage", "SP261107001", 0, "建议补货 1");
        var rows = service.generatePurchaseSuggestions();
        assertEquals(2, rows.size());
        assertEquals(java.util.Set.of("AUX000109", "RM000105"), rows.stream()
                .map(r -> r.get("materialCode")).collect(java.util.stream.Collectors.toSet()));
        for (var row : rows) {
            assertEquals(1.0, row.get("suggestQuantity"));
            assertEquals("样品单[SP261107001]缺料，建议补货", row.get("reason"));
            assertEquals("AUX000109".equals(row.get("materialCode")) ? 1L : 2L, row.get("sourceAlertId"));
        }
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void mergeTakesLargestGapAndSourceInEitherOrder(boolean sampleLarger) {
        seed(1, 109, "AUX000109", "order_shortage", "SO1", 0, "建议补货 " + (sampleLarger ? "2" : "7"));
        seed(2, 109, "AUX000109", "sample_shortage", "SP1", 0, "建议补货 " + (sampleLarger ? "7" : "2"));
        seed(3, 109, "AUX000109", "sample_shortage", "SP1", 0, "建议补货 1");
        var rows = service.generatePurchaseSuggestions();
        assertEquals(1, rows.size());
        assertEquals(7.0, rows.get(0).get("suggestQuantity"));
        assertEquals(sampleLarger ? 2L : 1L, rows.get(0).get("sourceAlertId"));
        assertEquals("订单[SO1]、样品单[SP1]缺料，建议补货", rows.get(0).get("reason"));
        assertEquals(java.util.Set.of(1L, 2L, 3L), new java.util.HashSet<>(
                service.getUnprocessedAlertIdsByMaterials(List.of(109L))));
    }
    @ParameterizedTest @ValueSource(strings = {"建议补货 0", "建议补货 -2", "建议补货 abc", "其他建议"})
    void nonPositiveOrInvalidGapsAreSkipped(String text) {
        seed(1, 109, "AUX000109", "sample_shortage", "SP1", 0, text);
        assertTrue(service.generatePurchaseSuggestions().isEmpty());
        verifyNoInteractions(materialMapper);
    }
    @Test void nullSuggestionIsSkipped() {
        seed(1, 109, "AUX000109", "sample_shortage", "SP1", 0, null);
        assertTrue(service.generatePurchaseSuggestions().isEmpty());
    }
    @Test void realQueryExcludesProcessedResolvedAndUnrelatedTypes() {
        long id = 1;
        for (String type : List.of("sample_shortage", "order_shortage", "demand_shortage")) {
            seed(id++, 109, "AUX000109", type, "SP1", 2, "建议补货 99");
            seed(id++, 109, "AUX000109", type, "SP1", 3, "建议补货 99");
        }
        seed(10, 109, "AUX000109", "expiry", null, 0, "建议补货 99");
        assertTrue(service.generatePurchaseSuggestions().isEmpty());
        verifyNoInteractions(materialMapper);
    }
    @Test void demandShortageKeepsFallbackAndDecimalGap() {
        seed(1, 109, "AUX000109", "demand_shortage", null, 0, "建议补货 1.25");
        var row = service.generatePurchaseSuggestions().get(0);
        assertEquals("多单汇总缺料，建议补货", row.get("reason"));
        assertEquals(1.25, row.get("suggestQuantity"));
    }
    @ParameterizedTest @ValueSource(ints = {3, 10})
    void lowStockCalculationIsPreservedWhenMerging(int gap) {
        var stock = new InventoryStock();
        stock.setMaterialId(109L);
        stock.setMaterialCode("AUX000109");
        stock.setMaterialName("保护膜");
        stock.setTotalQuantity(new BigDecimal("2"));
        stock.setSafeStock(new BigDecimal("5"));
        when(stockMapper.selectLowStock()).thenReturn(List.of(stock));
        when(purchaseOrderItemMapper.selectInTransitByMaterial()).thenReturn(List.of(
                Map.of("material_id", 109L, "in_transit", new BigDecimal("1"))));
        seed(1, 109, "AUX000109", "safe_stock", null, 0, "建议补货 7");
        seed(2, 109, "AUX000109", "sample_shortage", "SP1", 0, "建议补货 " + gap);
        var rows = service.generatePurchaseSuggestions();
        assertEquals(1, rows.size());
        assertEquals((double) Math.max(7, gap), rows.get(0).get("suggestQuantity"));
        assertEquals(gap > 7 ? 2L : 1L, rows.get(0).get("sourceAlertId"));
        assertEquals("低于安全库存，建议补货；样品单[SP1]缺料，建议补货", rows.get(0).get("reason"));
        assertEquals("urgent", rows.get(0).get("priority"));
    }
}
