package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.master.MaterialMasterDTO;
import com.jjx.inventory.migration.InventoryMigrationCommand;
import com.jjx.inventory.migration.InventoryMigrationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.jjx.inventory.migration.InventoryMigrationData.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryMigrationTest {
    @Test void costAdjustmentUsesSignedOriginalQuantityAndDoesNotAccumulateRoundingDrift() {
        assertEquals(new BigDecimal("5.25"),InventoryMigrationService.costDelta(new BigDecimal("1.5"),null,new BigDecimal("3.5")));
        assertEquals(new BigDecimal("-0.25"),InventoryMigrationService.costDelta(new BigDecimal("-0.5"),new BigDecimal("3.5"),new BigDecimal("4")));
        BigDecimal q=new BigDecimal("0.3333");
        BigDecimal first=InventoryMigrationService.costDelta(q,null,new BigDecimal("0.01"));
        BigDecimal second=InventoryMigrationService.costDelta(q,new BigDecimal("0.01"),new BigDecimal("0.02"));
        assertEquals(InventoryMigrationService.costDelta(q,null,new BigDecimal("0.02")),first.add(second));
    }
    @Test void locationsNormalizeOnlyFormatting() {
        assertEquals(InventoryMigrationService.locationKey("D区架一，三层-1格"),InventoryMigrationService.locationKey("D区 架一,三层-1格"));
        assertNotEquals(InventoryMigrationService.locationKey("三层-1格"),InventoryMigrationService.locationKey("三层-2格"));
    }
    @Test void writeEndpointsHaveDedicatedPermissions() throws Exception {
        var type=com.jjx.inventory.migration.InventoryMigrationController.class;
        var permissions=type.getMethod("costs",Long.class,List.class).getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class);
        assertArrayEquals(new String[]{"inventory:migration:cost"},permissions.value());
        var importing=type.getMethod("importBatch",Batch.class).getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class);
        assertEquals(cn.dev33.satoken.annotation.SaMode.AND,importing.mode());
    }

    /** 真实MySQL服务/Mapper/事务；每项外层回滚，不启Web/调度服务，不留下测试库存。 */
    @Nested
    @EnabledIfEnvironmentVariable(named="JJX_TEST_MIGRATION_DATABASE",matches="1")
    class Database {
        InventoryMigrationCommand.Runtime runtime;
        DataSourceTransactionManager manager;
        TransactionStatus outer;
        int before;
        String sha;
        @BeforeEach void begin() throws Exception {
            runtime=InventoryMigrationCommand.connect(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL","jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"),System.getenv().getOrDefault("DB_USER","root"),System.getenv().getOrDefault("DB_PASS","123456"));
            before=runtime.jdbc().queryForObject("SELECT COUNT(*) FROM inventory_stock_item",Integer.class);
            manager=new DataSourceTransactionManager(runtime.jdbc().getDataSource());
            outer=manager.getTransaction(new DefaultTransactionDefinition());
            sha=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
        }
        @AfterEach void rollback() {
            if (outer!=null && !outer.isCompleted()) manager.rollback(outer);
            assertEquals(before,runtime.jdbc().queryForObject("SELECT COUNT(*) FROM inventory_stock_item",Integer.class));
            assertEquals(0,runtime.jdbc().queryForObject("SELECT COUNT(*) FROM inventory_inbound_order WHERE trace_id=?",Integer.class,sha));
        }
        MaterialMasterDTO material(String unit) {
            var dto=new MaterialMasterDTO();dto.setCategoryId(18L);dto.setMaterialName("T-MIG-"+sha.substring(0,12));
            dto.setUnit(unit);dto.setWidthMm(new BigDecimal("300"));dto.setLengthMm(new BigDecimal("305"));
            dto.setThicknessMm(new BigDecimal("0.125"));dto.setModel(null);dto.setMeasurementPolicy(null);dto.setAttributeJson(Map.of());return dto;
        }
        Batch batch(String quantity,String unit) {
            var first=new Line(2,"0.125中砂PC(尚昇)","300*305",null,unit,null,"迁移测试架一",new BigDecimal(quantity),List.of(),material(unit));
            var zero=new Line(3,"原名","300*305",null,unit,null,null,BigDecimal.ZERO,List.of(),material(unit));
            return new Batch("MIG20261117-"+sha.substring(0,12),sha,LocalDate.of(2026,11,17),"原料仓",List.of(first,zero));
        }
        Imported importing(Batch batch) { return runtime.service().importBatch(batch,"Codex-test","测试操作者",1L); }
        CostResult cost(Long id,List<Cost> requests) { return runtime.service().maintainCosts(id,requests,"Codex-test","测试操作者",1L); }
        @Test void importIsAtomicIdempotentAndKeepsFractionalAndZeroStockWithUnknownDatesAndCost() {
            Batch batch=batch("10.5","PCS");
            Long oldSourceMax=runtime.jdbc().queryForObject("SELECT COALESCE(MAX(source_id),0) FROM inventory_item WHERE item_type='MATERIAL'",Long.class);
            assertTrue(runtime.service().preview(batch).errors().isEmpty());
            Imported imported=importing(batch);
            assertEquals(2,imported.rows());assertFalse(imported.alreadyImported());assertTrue(importing(batch).alreadyImported());
            runtime.json().setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            assertTrue(importing(batch).alreadyImported(),"Web与命令序列化配置不同仍须幂等");
            var values=runtime.jdbc().queryForList("SELECT quantity,unit_cost,production_date,expiry_date FROM inventory_stock_item WHERE batch_no LIKE ?",batch.batchCode()+"-%");
            assertEquals(2,values.size());
            assertTrue(runtime.jdbc().queryForObject("SELECT MIN(material_id) FROM inventory_inbound_item WHERE inbound_id=?",Long.class,imported.inboundId())>oldSourceMax,"不得重用清库残留的库存来源身份");
            var itemIds=runtime.jdbc().queryForList("SELECT DISTINCT inventory_item_id FROM inventory_inbound_item WHERE inbound_id=?",Long.class,imported.inboundId());
            var valuation=runtime.stockMapper().costSummary(itemIds,com.jjx.inventory.enums.StockItemStatusEnum.ACTIVE.getValue());
            assertEquals(1,((Number)valuation.get("missing_cost_batches")).intValue());
            var unitSummary=runtime.stockMapper().quantitiesByUnit(itemIds,com.jjx.inventory.enums.StockItemStatusEnum.ACTIVE.getValue());
            assertEquals(1,unitSummary.size()); assertEquals("PCS",unitSummary.get(0).get("unit"));
            assertEquals(0,new BigDecimal("10.5").compareTo((BigDecimal)unitSummary.get(0).get("quantity")));
            assertEquals(0,new BigDecimal("10.5").compareTo(values.stream().map(m -> (BigDecimal)m.get("quantity")).reduce(BigDecimal.ZERO,BigDecimal::add)));
            for (var row:values) {assertNull(row.get("unit_cost"));assertNull(row.get("production_date"));assertNull(row.get("expiry_date"));}
            assertEquals(1,runtime.jdbc().queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE source_no=?",Integer.class,batch.batchCode()));
            assertEquals(1,runtime.jdbc().queryForObject("SELECT COUNT(DISTINCT material_id) FROM inventory_inbound_item WHERE inbound_id=?",Integer.class,imported.inboundId()));
            assertNull(runtime.jdbc().queryForObject("SELECT model FROM inventory_material WHERE material_id=(SELECT material_id FROM inventory_inbound_item WHERE inbound_id=? LIMIT 1)",String.class,imported.inboundId()));
            assertEquals(LocalDate.of(2026,11,17),runtime.jdbc().queryForObject("SELECT inbound_date FROM inventory_inbound_order WHERE inbound_id=?",java.sql.Date.class,imported.inboundId()).toLocalDate());
            assertNull(runtime.jdbc().queryForObject("SELECT total_amount FROM inventory_inbound_order WHERE inbound_id=?",BigDecimal.class,imported.inboundId()));
            assertEquals(com.jjx.common.enums.ApproveStatusEnum.APPROVED.getValue(),runtime.jdbc().queryForObject("SELECT approve_status FROM inventory_inbound_order WHERE inbound_id=?",Integer.class,imported.inboundId()));
            assertNull(runtime.jdbc().queryForObject("SELECT standard_price FROM inventory_material WHERE material_id=(SELECT material_id FROM inventory_inbound_item WHERE inbound_id=? LIMIT 1)",BigDecimal.class,imported.inboundId()));
        }
        @Test void costMaintenanceAdjustsOriginalAndAlreadyIssuedValuesWithoutChangingTheirFacts() {
            Batch batch=batch("1.5","M");Imported imported=importing(batch);
            RowView row=runtime.service().rows(imported.inboundId()).get(0);
            var original=runtime.jdbc().queryForMap("SELECT * FROM inventory_transaction WHERE source_no=?",batch.batchCode());
            runtime.json().setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
            CostResult first=cost(imported.inboundId(),List.of(new Cost(row.lineId(),row.costVersion(),new BigDecimal("3.5"),"客户确认历史成本")));
            assertEquals(1,first.adjustmentRecords());assertEquals(new BigDecimal("5.25"),first.amountDelta());
            row=runtime.service().rows(imported.inboundId()).get(0);
            assertEquals(0,cost(imported.inboundId(),List.of(new Cost(row.lineId(),row.costVersion(),new BigDecimal("3.5"),"重复保存"))).changedRows());
            InventoryStockItem stock=runtime.jdbc().queryForObject("SELECT * FROM inventory_stock_item WHERE source_inbound_item_id=?",BeanPropertyRowMapper.newInstance(InventoryStockItem.class),row.lineId());
            var issue=new InventoryTransaction();issue.setTransactionType("outbound");issue.setSourceType("TEST");issue.setSourceNo("TEST-MIG-ISSUE");issue.setSourceItemId(row.lineId());
            runtime.mutation().applyDelta(stock,new BigDecimal("-0.5"),issue);
            RowView refreshed=runtime.service().rows(imported.inboundId()).get(0);
            CostResult next=cost(imported.inboundId(),List.of(new Cost(refreshed.lineId(),refreshed.costVersion(),new BigDecimal("4"),"补正客户确认成本")));
            assertEquals(2,next.adjustmentRecords());assertEquals(new BigDecimal("0.50"),next.amountDelta());
            assertEquals(original,runtime.jdbc().queryForMap("SELECT * FROM inventory_transaction WHERE transaction_id=?",original.get("transaction_id")));
            assertEquals(0,BigDecimal.ONE.compareTo(runtime.service().rows(imported.inboundId()).get(0).remainingQuantity()));
            assertEquals(0,new BigDecimal("1.5").compareTo(runtime.service().rows(imported.inboundId()).get(0).receivedQuantity()));
            assertEquals(0,new BigDecimal("0.5").compareTo(runtime.service().rows(imported.inboundId()).get(0).issuedQuantity()));
            assertEquals(0,new BigDecimal("4").compareTo(runtime.jdbc().queryForObject("SELECT SUM(amount) FROM inventory_transaction WHERE batch_no=?",BigDecimal.class,stock.getBatchNo())));
            var valuation=runtime.stockMapper().costSummary(List.of(stock.getInventoryItemId()),com.jjx.inventory.enums.StockItemStatusEnum.ACTIVE.getValue());
            assertEquals(0,new BigDecimal("4").compareTo((BigDecimal)valuation.get("known_cost")));
            assertEquals(0,((Number)valuation.get("missing_cost_batches")).intValue());
            var history=runtime.service().costHistory(imported.inboundId(),row.lineId());
            assertEquals(3,history.size()); assertEquals("测试操作者",history.get(0).operator());
            assertTrue(history.stream().anyMatch(h -> h.beforeCost()==null && "客户确认历史成本".equals(h.reason())));
            assertThrows(BusinessException.class,() -> cost(imported.inboundId(),List.of(new Cost(refreshed.lineId(),refreshed.costVersion(),new BigDecimal("5"),"并发旧版本"))));
        }
        @Test void unknownOrCrossBatchLineCannotBeMaintainedAndFailureRollsBack() {
            Imported imported=importing(batch("1","M"));RowView row=runtime.service().rows(imported.inboundId()).get(0);
            assertThrows(BusinessException.class,() -> cost(imported.inboundId(),List.of(new Cost(row.lineId(),null,new BigDecimal("2"),"测试回滚"),new Cost(-1L,null,BigDecimal.ONE,"越界"))));
            manager.rollback(outer);outer=null;
            assertEquals(0,runtime.jdbc().queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE source_id=? AND source_type='MIGRATION_COST'",Integer.class,imported.inboundId()));
        }
        @Test void zeroBatchCanHaveCostAuditWithoutFakeQuantityMovement() {
            Imported imported=importing(batch("0","M"));RowView row=runtime.service().rows(imported.inboundId()).get(0);
            CostResult result=cost(imported.inboundId(),List.of(new Cost(row.lineId(),null,BigDecimal.ZERO,"客户确认实际零成本")));
            assertEquals(1,result.adjustmentRecords());assertEquals(0,result.amountDelta().signum());
            assertEquals(0,runtime.service().rows(imported.inboundId()).get(0).remainingQuantity().signum());
            assertEquals(0,runtime.jdbc().queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE batch_no=? AND quantity<>0",Integer.class,"MIG20261117-"+sha.substring(0,12)+"-2"));
        }
    }
}
