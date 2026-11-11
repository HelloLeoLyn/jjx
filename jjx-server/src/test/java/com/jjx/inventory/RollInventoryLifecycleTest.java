package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.dto.save.RollReturnSaveDTO;
import com.jjx.inventory.enums.*;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.*;
import com.jjx.inventory.service.impl.*;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
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
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 隔离 H2、真实 Mapper 和事务；不代表真实 MySQL/API 已验收。 */
class RollInventoryLifecycleTest extends RollFixture {
    @Test void fiftyRollsShareOneIqcBatchAndReturnHalfWithoutChangingOriginalFacts() {
        List<Long> stockIds = receive(50);
        eq("487.5", balance());
        long issueId = issue(stockIds.subList(0,2));
        eq("468", balance());
        var issued = outItems.selectByOutboundId(issueId).get(0);
        var ret = returnInput(issued,"25",1L,null);
        long returnedId = rolls.returnRoll(ret);
        eq("472.875", balance());
        assertEquals(49, stocks.selectList(null).stream().filter(s -> s.getQuantity().signum()>0).count());
        assertEquals(48, stocks.selectList(null).stream().filter(s -> s.getQuantity().compareTo(bd("9.75"))==0).count());
        var origin = stocks.selectById(stockIds.get(0));
        eq("50", origin.getOriginalRollLengthM()); eq("4.875", origin.getQuantity());
        eq("9.75", outItems.selectById(issued.getItemId()).getQuantity());
        eq("25", inItems.selectByInboundId(returnedId).get(0).getRollLengthM());
        assertEquals("IQC-SHARED", origin.getBatchNo()); assertEquals(77L,origin.getIqcBatchId());
        consistent();
    }

    @Test void moveReturnKeepsPhysicalRollAndAllPositionLedgers() {
        long source = receive(1).get(0); long id = issue(List.of(source));
        var issued = outItems.selectByOutboundId(id).get(0);
        long returned = rolls.returnRoll(returnInput(issued,"25",2L,20L));
        var item = inItems.selectByInboundId(returned).get(0);
        var target = stocks.selectById(item.getStockItemId());
        assertNotEquals(source,target.getItemId()); assertEquals("ROLL-1",target.getRollNo());
        assertEquals(stocks.selectById(source).getSourceInboundItemId(),target.getSourceInboundItemId());
        assertEquals(2L,target.getWarehouseId()); assertEquals(20L,target.getLocationId());
        eq("0", stocks.selectById(source).getQuantity()); eq("4.875",target.getQuantity());
        assertThrows(BusinessException.class,() -> rolls.returnRoll(returnInput(issued,"25",1L,null)));
        long reverse = rolls.reverseReturn(item.getItemId(),"测量错误");
        eq("-4.875",inItems.selectByInboundId(reverse).get(0).getQuantity());
        eq("4.875",inItems.selectById(item.getItemId()).getQuantity());
        assertThrows(BusinessException.class,() -> rolls.reverseReturn(item.getItemId(),"重复"));
        rolls.returnRoll(returnInput(issued,"20",2L,20L));
        long later = issue(List.of(target.getItemId()));
        assertNotEquals(id,later);
        assertThrows(BusinessException.class,() -> rolls.returnRoll(returnInput(issued,"10",1L,null)));
        consistent();
    }

    @Test void wrongRollExcessLengthAndWrongLocationNeverPost() {
        long stock = receive(1).get(0); long id = issue(List.of(stock));
        var issued = outItems.selectByOutboundId(id).get(0);
        int before = txs.selectCount(null).intValue();
        var wrong = returnInput(issued,"25",1L,null); wrong.setRollNo("OTHER-ROLL");
        assertThrows(BusinessException.class,() -> rolls.returnRoll(wrong));
        assertThrows(BusinessException.class,() -> rolls.returnRoll(returnInput(issued,"51",1L,null)));
        assertThrows(BusinessException.class,() -> rolls.returnRoll(returnInput(issued,"25",1L,20L)));
        assertEquals(before,txs.selectCount(null)); eq("0", balance());
    }

    @Test void postingFailureRollsBackReturnDocumentAndStockAndTransaction() {
        long stock = receive(1).get(0); long id = issue(List.of(stock));
        var issued = outItems.selectByOutboundId(id).get(0);
        int documents = ins.selectCount(null).intValue(), flows = txs.selectCount(null).intValue();
        jdbc.execute("ALTER TABLE inventory_transaction ADD CONSTRAINT deny_return CHECK (source_type <> 'roll_return')");
        assertThrows(Exception.class,() -> rolls.returnRoll(returnInput(issued,"25",2L,20L)));
        assertEquals(documents,ins.selectCount(null)); assertEquals(flows,txs.selectCount(null));
        eq("0", balance()); assertEquals(1,stocks.selectCount(null));
    }

    @Test void issueAndReturnCanBeReversedOnlyThroughNewDocuments() {
        long stock = receive(1).get(0); long id = issue(List.of(stock));
        var issued = outItems.selectByOutboundId(id).get(0);
        long returned = rolls.returnRoll(returnInput(issued,"25",1L,null));
        assertThrows(BusinessException.class,() -> rolls.reverseIssue(issued.getItemId(),"先退料红冲"));
        rolls.reverseReturn(inItems.selectByInboundId(returned).get(0).getItemId(),"退料测量错误");
        long reverse = rolls.reverseIssue(issued.getItemId(),"发料错误");
        eq("9.75",balance()); eq("-9.75",outItems.selectByOutboundId(reverse).get(0).getQuantity());
        eq("9.75",outItems.selectById(issued.getItemId()).getQuantity());
        assertThrows(BusinessException.class,() -> rolls.reverseIssue(issued.getItemId(),"重复红冲"));
        assertThrows(BusinessException.class,() -> rolls.returnRoll(returnInput(issued,"25",1L,null)));
        consistent();
    }
}

abstract class RollFixture {
    JdbcTemplate jdbc;
    DataSourceTransactionManager manager;
    InventoryStockItemMapper stocks;
    InventoryTransactionMapper txs;
    InventoryInboundOrderMapper ins;
    InventoryInboundItemMapper inItems;
    InventoryOutboundOrderMapper outs;
    InventoryOutboundItemMapper outItems;
    InventoryStockMapper summaries;
    InventoryRollService rolls;
    InventoryStockMutationService mutation;
    InventoryInboundService inbound;
    InventoryOutboundService outbound;
    InventoryMaterialMapper materials;
    InventoryItemMapper inventoryItems;
    MockedStatic<SecurityUtils> security;
    AtomicInteger numbers = new AtomicInteger();

    @BeforeEach void setupRollFixture() throws Exception {
        security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(1L);
        security.when(SecurityUtils::getUsername).thenReturn("test");
        security.when(SecurityUtils::getDisplayName).thenReturn("test");
        var ds = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");
        jdbc = new JdbcTemplate(ds); manager = new DataSourceTransactionManager(ds);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (var m : List.of(InventoryStockItemMapper.class,InventoryTransactionMapper.class,InventoryInboundOrderMapper.class,
                InventoryInboundItemMapper.class,InventoryOutboundOrderMapper.class,InventoryOutboundItemMapper.class,InventoryStockMapper.class,InventoryAlertLogMapper.class)) config.addMapper(m);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setConfiguration(config); factory.setDataSource(ds);
        var session = new SqlSessionTemplate(factory.getObject());
        for (var e : List.of(InventoryStockItem.class,InventoryTransaction.class,InventoryInboundOrder.class,
                InventoryInboundItem.class,InventoryOutboundOrder.class,InventoryOutboundItem.class,InventoryStock.class)) createTable(e);
        jdbc.execute("ALTER TABLE inventory_stock ADD UNIQUE(inventory_item_id)");
        jdbc.execute("ALTER TABLE inventory_stock_item ADD active_roll VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN quantity>0 THEN roll_no ELSE NULL END)");
        jdbc.execute("ALTER TABLE inventory_stock_item ADD UNIQUE(active_roll)");
        stocks=session.getMapper(InventoryStockItemMapper.class); txs=session.getMapper(InventoryTransactionMapper.class);
        ins=session.getMapper(InventoryInboundOrderMapper.class); inItems=session.getMapper(InventoryInboundItemMapper.class);
        outs=session.getMapper(InventoryOutboundOrderMapper.class); outItems=session.getMapper(InventoryOutboundItemMapper.class);
        summaries=session.getMapper(InventoryStockMapper.class);
        materials=mock(InventoryMaterialMapper.class); inventoryItems=mock(InventoryItemMapper.class);
        var material = new InventoryMaterial(); material.setMaterialId(11L); material.setMaterialCode("AUX-TEST"); material.setMaterialName("卷材"); material.setUnit("㎡"); material.setWidthMm(bd("195"));
        when(materials.selectById(11L)).thenReturn(material);
        var ii = new InventoryItem(); ii.setInventoryItemId(31L); ii.setItemType(InventoryItemTypeEnum.MATERIAL.getCode()); ii.setSourceId(11L); ii.setItemCode("AUX-TEST"); ii.setUnit("㎡");
        when(inventoryItems.selectById(31L)).thenReturn(ii);
        var locations=mock(InventoryStorageLocationMapper.class); var loc=new InventoryStorageLocation(); loc.setLocationId(20L); loc.setWarehouseId(2L);
        when(locations.selectById(20L)).thenReturn(loc);
        var warehouses=mock(InventoryWarehouseMapper.class);
        when(warehouses.selectById(anyLong())).thenAnswer(c -> { var w=new InventoryWarehouse(); w.setWarehouseId(c.getArgument(0)); return w; });
        var seq=mock(RedisSequenceService.class);
        when(seq.generateBusinessNumberByType(anyString(),anyString(),anyString(),anyInt())).thenAnswer(c -> c.getArgument(1)+"-"+numbers.incrementAndGet());
        mutation=proxy(new InventoryStockMutationServiceImpl(stocks,summaries,txs),InventoryStockMutationService.class);
        rolls=proxy(new InventoryRollServiceImpl(stocks,txs,ins,inItems,outs,outItems,inventoryItems,materials,locations,warehouses,mutation,seq),InventoryRollService.class);
        var in=construct(InventoryInboundServiceImpl.class);
        Map.of("inboundOrderMapper",ins,"inboundItemMapper",inItems,"stockItemMapper",stocks,"stockMutationService",mutation,
                "transactionMapper",txs,"stockMapper",summaries,"inventoryMaterialMapper",materials,"inventoryItemMapper",inventoryItems,
                "rollService",rolls,"redisSequenceService",seq).forEach((k,v)->ReflectionTestUtils.setField(in,k,v));
        inbound=proxy(in,InventoryInboundService.class);
        var out=construct(InventoryOutboundServiceImpl.class);
        Map.of("outboundOrderMapper",outs,"outboundItemMapper",outItems,"stockItemMapper",stocks,"stockMutationService",mutation,
                "transactionMapper",txs,"materialMapper",materials,"rollService",rolls,"redisSequenceService",seq).forEach((k,v)->ReflectionTestUtils.setField(out,k,v));
        outbound=proxy(out,InventoryOutboundService.class);
    }
    @AfterEach void closeFixture() { security.close(); }
    <T> T proxy(Object target,Class<T> type) {
        var p=new ProxyFactory(target); p.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource())); return type.cast(p.getProxy());
    }
    List<Long> receive(int count) {
        var list=new ArrayList<Map<String,Object>>();
        for(int n=1;n<=count;n++) { var item=new HashMap<String,Object>();
            item.put("inventoryItemId",31L); item.put("materialId",11L); item.put("materialCode","AUX-TEST"); item.put("materialName","卷材");
            item.put("unit","㎡"); item.put("rollNo","ROLL-"+n); item.put("rollWidthMm",195); item.put("rollLengthM",50);
            item.put("quantity",999); item.put("unitPrice",2); item.put("batchNo","IQC-SHARED"); list.add(item);
        }
        long id=inbound.create(Map.of("inboundType","other","sourceType","other","warehouseId",1L,"items",list));
        // 同检验批多卷，不把检验批号用作卷号。
        jdbc.update("UPDATE inventory_inbound_item SET iqc_batch_id=77 WHERE inbound_id=?",id);
        assertTrue(inbound.confirm(id,1L,"test"));
        return inItems.selectByInboundId(id).stream().map(InventoryInboundItem::getStockItemId).toList();
    }
    long issue(List<Long> ids) {
        var list=new ArrayList<Map<String,Object>>(); Long warehouse=null;
        for(long id:ids) { var stock=stocks.selectById(id); warehouse=stock.getWarehouseId(); var item=new HashMap<String,Object>();
            item.put("stockItemId",id); item.put("rollNo",stock.getRollNo()); item.put("inventoryItemId",31L);
            item.put("materialId",11L); item.put("materialCode","AUX-TEST"); item.put("materialName","卷材");
            item.put("unit","㎡"); item.put("quantity",stock.getQuantity()); item.put("unitPrice",999); list.add(item);
        }
        long id=outbound.create(Map.of("outboundType","other","sourceType","other","warehouseId",warehouse,"items",list));
        assertTrue(outbound.confirm(id,1L,"test")); return id;
    }
    RollReturnSaveDTO returnInput(InventoryOutboundItem issued,String length,Long warehouse,Long location) {
        var r=new RollReturnSaveDTO(); r.setSourceOutboundItemId(issued.getItemId()); r.setRollNo(issued.getRollNo());
        r.setReturnedLengthM(bd(length)); r.setWarehouseId(warehouse); r.setLocationId(location); r.setReason("余卷退仓"); return r;
    }
    BigDecimal balance() { return jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM inventory_stock_item",BigDecimal.class); }
    void consistent() {
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_stock_item s LEFT JOIN (SELECT stock_item_id,SUM(quantity) q FROM inventory_transaction GROUP BY stock_item_id) t ON t.stock_item_id=s.item_id WHERE s.quantity<>COALESCE(t.q,0)",Integer.class));
        eq(balance().toString(),jdbc.queryForObject("SELECT SUM(quantity) FROM inventory_transaction",BigDecimal.class));
        eq(balance().toString(),jdbc.queryForObject("SELECT total_quantity FROM inventory_stock WHERE inventory_item_id=31",BigDecimal.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE roll_no IS NOT NULL AND (source_item_id IS NULL OR stock_item_id IS NULL)",Integer.class));
    }
    static BigDecimal bd(String value) { return new BigDecimal(value); }
    static void eq(String expected,BigDecimal actual) { assertEquals(0,bd(expected).compareTo(actual),expected+" != "+actual); }
    static <T> T construct(Class<T> type) throws Exception { var c=type.getConstructors()[0]; return type.cast(c.newInstance(Arrays.stream(c.getParameterTypes()).map(t->mock(t)).toArray())); }
    void createTable(Class<?> entity) {
        var info=TableInfoHelper.getTableInfo(entity); var cols=new ArrayList<String>(); cols.add(info.getKeyColumn()+" BIGINT AUTO_INCREMENT PRIMARY KEY");
        for(var f:info.getFieldList()) { var t=f.getPropertyType(); String sql=t==String.class?"VARCHAR(2048)":t==BigDecimal.class?"DECIMAL(18,4)":t==Long.class?"BIGINT":t==Integer.class||t==Boolean.class?"INT":"TIMESTAMP"; cols.add(f.getColumn()+" "+sql); }
        jdbc.execute("CREATE TABLE "+info.getTableName()+" ("+String.join(",",cols)+")");
    }
}
