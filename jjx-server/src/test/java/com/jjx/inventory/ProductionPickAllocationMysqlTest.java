package com.jjx.inventory;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryOutboundOrder;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.enums.TransactionTypeEnum;
import com.jjx.inventory.service.impl.InventoryStockMutationServiceImpl;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryRollService;
import com.jjx.inventory.service.impl.MaterialStockAllocationService;
import com.jjx.inventory.service.impl.ProductionPickAllocationService;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.service.ReviewFlowService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.jjx.inventory.service.impl.ProductionPickAllocationService.*;

/** 真实MySQL、真实Mapper；全部业务表在连接内用临时表遮蔽，不写持久业务数据。 */
@EnabledIfEnvironmentVariable(named = "JJX_PICK_MYSQL_TEST", matches = "1")
class ProductionPickAllocationMysqlTest {
    SingleConnectionDataSource dataSource;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    ProductionPickAllocationService service;
    SqlSessionTemplate session;
    @BeforeEach void setup() throws Exception {
        String url="jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";
        Connection connection=DriverManager.getConnection(url,System.getenv().getOrDefault("DB_USER","root"),System.getenv().getOrDefault("DB_PASS","123456"));
        dataSource=new SingleConnectionDataSource(connection,true); jdbc=new JdbcTemplate(dataSource);
        for(String table:List.of("inventory_outbound_order","inventory_outbound_item","inventory_stock_item","inventory_inbound_item","inventory_inbound_order","inventory_iqc_batch","inventory_storage_location","inventory_stock","inventory_transaction","inventory_item")) {
            jdbc.execute("CREATE TEMPORARY TABLE jjx_pick_template LIKE " + table);
            Map<String,Object> ddl=jdbc.queryForMap("SHOW CREATE TABLE jjx_pick_template");
            String definition=ddl.get("Create Table").toString().replace("`jjx_pick_template`", "`"+table+"`");
            jdbc.execute("DROP TEMPORARY TABLE jjx_pick_template");
            jdbc.execute(definition);
        }
        jdbc.update("INSERT INTO inventory_outbound_order(outbound_id,outbound_no,outbound_type,source_type,warehouse_id,outbound_date,order_status) VALUES(1,'PICK-TEMP-TEST','production','work_order',2,CURRENT_DATE,?)",InventoryOrderStatusEnum.PENDING.getValue());
        jdbc.update("INSERT INTO inventory_outbound_item(item_id,outbound_id,material_id,material_code,material_name,unit,quantity,source_bom_item_id,coverage_quantity) VALUES(1,1,3,'M3','material','PCS',6,11,6),(2,1,3,'M3','material','PCS',6,12,6)");
        jdbc.update("INSERT INTO inventory_inbound_order(inbound_id,inbound_no,inbound_type,warehouse_id,inbound_date,supplier_id,supplier_name) VALUES(1,'IN-TEMP-TEST','purchase',2,CURRENT_DATE,7,'supplier')");
        jdbc.update("INSERT INTO inventory_inbound_item(item_id,inbound_id,material_id,material_code,material_name,quantity) VALUES(1,1,3,'M3','material',10)");
        jdbc.update("INSERT INTO inventory_stock_item(item_id,inventory_item_id,material_id,material_code,material_name,warehouse_id,location_id,batch_no,quantity,reserved_quantity,status,source_inbound_item_id) VALUES(21,30,3,'M3','material',2,20,'B21',10,0,1,1)");
        MybatisSqlSessionFactoryBean bean=new MybatisSqlSessionFactoryBean(); bean.setDataSource(dataSource);
        com.baomidou.mybatisplus.core.MybatisConfiguration config=new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(InventoryOutboundOrderMapper.class);config.addMapper(InventoryOutboundItemMapper.class);config.addMapper(InventoryStockItemMapper.class);config.addMapper(InventoryStockMapper.class);config.addMapper(InventoryTransactionMapper.class);
        bean.setConfiguration(config);SqlSessionFactory factory=bean.getObject();session=new SqlSessionTemplate(factory);
        ReviewFlowService reviews=mock(ReviewFlowService.class);
        when(reviews.record(anyString(),anyLong(),anyString(),anyString(),any(),any(),any(),isNull())).thenAnswer(inv->new ReviewFlow());
        when(reviews.updateById(any(ReviewFlow.class))).thenReturn(true);
        service=new ProductionPickAllocationService(session.getMapper(InventoryOutboundOrderMapper.class),session.getMapper(InventoryOutboundItemMapper.class),session.getMapper(InventoryStockItemMapper.class),mock(MaterialStockAllocationService.class),mock(InventoryRollService.class),jdbc,reviews);
        jdbc.update("INSERT INTO inventory_item(inventory_item_id,item_type,source_id,item_code,item_name,unit,safe_stock) VALUES(30,'MATERIAL',3,'M3','material','PCS',15)");
        session.getMapper(InventoryStockMapper.class).refreshSummaryByInventoryItemId(30L);
        tx=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }
    @AfterEach void close() { if(dataSource!=null) dataSource.destroy(); }
    @Test void realMapperAllocationConservesDemandAndRepeatedCallsAreIdempotent() {
        tx.executeWithoutResult(s->service.allocate(1L));tx.executeWithoutResult(s->service.allocate(1L));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_outbound_item",Integer.class));
        assertEquals(0,new BigDecimal("12").compareTo(jdbc.queryForObject("SELECT SUM(quantity) FROM inventory_outbound_item",BigDecimal.class)));
        assertEquals(0,new BigDecimal("10").compareTo(jdbc.queryForObject("SELECT SUM(quantity) FROM inventory_outbound_item WHERE stock_item_id IS NOT NULL",BigDecimal.class)));
        assertEquals(0,new BigDecimal("12").compareTo(jdbc.queryForObject("SELECT SUM(coverage_quantity) FROM inventory_outbound_item",BigDecimal.class)));
        assertEquals(0,BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT reserved_quantity FROM inventory_stock_item WHERE item_id=21",BigDecimal.class)));
    }
    @Test void transactionRollsBackEarlierDemandChangesWhenLaterDemandIsInvalid() {
        tx.executeWithoutResult(s->service.allocate(1L));
        AllocationView before=service.view(1L);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM inventory_outbound_item ORDER BY item_id");
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.save(1L,new SaveRequest(before.fingerprint(),List.of(
                new AllocationLine(1L,21L,BigDecimal.ONE),new AllocationLine(2L,21L,new BigDecimal("7")))))));
        assertEquals(rows,jdbc.queryForList("SELECT * FROM inventory_outbound_item ORDER BY item_id"));
    }
    @Test void existingLowStockWarningQueryReturnsIdenticalResultsBeforeAndAfterAllocation() {
        InventoryStockMapper summaries=session.getMapper(InventoryStockMapper.class);
        var warnings=summaries.selectLowStock(); assertEquals(1,warnings.size());
        tx.executeWithoutResult(s->service.allocate(1L));tx.executeWithoutResult(s->service.allocate(1L));
        assertEquals(warnings,summaries.selectLowStock());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction",Integer.class));
    }
    @Test void ordinaryBatchActuallyIssuesThroughMutationServiceAndWritesCorrectLedger() {
        jdbc.update("UPDATE inventory_outbound_item SET quantity=4,coverage_quantity=4 WHERE item_id=2");
        jdbc.update("UPDATE inventory_outbound_order SET order_status=? WHERE outbound_id=1",InventoryOrderStatusEnum.APPROVED.getValue());
        tx.executeWithoutResult(s->service.allocate(1L));
        InventoryStockMutationServiceImpl mutation=new InventoryStockMutationServiceImpl(session.getMapper(InventoryStockItemMapper.class),session.getMapper(InventoryStockMapper.class),session.getMapper(InventoryTransactionMapper.class));
        tx.executeWithoutResult(s->{
            InventoryOutboundOrder order=session.getMapper(InventoryOutboundOrderMapper.class).selectByIdForUpdate(1L);
            var source=session.getMapper(InventoryOutboundItemMapper.class).selectByOutboundId(1L);
            service.validateForIssue(order,source);
            for(var row:source){
                var stock=service.issueStock(order,row);
                InventoryTransaction flow=new InventoryTransaction();flow.setTransactionType(TransactionTypeEnum.OUTBOUND.getCode());flow.setSourceId(1L);flow.setSourceType("work_order");flow.setSourceItemId(row.getItemId());
                mutation.applyDelta(stock,row.getQuantity().negate(),flow);
            }
        });
        assertEquals(0,BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT quantity FROM inventory_stock_item WHERE item_id=21",BigDecimal.class)));
        assertEquals(0,BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT total_quantity FROM inventory_stock WHERE inventory_item_id=30",BigDecimal.class)));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE stock_item_id=21 AND source_id=1 AND source_item_id IS NOT NULL AND batch_no='B21'",Integer.class));
        assertEquals(0,new BigDecimal("-10").compareTo(jdbc.queryForObject("SELECT SUM(quantity) FROM inventory_transaction",BigDecimal.class)));
        assertEquals(0,BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT reserved_quantity FROM inventory_stock_item WHERE item_id=21",BigDecimal.class)));
    }
    @Test void clearingAllocationActuallyClearsNullBindingsAndRetainsOneShortagePerDemand() {
        tx.executeWithoutResult(s->service.allocate(1L));
        AllocationView before=service.view(1L);
        tx.executeWithoutResult(s->service.save(1L,new SaveRequest(before.fingerprint(),List.of())));
        tx.executeWithoutResult(s->service.save(1L,new SaveRequest(service.view(1L).fingerprint(),List.of())));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_outbound_item",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_outbound_item WHERE stock_item_id IS NOT NULL OR batch_no IS NOT NULL OR location_id IS NOT NULL OR roll_no IS NOT NULL",Integer.class));
        assertEquals(0,new BigDecimal("12").compareTo(jdbc.queryForObject("SELECT SUM(quantity) FROM inventory_outbound_item",BigDecimal.class)));
    }
}
