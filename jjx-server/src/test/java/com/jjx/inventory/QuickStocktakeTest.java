package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.impl.InventoryStockMutationServiceImpl;
import com.jjx.inventory.stocktake.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.math.BigDecimal;
import java.util.*;
import static com.jjx.inventory.stocktake.QuickStocktakeData.*;
import static org.junit.jupiter.api.Assertions.*;

/** 隔离H2，真实Mapper、事务及applyDelta；不修改业务库库存。 */
class QuickStocktakeTest {
    JdbcTemplate jdbc; QuickStocktakeService service;
    InventoryStockItemMapper stocks; InventoryStocktakeOrderMapper orders;
    InventoryTransactionMapper flows; InventoryItemMapper identities;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:qst"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");
        jdbc=new JdbcTemplate(ds);var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        for(var c:List.of(QuickStocktakeMapper.class,InventoryStocktakeOrderMapper.class,InventoryStocktakeItemMapper.class,
            InventoryStockItemMapper.class,InventoryItemMapper.class,InventoryWarehouseMapper.class,InventoryStorageLocationMapper.class,InventoryStockMapper.class,InventoryTransactionMapper.class))config.addMapper(c);
        var paging=new MybatisPlusInterceptor();paging.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        var bean=new MybatisSqlSessionFactoryBean();bean.setDataSource(ds);bean.setConfiguration(config);bean.setPlugins(paging);
        var session=new SqlSessionTemplate(bean.getObject());
        for(var c:List.of(InventoryStocktakeOrder.class,InventoryStocktakeItem.class,InventoryStockItem.class,InventoryItem.class,
            InventoryWarehouse.class,InventoryStorageLocation.class,InventoryStock.class,InventoryTransaction.class))createTable(c);
        jdbc.execute("ALTER TABLE inventory_stock ADD UNIQUE(inventory_item_id)");
        jdbc.execute("ALTER TABLE inventory_stocktake_order ADD UNIQUE(stocktake_no)");
        jdbc.update("INSERT INTO inventory_warehouse(warehouse_id,warehouse_name) VALUES (1,'测试仓')");
        identities=session.getMapper(InventoryItemMapper.class);stocks=session.getMapper(InventoryStockItemMapper.class);
        orders=session.getMapper(InventoryStocktakeOrderMapper.class);flows=session.getMapper(InventoryTransactionMapper.class);
        var item=new InventoryItem();item.setInventoryItemId(31L);item.setItemType("MATERIAL");item.setSourceId(11L);item.setItemCode("T-11");item.setUnit("M");item.setStatus(1);identities.insert(item);
        var mutation=new InventoryStockMutationServiceImpl(stocks,session.getMapper(InventoryStockMapper.class),flows);
        var stock=new InventoryStockItem();stock.setInventoryItemId(31L);stock.setMaterialId(11L);stock.setMaterialCode("T-11");stock.setMaterialName("测试材料");
        stock.setWarehouseId(1L);stock.setBatchNo("B-1");stock.setReservedQuantity(BigDecimal.ZERO);stock.setUnitCost(new BigDecimal("3"));
        var tx=new InventoryTransaction();tx.setTransactionType("inbound");mutation.applyDelta(stock,new BigDecimal("10.5"),tx);
        var target=new QuickStocktakeService(session.getMapper(QuickStocktakeMapper.class),orders,session.getMapper(InventoryStocktakeItemMapper.class),stocks,
            identities,org.mockito.Mockito.mock(InventoryInboundItemMapper.class),session.getMapper(InventoryWarehouseMapper.class),session.getMapper(InventoryStorageLocationMapper.class),mutation);
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        service=(QuickStocktakeService)proxy.getProxy();
    }
    void createTable(Class<?> type) {
        var info=TableInfoHelper.getTableInfo(type);var columns=new ArrayList<String>();columns.add(info.getKeyColumn()+" BIGINT AUTO_INCREMENT PRIMARY KEY");
        for(var f:info.getFieldList()) {
            var t=f.getPropertyType();String sql=t==String.class?"VARCHAR(2048)":t==BigDecimal.class?"DECIMAL(18,4)":t==Long.class?"BIGINT":t==Integer.class||t==Boolean.class?"INT":"TIMESTAMP";
            columns.add(f.getColumn()+" "+sql);
        }
        jdbc.execute("CREATE TABLE "+info.getTableName()+" ("+String.join(",",columns)+")");
    }
    Save request(String actual) {
        var stock=stocks.selectList(null).getFirst();var item=identities.selectById(stock.getInventoryItemId());
        return new Save(UUID.randomUUID().toString(),stock.getInventoryItemId(),stock.getItemId(),QuickStocktakeService.fingerprint(stock,item),new BigDecimal(actual),"实盘差异");
    }
    Receipt save(Save r){return service.save(r,1L,"测试人","test");}
    Receipt confirm(Long id){return service.confirm(id,2L,"审核人","reviewer");}
    BigDecimal balance(){return stocks.selectList(null).getFirst().getQuantity();}
    @Test void saveDoesNotChangeStockAndPostingIsAtomicAndIdempotent() {
        var request=request("9.125");var saved=save(request);assertEquals(saved.stocktakeId(),save(request).stocktakeId());
        assertEquals(0,new BigDecimal("10.5").compareTo(balance()));assertEquals(1,flows.selectCount(null));
        assertEquals(0,new BigDecimal("-1.375").compareTo(saved.diffQuantity()));
        confirm(saved.stocktakeId());confirm(saved.stocktakeId());assertEquals(0,new BigDecimal("9.125").compareTo(balance()));
        assertEquals(2,flows.selectCount(null));var flow=flows.selectList(null).getLast();
        assertEquals("adjust",flow.getTransactionType());assertEquals("STOCKTAKE",flow.getSourceType());assertEquals(saved.stocktakeId(),flow.getSourceId());
        assertEquals(saved.stockItemId(),flow.getStockItemId());assertNotNull(flow.getSourceItemId());assertEquals("审核人",flow.getOperatorName());
        assertEquals(0,new BigDecimal("10.5").compareTo(flows.selectList(null).getFirst().getQuantity()));
    }
    @Test void surplusZeroDifferenceAndUnknownCostsKeepOriginalFacts() {
        var a=save(request("12.25"));confirm(a.stocktakeId());assertEquals(0,new BigDecimal("12.25").compareTo(balance()));
        int count=flows.selectCount(null).intValue();confirm(save(request("12.25")).stocktakeId());assertEquals(count,flows.selectCount(null));
        jdbc.update("UPDATE inventory_stock_item SET unit_cost=NULL");var r=save(request("13"));confirm(r.stocktakeId());
        assertNull(flows.selectList(null).getLast().getUnitCost());assertNull(flows.selectList(null).getLast().getAmount());
        assertNull(orders.selectById(r.stocktakeId()).getTotalDiffAmount());
    }
    @Test void changedSnapshotAndReservationsRejectWithoutPosting() {
        var stale=request("8");jdbc.update("UPDATE inventory_stock_item SET reserved_quantity=9");
        assertThrows(BusinessException.class,()->save(stale));assertThrows(BusinessException.class,()->save(request("8")));
        jdbc.update("UPDATE inventory_stock_item SET reserved_quantity=0");var r=save(request("8"));
        jdbc.update("UPDATE inventory_stock_item SET reserved_quantity=1");assertThrows(BusinessException.class,()->confirm(r.stocktakeId()));assertEquals(1,flows.selectCount(null));
    }
    @Test void invalidQuantityInactiveWrongIdentityAndChangedRequestAreRejected() {
        assertThrows(BusinessException.class,()->save(request("-1")));assertThrows(BusinessException.class,()->save(request("1.00001")));
        var r=request("9");save(r);assertThrows(BusinessException.class,()->save(new Save(r.requestId(),r.inventoryItemId(),r.stockItemId(),r.fingerprint(),new BigDecimal("8"),r.reason())));
        jdbc.update("UPDATE inventory_stock_item SET status=0");assertThrows(BusinessException.class,()->save(request("9")));
    }
    @Test void productIdentityStaysNullAndRollIdentityIsPreserved() {
        jdbc.update("UPDATE inventory_item SET item_type='PRODUCT',source_id=71");jdbc.update("UPDATE inventory_stock_item SET material_id=NULL");
        var r=save(request("11"));confirm(r.stocktakeId());assertNull(flows.selectList(null).getLast().getMaterialId());
        jdbc.update("UPDATE inventory_stock_item SET roll_no='R-1',roll_width_mm=1000,original_roll_length_m=20,source_inbound_item_id=99");
        confirm(save(request("10")).stocktakeId());var tx=flows.selectList(null).getLast();assertEquals("R-1",tx.getRollNo());assertNotNull(tx.getSourceItemId());
    }
    @Test void transactionFailureRollsBackBalanceLedgerAndApprovalState() {
        var r=save(request("8"));jdbc.execute("ALTER TABLE inventory_transaction ADD CONSTRAINT deny_adjust CHECK(transaction_type <> 'adjust')");
        assertThrows(Exception.class,()->confirm(r.stocktakeId()));assertEquals(0,new BigDecimal("10.5").compareTo(balance()));
        assertEquals(1,flows.selectCount(null));assertEquals(5,orders.selectById(r.stocktakeId()).getOrderStatus());
    }
    @Test void pendingCancellationKeepsFactsAndPostedOrdersCannotBeCancelled() {
        var pending=save(request("9"));service.cancel(pending.stocktakeId(),"test");service.cancel(pending.stocktakeId(),"test");
        assertEquals(1,orders.selectCount(null));assertEquals(1,flows.selectCount(null));
        assertEquals(0,new BigDecimal("10.5").compareTo(balance()));assertThrows(BusinessException.class,()->confirm(pending.stocktakeId()));
        var posted=save(request("9"));confirm(posted.stocktakeId());assertThrows(BusinessException.class,()->service.cancel(posted.stocktakeId(),"test"));
    }
    @Test void concurrentConfirmationCreatesOnlyOneAdjustment() throws Exception {
        var receipt=save(request("9"));
        var start=new java.util.concurrent.CountDownLatch(1);
        var a=java.util.concurrent.CompletableFuture.runAsync(()->{try{start.await();confirm(receipt.stocktakeId());}catch(InterruptedException ex){throw new RuntimeException(ex);}});
        var b=java.util.concurrent.CompletableFuture.runAsync(()->{try{start.await();confirm(receipt.stocktakeId());}catch(InterruptedException ex){throw new RuntimeException(ex);}});
        start.countDown();java.util.concurrent.CompletableFuture.allOf(a,b).get(10,java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(2,flows.selectCount(null));assertEquals(0,new BigDecimal("9").compareTo(balance()));
    }
    @Test void previewComputesDifferenceWithoutWritingDocuments() {
        var preview=service.preview(request("8.125"));assertEquals(0,new BigDecimal("-2.375").compareTo(preview.diffQuantity()));
        assertEquals(0,orders.selectCount(null));assertEquals(1,flows.selectCount(null));
    }
    @Test void writeEndpointsUseSeparatePermissionsAndLegacyMutationRejectsQuickOrders() throws Exception {
        var create=QuickStocktakeController.class.getMethod("save",Save.class).getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class);
        var confirm=QuickStocktakeController.class.getMethod("confirm",Long.class).getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class);
        assertEquals("inventory:stocktake:add",create.value()[0]);assertEquals("inventory:stocktake:approve",confirm.value()[0]);
        var target=org.mockito.Mockito.mock(com.jjx.inventory.service.impl.InventoryStocktakeServiceImpl.class,org.mockito.Mockito.CALLS_REAL_METHODS);
        org.springframework.test.util.ReflectionTestUtils.setField(target,"stocktakeOrderMapper",orders);
        var r=save(request("9"));assertThrows(BusinessException.class,()->target.processDiff(r.stocktakeId(),1L,"bad"));assertThrows(BusinessException.class,()->target.updateStatus(r.stocktakeId(),11));
    }
}
