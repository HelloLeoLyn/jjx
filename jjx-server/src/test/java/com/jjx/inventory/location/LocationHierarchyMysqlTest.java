package com.jjx.inventory.location;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.StorageLocationStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.impl.InventoryStockMutationServiceImpl;
import com.jjx.system.mapper.SysOperLogMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真 MySQL 会话临时表：正式位置、库存和流水均不写入；不启动服务。
 * MySQL 禁止一个 SQL 重复引用同一临时表，因此仅汇总 mapper 用单次扫描等价聚合替身；
 * 位置、批次、流水、移库单据、日志及事务回滚均执行真实 mapper SQL。
 */
@EnabledIfEnvironmentVariable(named="JJX_TEST_MYSQL",matches="1")
class LocationHierarchyMysqlTest {
    private SingleConnectionDataSource ds;
    private JdbcTemplate jdbc;
    private LocationHierarchyService service;
    private LocationHierarchyPolicy policy;
    private InventoryStockMutationServiceImpl mutations;
    private InventoryStockItemMapper stockRows;
    private SysOperLogMapper logRows;
    private static Map<String,Object> original;
    private static DriverManagerDataSource source(){return new DriverManagerDataSource(
            System.getenv().getOrDefault("JJX_TEST_MYSQL_URL","jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"),
            System.getenv().getOrDefault("DB_USER","root"),System.getenv().getOrDefault("DB_PASS","123456"));}
    private static Map<String,Object> fingerprint(){return new JdbcTemplate(source()).queryForMap("SELECT (SELECT COUNT(*) FROM inventory_storage_location) locations,(SELECT BIT_XOR(CRC32(CONCAT(location_id,':',location_code,':',location_name,':',COALESCE(parent_id,'NULL')))) FROM inventory_storage_location) names,(SELECT COUNT(*) FROM inventory_stock_item) batches,(SELECT SUM(quantity) FROM inventory_stock_item) quantity,(SELECT COUNT(*) FROM inventory_transaction) flows");}
    @BeforeAll static void before(){original=fingerprint();}
    @AfterAll static void after(){assertEquals(original,fingerprint(),"真实业务库的库位、批次和流水须保持不变");}

    @BeforeEach void setup() throws Exception {
        ds=new SingleConnectionDataSource(source().getConnection(),true);jdbc=new JdbcTemplate(ds);
        for(String table:List.of("inventory_warehouse","inventory_storage_location","inventory_item","inventory_stock_item","inventory_stock","inventory_transaction","inventory_transfer_order","inventory_transfer_item","inventory_inbound_item","inventory_outbound_item","sys_oper_log"))temporary(table);
        jdbc.update("INSERT INTO inventory_warehouse(warehouse_id,warehouse_code,warehouse_name,status) VALUES (1,'RAW','原料仓','0'),(2,'TMP','临时仓','0')");
        jdbc.update("INSERT INTO inventory_item(inventory_item_id,item_type,source_id,item_code,item_name,unit) VALUES (1,'material',1,'AUX001929','原料','M'),(2,'material',2,'PCS001','零件','PCS')");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        for(Class<?> type:List.of(LocationHierarchyMapper.class,InventoryStorageLocationMapper.class,InventoryWarehouseMapper.class,InventoryStockItemMapper.class,InventoryItemMapper.class,InventoryTransactionMapper.class,InventoryTransferOrderMapper.class,InventoryTransferItemMapper.class,SysOperLogMapper.class))configuration.addMapper(type);
        var bean=new MybatisSqlSessionFactoryBean();bean.setDataSource(ds);bean.setConfiguration(configuration);bean.setGlobalConfig(new GlobalConfig().setBanner(false));
        var session=new SqlSessionTemplate(bean.getObject());var tx=new DataSourceTransactionManager(ds);
        var locations=session.getMapper(InventoryStorageLocationMapper.class);var items=session.getMapper(InventoryItemMapper.class);
        var queries=session.getMapper(LocationHierarchyMapper.class);stockRows=session.getMapper(InventoryStockItemMapper.class);
        policy=new LocationHierarchyPolicy(queries,locations,items);
        var summary=mock(InventoryStockMapper.class);
        when(summary.refreshSummaryByInventoryItemId(anyLong())).thenAnswer(call->jdbc.update("INSERT INTO inventory_stock(inventory_item_id,material_id,material_code,material_name,total_quantity,total_reserved) SELECT inventory_item_id,MAX(material_id),MAX(material_code),MAX(material_name),SUM(quantity),SUM(reserved_quantity) FROM inventory_stock_item WHERE inventory_item_id=? GROUP BY inventory_item_id ON DUPLICATE KEY UPDATE total_quantity=VALUES(total_quantity),total_reserved=VALUES(total_reserved)",call.getArgument(0,Long.class)));
        var mutationTarget=new InventoryStockMutationServiceImpl(stockRows,summary,session.getMapper(InventoryTransactionMapper.class));mutationTarget.setLocationPolicy(policy);mutations=proxy(mutationTarget,tx);
        logRows=spy(session.getMapper(SysOperLogMapper.class));
        service=proxy(new LocationHierarchyService(queries,policy,locations,session.getMapper(InventoryWarehouseMapper.class),stockRows,items,mutations,session.getMapper(InventoryTransferOrderMapper.class),session.getMapper(InventoryTransferItemMapper.class),logRows,new ObjectMapper().findAndRegisterModules()),tx);
    }
    @AfterEach void close(){if(ds!=null)ds.destroy();}
    private void temporary(String table){String ddl=jdbc.queryForMap("SHOW CREATE TABLE "+table).get("Create Table").toString().replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE").replaceAll("(?m)^\\s*CONSTRAINT[^\\n]*\\n","").replaceAll(",\\s*\\) ENGINE","\n) ENGINE");jdbc.execute(ddl);}
    @SuppressWarnings("unchecked") private static <T>T proxy(T target,DataSourceTransactionManager tx){var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();}
    private static BigDecimal n(String value){return new BigDecimal(value);}
    private InventoryStorageLocation draft(String name,Long parent){var node=new InventoryStorageLocation();node.setWarehouseId(1L);node.setLocationName(name);node.setParentId(parent);return node;}
    private InventoryStorageLocation node(String name,Long parent){return service.saveNode(draft(name,parent),"Codex-test");}
    private InventoryStockItem inbound(Long location,long item,String quantity,boolean roll){
        var row=new InventoryStockItem();row.setInventoryItemId(item);row.setMaterialId(item);row.setMaterialCode(item==1?"AUX001929":"PCS001");row.setMaterialName("测试原料");row.setWarehouseId(1L);row.setLocationId(location);row.setBatchNo("B"+UUID.randomUUID());row.setQuantity(BigDecimal.ZERO);row.setReservedQuantity(BigDecimal.ZERO);row.setUnitCost(n("2.5"));row.setSourceInboundItemId(889L);
        if(roll){row.setRollNo("R"+UUID.randomUUID());row.setRollWidthMm(n("100"));row.setOriginalRollLengthM(n("100"));}
        row.setMeasurementSnapshot("{\"locationRegression\":true}");row.setIqcBatchId(552L);row.setProductionDate(java.time.LocalDate.of(2026,11,1));row.setExpiryDate(java.time.LocalDate.of(2027,11,1));
        var tx=new InventoryTransaction();tx.setLotId(777L);tx.setTransactionType("inbound");tx.setSourceType("INVENTORY_INBOUND");tx.setSourceItemId(889L);
        return mutations.applyDelta(row,n(quantity),tx);
    }
    private LocationHierarchyService.Subdivision request(List<InventoryStorageLocation> children,LocationHierarchyService.Allocation... rows){return new LocationHierarchyService.Subdivision(children,List.of(rows),true);}
    private LocationHierarchyService.Allocation allocation(InventoryStockItem row,int child,String quantity){return new LocationHierarchyService.Allocation(row.getItemId(),child,n(quantity));}
    private long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    private void quantity(String expected,String sql){assertEquals(0,n(expected).compareTo(jdbc.queryForObject(sql,BigDecimal.class)));}

    @Test void unknownCapacityDisplaysWithoutRenamingAndCodesSurviveReparenting(){
        var a=node("I区架二，顶层 格",null);var b=node("在印刷，贴膜房间",null);
        assertNull(service.tree(1L).getFirst().getCapacity());assertNull(service.tree(1L).getFirst().getUsedCapacity());
        assertEquals("I区架二，顶层 格",service.tree(1L).getFirst().getLocationName());assertTrue(a.getLocationCode().startsWith("RAW-L"));
        String code=b.getLocationCode();b.setParentId(a.getLocationId());b.setLocationName("用户自定分区");var changed=service.saveNode(b,"Codex-test");assertEquals(code,changed.getLocationCode());
        assertEquals("I区架二，顶层 格 / 用户自定分区",service.tree(1L).getFirst().children.getFirst().path);
        var stock=inbound(changed.getLocationId(),1,"1",false);
        var itemService=new com.jjx.inventory.service.impl.InventoryStockItemServiceImpl(stockRows,mock(InventoryStorageLocationMapper.class),org.mapstruct.factory.Mappers.getMapper(com.jjx.inventory.converter.StockItemConverter.class),policy);
        assertEquals("I区架二，顶层 格 / 用户自定分区",itemService.getById(stock.getItemId()).getLocationName());
    }
    @Test void rejectsCyclesCrossWarehouseAndDuplicateCodes(){
        var a=node("A",null);var b=node("B",a.getLocationId());
        a.setParentId(b.getLocationId());assertThrows(BusinessException.class,()->service.saveNode(a,"test"));
        var cross=draft("跨仓",a.getLocationId());cross.setWarehouseId(2L);assertThrows(BusinessException.class,()->service.saveNode(cross,"test"));
        var duplicate=draft("重复",null);duplicate.setLocationCode(b.getLocationCode());assertThrows(BusinessException.class,()->service.saveNode(duplicate,"test"));assertEquals(2,count("inventory_storage_location"));
    }
    @Test void onlyEnabledLeavesAcceptInventoryAndOccupiedLeafRequiresAllocation(){
        var root=node("分组",null);var leaf=node("格内分区",root.getLocationId());
        assertThrows(BusinessException.class,()->inbound(root.getLocationId(),1,"5",false));
        inbound(leaf.getLocationId(),1,"5",false);
        assertThrows(BusinessException.class,()->node("下级",leaf.getLocationId()));
        assertThrows(BusinessException.class,()->service.status(root.getLocationId(),StorageLocationStatusEnum.DISABLED.getCode(),"test"));
        var empty=node("临时空位",null);service.status(empty.getLocationId(),StorageLocationStatusEnum.DISABLED.getCode(),"test");
        assertThrows(BusinessException.class,()->inbound(empty.getLocationId(),1,"1",false));
    }
    @Test void aggregatesReceivingIssuingBalanceSeparatelyByUnit(){
        var root=node("用户分组",null);var a=node("米位",root.getLocationId());var b=node("件位",root.getLocationId());
        inbound(a.getLocationId(),1,"194",false);inbound(b.getLocationId(),2,"3",false);
        var balances=service.tree(1L).getFirst().balances;assertEquals(2,balances.size());
        var m=balances.stream().filter(row->row.unit().equals("M")).findFirst().orElseThrow();assertEquals(0,n("194").compareTo(m.quantity()));assertEquals(0,n("194").compareTo(m.received()));assertEquals(0,BigDecimal.ZERO.compareTo(m.issued()));
    }
    @Test void optionalCapacityValidatesUnitAndAncestorTotals(){
        var root=node("限额",null);var leaf=node("下级",root.getLocationId());
        root.setCapacityEnabled(true);root.setCapacityUnit("M");root.setCapacity(n("5"));service.saveNode(root,"test");
        inbound(leaf.getLocationId(),1,"5",false);
        assertThrows(BusinessException.class,()->inbound(leaf.getLocationId(),1,"0.01",false));
        assertThrows(BusinessException.class,()->inbound(leaf.getLocationId(),2,"1",false));
        root.setCapacityEnabled(false);service.saveNode(root,"test");inbound(leaf.getLocationId(),2,"1",false);
        root.setCapacityEnabled(true);assertThrows(BusinessException.class,()->service.saveNode(root,"test"));
        quantity("6","SELECT SUM(quantity) FROM inventory_stock_item");
    }
    @Test void occupiedOrHistoricallyUsedLocationsCannotBeDeleted(){
        var parent=node("父",null);var leaf=node("原始库位",parent.getLocationId());var stock=inbound(leaf.getLocationId(),1,"4",false);
        assertThrows(BusinessException.class,()->service.delete(parent.getLocationId()));assertThrows(BusinessException.class,()->service.delete(leaf.getLocationId()));
        var tx=new InventoryTransaction();tx.setTransactionType("outbound");mutations.applyDelta(stock,n("-4"),tx);
        service.status(leaf.getLocationId(),StorageLocationStatusEnum.DISABLED.getCode(),"test");assertThrows(BusinessException.class,()->service.delete(leaf.getLocationId()));
        var empty=node("未使用空位",null);service.delete(empty.getLocationId());assertEquals(2,count("inventory_storage_location"));
    }
    @Test void subdividesAtomicallyWithPairedLedgerAndPreservedBatchLineage(){
        var parent=node("在印刷，贴膜房间",null);var stock=inbound(parent.getLocationId(),1,"10",false);
        var result=service.subdivide(parent.getLocationId(),request(List.of(draft("暂存分区A",null),draft("暂存分区B",null)),allocation(stock,0,"3"),allocation(stock,1,"7")),"Codex-test",1L,"Codex");
        assertNotNull(result.transferNo());assertEquals(3,count("inventory_storage_location"));assertEquals(5,count("inventory_transaction"));assertEquals(1,count("sys_oper_log"));assertEquals(2,count("inventory_transfer_item"));
        quantity("10","SELECT SUM(quantity) FROM inventory_stock_item");quantity("10","SELECT SUM(quantity) FROM inventory_transaction");quantity("10","SELECT total_quantity FROM inventory_stock");
        quantity("0","SELECT quantity FROM inventory_stock_item WHERE item_id="+stock.getItemId());
        var destinations=jdbc.queryForList("SELECT * FROM inventory_stock_item WHERE quantity>0");assertEquals(2,destinations.size());
        for(var destination:destinations){assertEquals(stock.getBatchNo(),destination.get("batch_no"));assertEquals(889L,((Number)destination.get("source_inbound_item_id")).longValue());assertEquals(552L,((Number)destination.get("iqc_batch_id")).longValue());assertEquals(0,n("2.5").compareTo((BigDecimal)destination.get("unit_cost")));assertTrue(destination.get("measurement_snapshot").toString().contains("locationRegression"));assertNotNull(destination.get("production_date"));assertNotNull(destination.get("expiry_date"));}
        assertFalse(service.tree(1L).getFirst().leaf);assertEquals(2,service.tree(1L).getFirst().children.size());
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE source_type='INVENTORY_TRANSFER' AND lot_id=777",Integer.class));
        quantity("10","SELECT total_quantity FROM inventory_transfer_order");
    }
    @Test void incompleteDuplicateReservedAndUnconfirmedAllocationsDoNotWrite(){
        var parent=node("原位",null);var stock=inbound(parent.getLocationId(),1,"10",false);var children=List.of(draft("新位",null));
        assertThrows(BusinessException.class,()->service.subdivide(parent.getLocationId(),request(children,allocation(stock,0,"9")),"test",1L,"test"));
        assertThrows(BusinessException.class,()->service.subdivide(parent.getLocationId(),request(children,allocation(stock,0,"5"),allocation(stock,0,"5")),"test",1L,"test"));
        assertThrows(BusinessException.class,()->service.subdivide(parent.getLocationId(),new LocationHierarchyService.Subdivision(children,List.of(allocation(stock,0,"10")),false),"test",1L,"test"));
        jdbc.update("UPDATE inventory_stock_item SET reserved_quantity=1 WHERE item_id=?",stock.getItemId());
        assertThrows(BusinessException.class,()->service.subdivide(parent.getLocationId(),request(children,allocation(stock,0,"10")),"test",1L,"test"));
        assertEquals(1,count("inventory_storage_location"));assertEquals(1,count("inventory_transaction"));assertEquals(0,count("inventory_transfer_order"));
    }
    @Test void physicalRollMovesWholeAndKeepsOriginalSourceAndMeasurements(){
        jdbc.update("UPDATE inventory_item SET unit='㎡' WHERE inventory_item_id=1");
        var parent=node("卷材暂存",null);var stock=inbound(parent.getLocationId(),1,"10",true);
        assertThrows(BusinessException.class,()->service.subdivide(parent.getLocationId(),request(List.of(draft("A",null),draft("B",null)),allocation(stock,0,"5"),allocation(stock,1,"5")),"test",1L,"test"));
        var result=service.subdivide(parent.getLocationId(),request(List.of(draft("整卷区",null)),allocation(stock,0,"10")),"test",1L,"test");
        var target=stockRows.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InventoryStockItem>().eq(InventoryStockItem::getLocationId,result.childIds().getFirst())).getFirst();
        assertEquals(stock.getRollNo(),target.getRollNo());assertEquals(stock.getSourceInboundItemId(),target.getSourceInboundItemId());assertEquals(0,stock.getRollWidthMm().compareTo(target.getRollWidthMm()));assertEquals(0,stock.getOriginalRollLengthM().compareTo(target.getOriginalRollLengthM()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_stock_item WHERE quantity>0 AND roll_no=?",Integer.class,stock.getRollNo()));
    }
    @Test void failureAfterMovementRollsBackChildrenDocumentsLedgerBalancesAndAudit(){
        var parent=node("回滚原位",null);var stock=inbound(parent.getLocationId(),1,"10",false);
        doThrow(new BusinessException("日志失败")).when(logRows).insert(any(com.jjx.system.domain.entity.SysOperLog.class));
        assertThrows(BusinessException.class,()->service.subdivide(parent.getLocationId(),request(List.of(draft("回滚子位",null)),allocation(stock,0,"10")),"test",1L,"test"));
        assertEquals(1,count("inventory_storage_location"));assertEquals(1,count("inventory_transaction"));assertEquals(1,count("inventory_stock_item"));assertEquals(0,count("inventory_transfer_order"));assertEquals(0,count("inventory_transfer_item"));assertEquals(0,count("sys_oper_log"));
        quantity("10","SELECT quantity FROM inventory_stock_item");quantity("10","SELECT total_quantity FROM inventory_stock");
    }

    @Test void batchStatusIsAtomicAndDefaultOrSummaryReferencesAreRetained(){
        var empty=node("空位",null);var occupied=node("有库存",null);inbound(occupied.getLocationId(),1,"3",false);
        assertThrows(BusinessException.class,()->service.batchStatus(List.of(empty.getLocationId(),occupied.getLocationId()),StorageLocationStatusEnum.DISABLED.getCode(),"test"));
        assertEquals(StorageLocationStatusEnum.NORMAL.getCode(),jdbc.queryForObject("SELECT status FROM inventory_storage_location WHERE location_id=?",String.class,empty.getLocationId()));
        jdbc.update("UPDATE inventory_item SET default_location_id=? WHERE inventory_item_id=1",empty.getLocationId());
        assertThrows(BusinessException.class,()->service.delete(empty.getLocationId()));
    }

    @Test void productInventoryCanBeMovedWithoutFabricatingMaterialIdentityAndMixedUnitsAreNotAdded(){
        jdbc.update("UPDATE inventory_item SET item_type='product' WHERE inventory_item_id=2");
        var parent=node("多种物品暂存",null);var material=inbound(parent.getLocationId(),1,"10",false);var product=inbound(parent.getLocationId(),2,"2",false);
        jdbc.update("UPDATE inventory_stock_item SET material_id=NULL WHERE item_id=?",product.getItemId());
        service.subdivide(parent.getLocationId(),request(List.of(draft("米位",null),draft("成品位",null)),allocation(material,0,"10"),allocation(product,1,"2")),"test",1L,"test");
        assertNull(jdbc.queryForObject("SELECT total_quantity FROM inventory_transfer_order",BigDecimal.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transfer_item WHERE inventory_item_id=2 AND material_id IS NULL",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transfer_item WHERE status=1",Integer.class));
    }
}
