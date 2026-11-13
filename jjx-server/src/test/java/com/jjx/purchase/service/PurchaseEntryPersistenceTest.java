package com.jjx.purchase.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.purchase.converter.PurchaseConverter;
import com.jjx.purchase.domain.dto.*;
import com.jjx.purchase.domain.entity.*;
import com.jjx.purchase.mapper.*;
import com.jjx.purchase.service.impl.*;
import com.jjx.system.mapper.SysDictItemMapper;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 隔离H2真实Mapper/事务；证明保存回读复制精度及默认约定，不代表运行中MySQL已迁移。 */
class PurchaseEntryPersistenceTest {
    JdbcTemplate jdbc; TransactionTemplate transaction;
    PurchaseOrderMapper orders; PurchaseOrderItemMapper items; PurchaseMaterialDefaultMapper defaults;
    PurchaseOrderServiceImpl service; InventoryMaterialMapper materials;
    org.mockito.MockedStatic<SecurityUtils> security;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");
        jdbc=new JdbcTemplate(ds);transaction=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PurchaseOrderMapper.class);configuration.addMapper(PurchaseOrderItemMapper.class);configuration.addMapper(PurchaseMaterialDefaultMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setConfiguration(configuration);factory.setDataSource(ds);
        var session=new SqlSessionTemplate(factory.getObject());
        create(PurchaseOrder.class);create(PurchaseOrderItem.class);
        jdbc.execute("CREATE TABLE purchase_supplier(supplier_id BIGINT PRIMARY KEY,del_flag VARCHAR(1),material_purchase_defaults CLOB,update_by VARCHAR(50),update_time TIMESTAMP)");
        jdbc.update("INSERT INTO purchase_supplier(supplier_id,del_flag) VALUES(16,'0')");
        orders=session.getMapper(PurchaseOrderMapper.class);items=session.getMapper(PurchaseOrderItemMapper.class);defaults=session.getMapper(PurchaseMaterialDefaultMapper.class);
        var ctor=PurchaseOrderServiceImpl.class.getConstructors()[0];
        service=spy((PurchaseOrderServiceImpl)ctor.newInstance(Arrays.stream(ctor.getParameterTypes()).map(org.mockito.Mockito::mock).toArray()));
        ReflectionTestUtils.setField(service,"orderMapper",orders);ReflectionTestUtils.setField(service,"orderItemMapper",items);
        ReflectionTestUtils.setField(service,"purchaseConverter",Mappers.getMapper(PurchaseConverter.class));
        materials=mock(InventoryMaterialMapper.class);ReflectionTestUtils.setField(service,"materialMapper",materials);
        var material=new InventoryMaterial();material.setMaterialId(354L);material.setUnit("㎡");material.setSupplierMaterialName("EBA180");
        material.setWidthMm(bd("1220"));material.setLengthMm(bd("100000"));when(materials.selectById(354L)).thenReturn(material);
        security=mockStatic(SecurityUtils.class);security.when(SecurityUtils::getUsername).thenReturn("test");
        security.when(SecurityUtils::getDisplayName).thenReturn("test");security.when(SecurityUtils::getUserId).thenReturn(1L);
    }
    @AfterEach void close() { security.close(); }
    static BigDecimal bd(String n) { return new BigDecimal(n); }
    static void eq(String n,BigDecimal a) {assertNotNull(a);assertEquals(0,bd(n).compareTo(a));}
    PurchaseOrderDTO order(String number) {
        var d=new PurchaseOrderDTO();d.setOrderNo(number);d.setSupplierId(16L);d.setSupplierName("test supplier");d.setOrderDate(LocalDate.now());d.setExpectedDeliveryDate(LocalDate.now().plusDays(7));d.setCurrency("CNY");
        var i=new PurchaseOrderItemDTO();i.setMaterialId(354L);i.setMaterialCode("RM000350");i.setMaterialName("PET188");i.setUnit("㎡");
        i.setPurchaseUnit("ROLL");i.setPriceUnit("㎡");i.setPurchaseQuantity(bd("3"));i.setRollWidthMm(bd("1220"));i.setRollLengthM(bd("100"));
        i.setUnitPrice(bd("85"));i.setQuantity(bd("999"));i.setAmount(bd("999"));i.setTaxRate(bd("13"));i.setPurchaseRemark("分切后余卷交回\n切片985PCS");d.setItems(List.of(i));return d;
    }
    @Test void saveReadReopenCopyKeepsQuantitiesDimensionsTaxAndPurchaseRemark() {
        var d=order("PO-ONE");transaction.execute(s->{service.insertOrder(d);return null;});
        var stored=orders.selectList(null).get(0);var line=service.selectOrderById(stored.getOrderId()).getItems().get(0);
        eq("3",line.getPurchaseQuantity());eq("366",line.getQuantity());eq("366",line.getPriceQuantity());eq("13",line.getTaxRate());assertTrue(line.getPurchaseRemark().contains("985PCS"));
        doReturn("PO-COPY").when(service).generateOrderNo();
        Long copied=transaction.execute(s->service.copyOrder(stored.getOrderId()));
        var copy=service.selectOrderById(copied).getItems().get(0);eq("3",copy.getPurchaseQuantity());eq("366",copy.getPriceQuantity());eq("100",copy.getRollLengthM());assertEquals(line.getPurchaseRemark(),copy.getPurchaseRemark());
        // 改档案尺寸后，只读回看与复制仍按单据尺寸，不重解释旧订单。
        when(materials.selectById(354L)).thenAnswer(call->{var m=new InventoryMaterial();m.setUnit("㎡");m.setWidthMm(bd("999"));m.setLengthMm(bd("1000"));return m;});
        eq("366",service.selectOrderById(stored.getOrderId()).getItems().get(0).getPriceQuantity());
    }
    @Test void fourDecimalPriceSurvivesRealMapperStorageAndTaxRoundTrip() {
        var d=order("PO-PRICE");var i=d.getItems().get(0);i.setPurchaseUnit("㎡");i.setPurchaseQuantity(bd("240"));i.setUnitPrice(bd("3.564"));
        transaction.execute(s->{service.insertOrder(d);return null;});
        var entity=items.selectList(null).get(0);eq("3.564",entity.getUnitPrice());eq("855.36",entity.getAmount());
        var line=service.selectOrderItemsById(entity.getOrderId()).get(0);eq("240",line.getPriceQuantity());eq("111.1968",line.getTaxAmount());
        var update=order("PO-PRICE");update.setOrderId(entity.getOrderId());update.getItems().get(0).setPurchaseUnit("㎡");update.getItems().get(0).setPurchaseQuantity(bd("240"));update.getItems().get(0).setUnitPrice(bd("3.564"));
        transaction.execute(s->{service.updateOrder(update);return null;});
        eq("3.564",items.selectList(null).get(0).getUnitPrice());
    }
    @Test void previewTaxAtHalfCentMatchesStoredHeader() {
        var d=order("PO-HALF-CENT");var i=d.getItems().get(0);i.setPurchaseUnit("㎡");i.setPurchaseQuantity(bd("10.125"));i.setUnitPrice(bd("20"));
        service.previewPricing(d);eq("26.33",d.getOrderTax());eq("228.83",d.getOrderTotalAmount());
        transaction.execute(s->{service.insertOrder(d);return null;});
        var stored=orders.selectList(null).get(0);eq("26.33",stored.getOrderTax());eq("228.83",stored.getOrderTotalAmount());
    }
    @Test void supplierDefaultsPersistAndConcurrentMaterialUpdatesDoNotOverwriteEachOther() throws Exception {
        var service=new PurchaseMaterialDefaultService(defaults,materials,mock(SysDictItemMapper.class),new ObjectMapper());
        var material=materials.selectById(354L);
        when(materials.selectById(110L)).thenReturn(material);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<?>>();
            for(long id:new long[]{354,110}) futures.add(pool.submit(()->{
                try {start.await();} catch(InterruptedException e) {Thread.currentThread().interrupt();throw new RuntimeException(e);}
                var dto=new PurchaseMaterialDefaultDTO();dto.setSupplierId(16L);dto.setMaterialId(id);dto.setPurchaseUnit("ROLL");dto.setPriceUnit("㎡");
                transaction.execute(s->{service.save(dto,"test");return null;});
            }));
            start.countDown();for(var f:futures) f.get(10,TimeUnit.SECONDS);
        } finally {pool.shutdown();}
        assertEquals("ROLL",service.read(16L,354L).get("purchaseUnit"));assertEquals("ROLL",service.read(16L,110L).get("purchaseUnit"));
    }
    private void create(Class<?> entity) {
        var info=TableInfoHelper.getTableInfo(entity);var fields=new ArrayList<String>();
        fields.add(info.getKeyColumn()+" BIGINT AUTO_INCREMENT PRIMARY KEY");
        for(var f:info.getFieldList()) {
            Class<?> t=f.getPropertyType();String sql=t==BigDecimal.class?"DECIMAL(18,4)":t==Long.class?"BIGINT":t==Integer.class?"INT":t==Boolean.class?"BOOLEAN":t==LocalDate.class?"DATE":t==LocalDateTime.class?"TIMESTAMP":"VARCHAR(4000)";
            if ("unit_price".equals(f.getColumn())) sql="DECIMAL(14,4)";
            fields.add(f.getColumn()+" "+sql);
        }
        jdbc.execute("CREATE TABLE "+info.getTableName()+" ("+String.join(",",fields)+")");
    }
}
