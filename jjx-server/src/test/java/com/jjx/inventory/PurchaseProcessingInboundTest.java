package com.jjx.inventory;

import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.*;
import com.jjx.purchase.domain.entity.*;
import com.jjx.purchase.mapper.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 隔离H2真实Mapper和事务：实收切片/独立余卷→IQC允收→applyDelta；失败整笔回滚，不代表运行态MySQL。 */
class PurchaseProcessingInboundTest extends RollFixture {
    PurchaseOrderItemMapper sources;
    @BeforeEach void sources(){
        var po=new PurchaseOrder();po.setOrderId(777L);po.setOrderNo("PO-PROCESS");po.setSupplierId(16L);po.setSupplierName("加工供方");
        var orders=mock(PurchaseOrderMapper.class);when(orders.selectById(777L)).thenReturn(po);
        sources=mock(PurchaseOrderItemMapper.class);var source=new PurchaseOrderItem();source.setItemId(99L);source.setOrderId(777L);source.setMaterialId(354L);when(sources.selectById(99L)).thenReturn(source);
        ReflectionTestUtils.setField(inboundTarget,"purchaseOrderMapper",orders);ReflectionTestUtils.setField(inboundTarget,"purchaseOrderItemMapper",sources);
        var material=new InventoryMaterial();material.setMaterialId(12L);material.setMaterialCode("CUT-12");material.setMaterialName("切片");material.setUnit("PCS");material.setWidthMm(bd("100"));material.setLengthMm(bd("200"));material.setStatus(StatusEnum.NORMAL.getCode());when(materials.selectById(12L)).thenReturn(material);
        var ii=new InventoryItem();ii.setInventoryItemId(32L);ii.setItemType(InventoryItemTypeEnum.MATERIAL.getCode());ii.setSourceId(12L);ii.setItemCode("CUT-12");ii.setUnit("PCS");when(inventoryItems.selectById(32L)).thenReturn(ii);
    }
    List<InventoryInboundItem> actual(){
        var rows=new ArrayList<InventoryInboundItem>();
        for(int n=0;n<3;n++){
            var row=new InventoryInboundItem();row.setSourcePurchaseItemId(99L);row.setMaterialId(n<2?11L:12L);row.setInventoryItemId(n<2?31L:32L);
            row.setMaterialCode(n<2?"AUX-TEST":"CUT-12");row.setMaterialName(n<2?"实际余卷":"实际切片");row.setUnit(n<2?"㎡":"PCS");row.setQuantity(bd(n<2?"9.75":"500"));row.setAmount(bd(n<2?"50":"100"));row.setUnitPrice(row.getAmount().divide(row.getQuantity(),4,java.math.RoundingMode.HALF_UP));
            row.setPurchaseProcessingJson("{\"groupId\":\"g1\",\"outputId\":\""+(n<2?"r1":"p1")+"\",\"quantity\":"+(n<2?"1":"500")+",\"settledArea\":9.75,\"netCost\":50}");
            if(n<2){row.setRollWidthMm(bd("195"));row.setRollLengthM(bd("50"));}rows.add(row);
        }return rows;
    }
    long receipt(){return inbound.createInboundRecordFromPurchase(777L,actual());}
    void iqc(long id){
        var order=ins.selectById(id);order.setOrderStatus(InventoryOrderStatusEnum.APPROVED.getValue());ins.updateById(order);
        for(var row:inItems.selectByInboundId(id)){
            long lot=900L+row.getItemId();row.setAcceptedQuantity(row.getQuantity());row.setPostedQuantity(BigDecimal.ZERO);row.setLotId(lot);inItems.updateById(row);
            var quality=new com.jjx.quality.domain.entity.QualityLot();quality.setLotId(lot);quality.setReviewStatus("APPROVED");quality.setPassQuantity(row.getQuantity());quality.setFailQuantity(BigDecimal.ZERO);quality.setStoredQuantity(BigDecimal.ZERO);
            when(qualityLots.selectById(lot)).thenReturn(quality);when(qualityService.getLot(lot)).thenReturn(quality);
        }
    }
    @Test void receiptCreatesActualSourcesWithoutStockUntilIqcThenPostsSeparateRollsAndPieces(){
        long id=receipt();var rows=inItems.selectByInboundId(id);assertEquals(3,rows.size());assertEquals(0,txs.selectCount(null));assertEquals(0,stocks.selectCount(null));
        assertTrue(rows.stream().allMatch(r->r.getSourcePurchaseItemId()==99L));assertTrue(rows.stream().noneMatch(r->r.getMaterialId()==354L));assertNotEquals(rows.get(0).getRollNo(),rows.get(1).getRollNo());assertNotNull(rows.get(0).getRollNo());
        iqc(id);assertTrue(inbound.confirm(id,1L,"test"));assertEquals(3,stocks.selectCount(null));assertEquals(3,txs.selectCount(null));
        eq("19.5",stocks.selectList(null).stream().filter(s->s.getMaterialId()==11L).map(s->s.getQuantity()).reduce(BigDecimal.ZERO,BigDecimal::add));eq("500",stocks.selectList(null).stream().filter(s->s.getMaterialId()==12L).findFirst().orElseThrow().getQuantity());eq("200",txs.selectList(null).stream().map(t->t.getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_stock_item s LEFT JOIN (SELECT stock_item_id,SUM(quantity) q FROM inventory_transaction GROUP BY stock_item_id) t ON t.stock_item_id=s.item_id WHERE s.quantity<>COALESCE(t.q,0)",Integer.class));
        assertFalse(inbound.confirm(id,1L,"test"));assertEquals(3,txs.selectCount(null));
    }
    @Test void failurePostingSecondRollRollsBackFirstRollLedgerAndAllPostedQuantities(){
        long id=receipt();iqc(id);String denied=inItems.selectByInboundId(id).get(1).getRollNo();jdbc.execute("ALTER TABLE inventory_transaction ADD CONSTRAINT deny_processing CHECK (roll_no <> '"+denied+"')");
        assertThrows(Exception.class,()->inbound.confirm(id,1L,"test"));assertEquals(0,stocks.selectCount(null));assertEquals(0,txs.selectCount(null));for(var row:inItems.selectByInboundId(id)){eq("0",row.getPostedQuantity());assertTrue(row.getQuantity().signum()>0);}
    }
    @Test void foreignPurchaseLineCannotCreateAnyInboundDocument(){var rows=actual();rows.get(0).setSourcePurchaseItemId(100L);assertThrows(BusinessException.class,()->inbound.createInboundRecordFromPurchase(777L,rows));assertEquals(0,ins.selectCount(null));assertEquals(0,inItems.selectCount(null));}
}
