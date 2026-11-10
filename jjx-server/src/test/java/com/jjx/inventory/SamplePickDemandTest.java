package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.impl.InventoryOutboundServiceImpl;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** BOM 轮次过滤使用隔离 H2 的真实 Mapper；单据写入用替身，不连接业务库。 */
@ExtendWith(MockitoExtension.class)
class SamplePickDemandTest {
    @Mock com.jjx.inventory.service.impl.InventoryAlertServiceImpl inventoryAlertService;
    @Mock InventoryOutboundOrderMapper outboundOrderMapper;
    @Mock InventoryOutboundItemMapper outboundItemMapper;
    @Mock InventoryMaterialMapper materialMapper;
    @Mock InventoryStockMapper stockMapper;
    @Mock InventoryStockItemMapper stockItemMapper;
    @Mock InventoryWarehouseMapper outboundWarehouseMapper;
    @Mock InventoryTransactionMapper transactionMapper;
    @Mock SalesSampleOrderMapper sampleOrderMapper;
    @Mock OrderMapper salesOrderMapper;
    @Mock RedisSequenceService redisSequenceService;
    @InjectMocks InventoryOutboundServiceImpl service;
    private SalesSampleBomMapper bomMapper;
    private SalesSampleOrder sample;

    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:sample_pick_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new JdbcTemplate(source).execute("CREATE TABLE sales_sample_bom (bom_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "order_id BIGINT, round_no INT, layer_name VARCHAR(100), material_name VARCHAR(100), "
                + "specification VARCHAR(100), quantity DECIMAL(20,4), unit VARCHAR(20), material_id BIGINT, "
                + "material_code VARCHAR(100), project_ids VARCHAR(100), module_qty DECIMAL(20,4), "
                + "split VARCHAR(100), base_qty DECIMAL(20,4), remark VARCHAR(100), create_by VARCHAR(100), create_time TIMESTAMP)");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(SalesSampleBomMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        bomMapper = new SqlSessionTemplate(factory.getObject()).getMapper(SalesSampleBomMapper.class);
        ReflectionTestUtils.setField(service, "sampleBomMapper", bomMapper);
        sample = new SalesSampleOrder();
        sample.setOrderId(7L);
        sample.setSampleQty(2);
        sample.setSampleRound(1);
        // dev-20261110-003：生成领料单要求「工程打样中」，fixture 按该状态构造
        sample.setSampleStatus(com.jjx.sales.enums.SampleOrderStatusEnum.ENGINEERING.getValue());
        when(sampleOrderMapper.selectByOrderId(7L)).thenReturn(sample);
    }

    @Test
    void 非工程打样中或本轮无BOM时不能生成领料单() {
        // 状态不是工程打样中：直接拦下并说明当前状态
        sample.setSampleStatus(com.jjx.sales.enums.SampleOrderStatusEnum.SAMPLE_READY.getValue());
        var statusError = assertThrows(BusinessException.class, () -> service.previewSamplePick(7L));
        assertTrue(statusError.getMessage().contains("工程打样中"));

        // 状态正确但本轮没有 BOM：提示先编制打样材料
        sample.setSampleStatus(com.jjx.sales.enums.SampleOrderStatusEnum.ENGINEERING.getValue());
        var bomError = assertThrows(BusinessException.class, () -> service.previewSamplePick(7L));
        assertTrue(bomError.getMessage().contains("还没有打样 BOM"));
    }

    @ParameterizedTest
    @CsvSource({"1,2,2", "1,3,3", "0.4,2,1"})
    void demandMultipliesSampleQuantityThenRoundsUp(String perUnit, int sampleQty, String expected) {
        sample.setSampleQty(sampleQty);
        bom(7L, 1, 11L, "材料", perUnit);
        material(11L);
        var row = service.previewSamplePick(7L).get(0);
        assertEquals(new BigDecimal(expected), row.get("quantity"));
        assertEquals("10×20", row.get("specification"));
        assertEquals("PCS", row.get("unit"));
        assertEquals(BigDecimal.ZERO, row.get("availableQuantity"));
        assertEquals(new BigDecimal(expected), row.get("gap"));
        verify(inventoryAlertService).checkSampleShortage(7L);
    }

    @ParameterizedTest
    @CsvSource({"1,1,1.0000,2", "1,4,0.2500,1", "3,4,0.7500,2", "1,3,0.3333,1", "2,3,0.6667,2"})
    void recalculatesUnitQuantityFromBaseAndModuleInsteadOfStoredQuantity(
            String base, String module, String unitQuantity, String demand) {
        bom(7L, 1, 11L, "材料", "99", base, module);
        material(11L);
        var row = service.previewSamplePick(7L).get(0);
        assertDecimal(base, row.get("baseQty"));
        assertDecimal(module, row.get("moduleQty"));
        assertEquals(new BigDecimal(unitQuantity), row.get("unitQuantity"));
        assertEquals(new BigDecimal(demand), row.get("quantity"));
    }

    @ParameterizedTest
    @CsvSource({",4", "3,", ",", "0,4", "3,0", "-1,4", "3,-1"})
    void missingOrNonPositiveBaseOrModuleFallsBackToStoredQuantity(String base, String module) {
        bom(7L, 1, 11L, "材料", "0.6", base, module);
        material(11L);
        var row = service.previewSamplePick(7L).get(0);
        assertDecimal(base, row.get("baseQty"));
        assertDecimal(module, row.get("moduleQty"));
        assertDecimal("0.6", row.get("unitQuantity"));
        assertEquals(new BigDecimal("2"), row.get("quantity")); // ceil(0.6 × 2)
    }

    @Test void currentRoundExcludesOldRoundAndOtherOrders() {
        sample.setSampleRound(2);
        bom(7L, 1, 11L, "第一轮", "100");
        bom(8L, 2, 11L, "其他样品单", "100");
        bom(7L, 2, 11L, "第二轮", "1");
        material(11L);
        var rows = service.previewSamplePick(7L);
        assertEquals(1, rows.size());
        assertEquals("第二轮", rows.get(0).get("materialName"));
        assertEquals(new BigDecimal("2"), rows.get(0).get("quantity"));
    }

    @Test void sameNameMaterialsKeepSeparateIssuedTotals() {
        bom(7L, 1, 11L, "同名", "5");
        bom(7L, 1, 12L, "同名", "5");
        material(11L);
        material(12L);
        issued(issue(11L, "同名", "2"), issue(11L, "历史名称", "1"), issue(12L, "同名", "4"));
        var rows = service.previewSamplePick(7L);
        assertEquals(new BigDecimal("3"), rows.get(0).get("issuedQuantity"));
        assertEquals(new BigDecimal("4"), rows.get(1).get("issuedQuantity"));
        verify(materialMapper, never()).selectOne(any());
    }

    @Test void historicalRowsWithoutIdsFallBackToNameAndUnmatchedRowsStayDisabled() {
        sample.setSampleRound(null); // 沿用首轮默认值
        bom(7L, 1, null, "历史材料", "1");
        bom(7L, 1, 99L, "已失效档案", "1");
        var matched = new InventoryMaterial();
        matched.setMaterialId(11L);
        when(materialMapper.selectOne(any())).thenReturn(matched);
        issued(issue(null, "历史材料", "1"));
        var rows = service.previewSamplePick(7L);
        assertEquals(11L, rows.get(0).get("materialId"));
        assertEquals(BigDecimal.ONE, rows.get(0).get("issuedQuantity"));
        assertNull(rows.get(1).get("materialId"));
        assertEquals(BigDecimal.ZERO, rows.get(1).get("availableQuantity"));
    }

    @Test void splitRequestCannotExceedRemainingDemandAndWritesNothing() {
        bom(7L, 1, 11L, "材料", "99", "3", "4"); // ceil(0.75 × 2)=2，已领 1，剩余 1
        material(11L);
        issued(issue(11L, "材料", "1"));
        var error = assertThrows(BusinessException.class,
                () -> service.createSamplePick(7L, List.of(item(11L, "1"), item(11L, "1"))));
        assertTrue(error.getMessage().contains("剩余可领 1"));
        verify(outboundOrderMapper, never()).insert(any(InventoryOutboundOrder.class));
        verify(outboundItemMapper, never()).insert(any(InventoryOutboundItem.class));
        verifyNoInteractions(redisSequenceService, outboundWarehouseMapper, stockItemMapper, transactionMapper);
        var sequence = inOrder(salesOrderMapper, sampleOrderMapper, outboundOrderMapper);
        sequence.verify(salesOrderMapper).selectByIdForUpdate(7L);
        sequence.verify(sampleOrderMapper).selectByOrderId(7L);
        sequence.verify(outboundOrderMapper).selectList(any());
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "null", "invalid"})
    void everySubmittedQuantityMustBePositive(String quantity) {
        bom(7L, 1, 11L, "材料", "2");
        material(11L);
        assertThrows(BusinessException.class,
                () -> service.createSamplePick(7L, List.of(item(11L, "1"), item(11L, quantity))));
        verify(outboundOrderMapper, never()).insert(any(InventoryOutboundOrder.class));
        verify(outboundItemMapper, never()).insert(any(InventoryOutboundItem.class));
    }

    @Test void cannotSubmitMaterialOnlyPresentInOldRound() {
        sample.setSampleRound(2);
        bom(7L, 1, 11L, "旧材料", "2");
        assertThrows(BusinessException.class, () -> service.createSamplePick(7L, List.of(item(11L, "1"))));
        verify(outboundOrderMapper, never()).insert(any(InventoryOutboundOrder.class));
        verify(outboundItemMapper, never()).insert(any(InventoryOutboundItem.class));
    }

    @Test void acceptsExactlyRemainingDemandAcrossRepeatedBomAndRequestRows() {
        bom(7L, 1, 11L, "材料", "99", "3", "4");
        bom(7L, 1, 11L, "材料", "99", "3", "4"); // 每行需求 2，合计 4，已领 1 只扣一次
        material(11L);
        issued(issue(11L, "材料", "1"));
        var warehouse = new InventoryWarehouse();
        warehouse.setWarehouseId(1L);
        when(outboundWarehouseMapper.selectOne(any())).thenReturn(warehouse);
        when(redisSequenceService.generateBusinessNumberByType(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("SPK-TEST");
        doAnswer(invocation -> {
            ((InventoryOutboundOrder) invocation.getArgument(0)).setOutboundId(100L);
            return 1;
        }).when(outboundOrderMapper).insert(any(InventoryOutboundOrder.class));
        assertEquals(100L, service.createSamplePick(7L, List.of(item(11L, "1"), item(11L, "2"))));
        var order = org.mockito.ArgumentCaptor.forClass(InventoryOutboundOrder.class);
        verify(outboundOrderMapper).updateById(order.capture());
        assertEquals(new BigDecimal("3"), order.getValue().getTotalQuantity());
        verify(outboundItemMapper, times(2)).insert(any(InventoryOutboundItem.class));
        verify(outboundItemMapper).selectByOutboundId(100L); // 沿用原预占入口
    }

    @Test void previewGapClearsWhenStockCoversDemandAndRefreshFailureDoesNotBlock() {
        bom(7L, 1, 11L, "材料", "1");
        material(11L);
        var stock = new InventoryStock();
        stock.setAvailableQuantity(new BigDecimal("3"));
        when(stockMapper.selectByMaterialId(11L)).thenReturn(stock);
        doThrow(new IllegalStateException("预警存储失败")).when(inventoryAlertService).checkSampleShortage(7L);
        assertEquals(BigDecimal.ZERO, service.previewSamplePick(7L).get(0).get("gap"));
        verify(inventoryAlertService).checkSampleShortage(7L);
    }

    private void bom(Long orderId, int round, Long materialId, String name, String quantity) {
        bom(orderId, round, materialId, name, quantity, null, null);
    }

    private void bom(Long orderId, int round, Long materialId, String name, String quantity,
                     String base, String module) {
        var bom = new SalesSampleBom();
        bom.setOrderId(orderId);
        bom.setRoundNo(round);
        bom.setMaterialId(materialId);
        bom.setMaterialName(name);
        bom.setQuantity(new BigDecimal(quantity));
        bom.setBaseQty(base == null ? null : new BigDecimal(base));
        bom.setModuleQty(module == null ? null : new BigDecimal(module));
        bom.setSpecification("10×20");
        bom.setUnit("PCS");
        bomMapper.insert(bom);
    }

    private void assertDecimal(String expected, Object actual) {
        if (expected == null) assertNull(actual);
        else assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual));
    }

    private void material(Long id) {
        var material = new InventoryMaterial();
        material.setMaterialId(id);
        material.setMaterialCode("M" + id);
        when(materialMapper.selectById(id)).thenReturn(material);
    }

    private void issued(InventoryOutboundItem... items) {
        var order = new InventoryOutboundOrder();
        order.setOutboundId(90L);
        when(outboundOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(outboundItemMapper.selectByOutboundId(90L)).thenReturn(List.of(items));
    }

    private InventoryOutboundItem issue(Long id, String name, String quantity) {
        var item = new InventoryOutboundItem();
        item.setMaterialId(id);
        item.setMaterialName(name);
        item.setQuantity(new BigDecimal(quantity));
        return item;
    }

    private Map<String, Object> item(Long id, String quantity) {
        return Map.of("materialId", id, "materialName", "材料", "quantity", quantity);
    }
}
