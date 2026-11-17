package com.jjx.purchase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.purchase.domain.dto.*;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.purchase.mapper.PurchaseProcessingReceiptMapper;
import com.jjx.purchase.service.impl.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 当前真实数量/税费/加工收货服务，Mapper替身；样例交付余卷长度是明确构造的实收，不猜照片未写长度。 */
class PurchaseProcessingTest {
    InventoryMaterialMapper materials=mock(InventoryMaterialMapper.class);
    PurchaseProcessingReceiptMapper receipts=mock(PurchaseProcessingReceiptMapper.class);
    ObjectMapper json=new ObjectMapper();
    PurchaseProcessingService processing=new PurchaseProcessingService(materials,receipts,json,new PurchaseProcessingTreeService(materials));
    PurchaseOrderServiceImpl orders=mock(PurchaseOrderServiceImpl.class,CALLS_REAL_METHODS);
    static BigDecimal bd(String s){return new BigDecimal(s);}
    static void eq(String s,BigDecimal n){assertNotNull(n);assertEquals(0,bd(s).compareTo(n));}
    @BeforeEach void setup(){
        ReflectionTestUtils.setField(orders,"materialMapper",materials);ReflectionTestUtils.setField(orders,"processingService",processing);
        material(354,"㎡","1220","100000");material(501,"PCS","285","315");material(502,"㎡","335","100000");
        material(503,"㎡","365","100000");material(504,"PCS","290","315");material(505,"PCS","320","285");material(506,"㎡","320","100000");
        when(receipts.history(anyLong())).thenReturn(List.of());
    }
    InventoryMaterial material(long id,String unit,String w,String l){var m=new InventoryMaterial();m.setMaterialId(id);m.setMaterialCode("M"+id);m.setMaterialName(id==354?"EBA180":"交付物料"+id);m.setUnit(unit);m.setWidthMm(bd(w));m.setLengthMm(bd(l));m.setSpecification(w+"×"+l+"mm");m.setStatus(StatusEnum.NORMAL.getCode());when(materials.selectById(id)).thenReturn(m);return m;}
    PurchaseProcessingPlan.Output output(String id,long material,String unit,String q,String length){var o=new PurchaseProcessingPlan.Output();o.setOutputId(id);o.setMaterialId(material);o.setUnit(unit);o.setQuantity(bd(q));if(length!=null)o.setRollLengthM(bd(length));return o;}
    PurchaseProcessingPlan.Group group(String id,int[] widths,int[] rolls,PurchaseProcessingPlan.Output...outputs){var g=new PurchaseProcessingPlan.Group();g.setGroupId(id);g.setName("母卷"+id);g.setMotherRolls(1);g.setSlits(new ArrayList<>());for(int i=0;i<widths.length;i++){var s=new PurchaseProcessingPlan.Slit();s.setWidthMm(BigDecimal.valueOf(widths[i]));s.setRolls(rolls[i]);g.getSlits().add(s);}g.setOutputs(new ArrayList<>(List.of(outputs)));return g;}
    PurchaseProcessingPlan plan(){var p=new PurchaseProcessingPlan();p.setGroups(new ArrayList<>(List.of(
        group("g1",new int[]{285,315,335},new int[]{2,1,1},output("p1",501,"PCS","985",null),output("r1",502,"ROLL","1","100")),
        group("g2",new int[]{285,365},new int[]{3,1},output("p2",501,"PCS","952",null),output("r2",503,"ROLL","1","100")),
        group("g3",new int[]{320,290},new int[]{2,2},output("p3",504,"PCS","635",null),output("p4",505,"PCS","500",null),output("r3",506,"ROLL","2","28.7")))));return p;}
    PurchaseOrderDTO order(){var d=new PurchaseOrderDTO();var i=new PurchaseOrderItemDTO();i.setMaterialId(354L);i.setUnit("㎡");i.setPurchaseUnit("ROLL");i.setPriceUnit("㎡");i.setPurchaseQuantity(bd("3"));i.setRollWidthMm(bd("1220"));i.setRollLengthM(bd("100"));i.setUnitPrice(bd("85"));i.setTaxRate(bd("13"));i.setPriceTaxInclusive(true);i.setProcessingPlan(processing.write(plan()));d.setItems(List.of(i));return d;}
    PurchaseOrderItem source(){var priced=orders.previewPricing(order()).getItems().get(0);var source=new PurchaseOrderItem();org.springframework.beans.BeanUtils.copyProperties(priced,source);source.setItemId(99L);source.setOrderId(77L);source.setReceivedQuantity(BigDecimal.ZERO);return source;}
    PurchaseProcessingPlan.ReceiptOutput receive(String id,String q,String length){var r=new PurchaseProcessingPlan.ReceiptOutput();r.setOutputId(id);r.setQuantity(bd(q));if(length!=null)r.setRollLengthM(bd(length));return r;}
    PurchaseProcessingPlan.ReceiptGroup actual(String id,String loss,PurchaseProcessingPlan.ReceiptOutput...rows){var r=new PurchaseProcessingPlan.ReceiptGroup();r.setGroupId(id);r.setLossArea(bd(loss));if(r.getLossArea().signum()>0)r.setLossReason("实测加工损耗");r.setOutputs(List.of(rows));return r;}
    List<PurchaseProcessingPlan.ReceiptGroup> full(){return List.of(actual("g1","0.0716",receive("p1","985",null),receive("r1","1","100")),actual("g2","0.0342",receive("p2","952",null),receive("r2","1","100")),actual("g3","0.0247",receive("p3","635",null),receive("p4","500",null),receive("r3","2","28.7")));}
    @Test void photoThreeRolls366AreaAndGross31110AreNotTaxedTwice(){var d=orders.previewPricing(order());var i=d.getItems().get(0);eq("366",i.getQuantity());eq("31110",i.getQuotedAmount());eq("27530.97",d.getOrderAmount());eq("3579.03",d.getOrderTax());eq("31110",d.getOrderTotalAmount());assertEquals(3,processing.parse(i.getProcessingPlan()).getGroups().size());}
    @Test void untaxedOrdersKeepTheirOriginalTaxRule(){var d=order();d.getItems().get(0).setPriceTaxInclusive(false);orders.previewPricing(d);eq("31110",d.getOrderAmount());eq("4044.3",d.getOrderTax());eq("35154.3",d.getOrderTotalAmount());}
    @Test void fullReceiptCreatesOnlyActualPiecesAndSeparatePhysicalRemainders(){var s=source();var r=processing.preview(s,full());eq("366",r.receivedQuantity());assertEquals(8,r.items().size());assertTrue(r.items().stream().noneMatch(i->i.getMaterialId()==354));assertTrue(r.items().stream().allMatch(i->i.getSourcePurchaseItemId()==99));eq("27530.97",r.items().stream().map(i->i.getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));assertEquals(2,r.items().stream().filter(i->i.getMaterialId()==506).count());eq("1937",r.items().stream().filter(i->i.getMaterialId()==501).map(i->i.getQuantity()).reduce(BigDecimal.ZERO,BigDecimal::add));}
    @Test void partialReceiptDoesNotInventUnreceivedRemaindersOrWholeMotherRolls(){var r=processing.preview(source(),List.of(actual("g1","0",receive("p1","500",null))));eq("44.8875",r.receivedQuantity());assertEquals(1,r.items().size());eq("500",r.items().get(0).getQuantity());assertEquals(501L,r.items().get(0).getMaterialId());}
    @Test void repeatedPartialReceiptsConserveAreaAndEveryCent(){var s=source();var first=processing.preview(s,List.of(actual("g1","0",receive("p1","500",null))));when(receipts.history(99L)).thenReturn(first.items().stream().map(i->i.getPurchaseProcessingJson()).toList());s.setReceivedQuantity(first.receivedQuantity());var remaining=new ArrayList<>(full());remaining.set(0,actual("g1","0.0716",receive("p1","485",null),receive("r1","1","100")));var second=processing.preview(s,remaining);eq("366",first.receivedQuantity().add(second.receivedQuantity()));eq("27530.97",java.util.stream.Stream.concat(first.items().stream(),second.items().stream()).map(i->i.getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));}
    @Test void duplicateAndForeignGroupsOrOutputsAreRejected(){var s=source();var one=actual("g1","0",receive("p1","1",null));assertThrows(BusinessException.class,()->processing.preview(s,List.of(one,one)));assertThrows(BusinessException.class,()->processing.preview(s,List.of(actual("g1","0",receive("p1","1",null),receive("p1","1",null)))));assertThrows(BusinessException.class,()->processing.preview(s,List.of(actual("g1","0",receive("p2","1",null)))));assertThrows(BusinessException.class,()->processing.preview(s,List.of(actual("foreign","0",receive("p1","1",null)))));}
    @Test void excessPiecesFractionalRollsAndMissingLengthsAreRejected(){var s=source();assertThrows(BusinessException.class,()->processing.preview(s,List.of(actual("g1","0",receive("p1","986",null)))));assertThrows(BusinessException.class,()->processing.preview(s,List.of(actual("g1","0",receive("r1","0.5","100")))));assertThrows(BusinessException.class,()->processing.preview(s,List.of(actual("g1","0",receive("r1","1",null)))));assertThrows(BusinessException.class,()->processing.preview(s,List.of(actual("g1","0",receive("r1","1","101")))));}
    @Test void lossNeedsReasonAndCannotExceedMotherArea(){var s=source();var r=actual("g1","1",receive("p1","1",null));r.setLossReason("");assertThrows(BusinessException.class,()->processing.preview(s,List.of(r)));assertThrows(BusinessException.class,()->processing.preview(s,List.of(actual("g1","1000",receive("p1","1",null)))));}
    @Test void changedArchiveOrDisabledOutputCannotBeReceived(){var s=source();materials.selectById(501L).setWidthMm(bd("999"));assertThrows(BusinessException.class,()->processing.preview(s,List.of(actual("g1","0",receive("p1","1",null)))));materials.selectById(501L).setWidthMm(bd("285"));materials.selectById(501L).setStatus(StatusEnum.DISABLE.getCode());assertThrows(BusinessException.class,()->processing.preview(s,List.of(actual("g1","0",receive("p1","1",null)))));}
    @Test void cumulativeQuantityGuardsUseOriginalReceiptFacts(){var s=source();var first=processing.preview(s,List.of(actual("g1","0",receive("p1","500",null))));when(receipts.history(99L)).thenReturn(first.items().stream().map(i->i.getPurchaseProcessingJson()).toList());s.setReceivedQuantity(first.receivedQuantity());assertThrows(BusinessException.class,()->processing.preview(s,List.of(actual("g1","0",receive("p1","486",null)))));}
    @Test void dimensionsAndMetadataComeFromArchiveAndGroupsCannotExceedWidthOrRollCount(){var d=order();var p=plan();p.getGroups().get(0).getOutputs().get(0).setWidthMm(bd("1"));d.getItems().get(0).setProcessingPlan(processing.write(p));var normalized=orders.previewPricing(d).getItems().get(0);eq("285",processing.parse(normalized.getProcessingPlan()).getGroups().get(0).getOutputs().get(0).getWidthMm());p.getGroups().get(0).getSlits().get(0).setWidthMm(bd("1220"));d.getItems().get(0).setProcessingPlan(processing.write(p));final var tooWide=d;assertThrows(BusinessException.class,()->orders.previewPricing(tooWide));d=order();d.getItems().get(0).setPurchaseQuantity(bd("2"));final var fewer=d;assertThrows(BusinessException.class,()->orders.previewPricing(fewer));}
    @Test void physicalRollSplitPreservesFourDecimalQuantityInsteadOfRepeatingRoundedShare(){
        var d=order();var p=plan();p.getGroups().get(0).setOutputs(List.of(output("r1",502,"ROLL","3","100")));d.getItems().get(0).setProcessingPlan(processing.write(p));
        var priced=orders.previewPricing(d).getItems().get(0);var source=new PurchaseOrderItem();org.springframework.beans.BeanUtils.copyProperties(priced,source);source.setItemId(99L);source.setReceivedQuantity(BigDecimal.ZERO);
        var result=processing.preview(source,List.of(actual("g1","0",receive("r1","3","1.0001"))));
        eq("1.0051",result.receivedQuantity());assertEquals(3,result.items().size());eq("1.0051",result.items().stream().map(i->i.getQuantity()).reduce(BigDecimal.ZERO,BigDecimal::add));
    }

}
