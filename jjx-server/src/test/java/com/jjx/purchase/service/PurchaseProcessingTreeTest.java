package com.jjx.purchase.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.purchase.domain.dto.*;
import com.jjx.purchase.domain.dto.PurchaseProcessingPlan.*;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.purchase.domain.enums.PurchaseProcessingOperation;
import com.jjx.purchase.domain.enums.PurchaseProcessingDelivery;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实后台几何/金额/收货规则，档案与收货历史替身；没有向业务库创建物料。 */
class PurchaseProcessingTreeTest {
    PurchaseProcessingTest f;
    @BeforeEach void setup(){f=new PurchaseProcessingTest();f.setup();f.material(701,"PCS","285","145");f.material(702,"PCS","315","250");}
    static BigDecimal bd(String n){return new BigDecimal(n);}
    static void eq(String n,BigDecimal a){assertNotNull(a);assertEquals(0,bd(n).compareTo(a));}
    Operation slit(int use,String width,int count){var op=new Operation();op.setType(PurchaseProcessingOperation.SLIT);op.setUseQuantity(use);var row=new Slit();row.setWidthMm(bd(width));row.setRolls(count);op.setSlits(List.of(row));return op;}
    Operation cut(int use,String width,String length,boolean rotated){var op=new Operation();op.setType(PurchaseProcessingOperation.CUT);op.setUseQuantity(use);op.setPieceWidthMm(bd(width));op.setPieceLengthMm(bd(length));op.setRotated(rotated);return op;}
    PurchaseOrderItemDTO item(){var item=f.order().getItems().get(0);var plan=new PurchaseProcessingPlan();plan.setTree(new Node());item.setProcessingPlan(f.processing.write(plan));return normalize(item);}
    PurchaseOrderItemDTO normalize(PurchaseOrderItemDTO i){var dto=new PurchaseOrderDTO();dto.setItems(List.of(i));return f.orders.previewPricing(dto).getItems().get(0);}
    Node tree(PurchaseOrderItemDTO i){return f.processing.parse(i.getProcessingPlan()).getTree();}
    Node find(Node n,String id){if(n.getNodeId().equals(id))return n;for(var c:n.getChildren()){var hit=find(c,id);if(hit!=null)return hit;}return null;}
    void saveTree(PurchaseOrderItemDTO i,Node tree){var p=new PurchaseProcessingPlan();p.setTree(tree);i.setProcessingPlan(f.processing.write(p));normalize(i);}
    PurchaseOrderItemDTO firstSlit(){var i=item();var tree=tree(i);tree.setOperation(slit(1,"285",2));saveTree(i,tree);return i;}
    PurchaseOrderItemDTO full(boolean bind){var i=firstSlit();var t=tree(i);find(t,"root-width").setOperation(slit(1,"315",1));saveTree(i,t);t=tree(i);find(t,"root-s0").setOperation(cut(2,"285","315",false));find(t,"root-width-s0").setOperation(cut(1,"285","315",true));saveTree(i,t);
        if(bind){t=tree(i);find(t,"root-s0-pieces").setMaterialId(501L);find(t,"root-s0-tail").setMaterialId(701L);find(t,"root-width-s0-pieces").setMaterialId(501L);find(t,"root-width-s0-tail").setMaterialId(702L);find(t,"root-width-width").setMaterialId(502L);saveTree(i,t);}return i;}
    PurchaseOrderItem source(PurchaseOrderItemDTO i){var s=new PurchaseOrderItem();org.springframework.beans.BeanUtils.copyProperties(i,s);s.setItemId(99L);s.setOrderId(77L);s.setReceivedQuantity(BigDecimal.ZERO);return s;}
    List<ReceiptGroup> all(PurchaseOrderItemDTO i){var rows=new ArrayList<ReceiptOutput>();for(var o:f.processing.parse(i.getProcessingPlan()).getGroups().get(0).getOutputs()){rows.add(f.receive(o.getOutputId(),o.getQuantity().toPlainString(),o.getRollLengthM()==null?null:o.getRollLengthM().toPlainString()));}var g=new ReceiptGroup();g.setGroupId("root");g.setOutputs(rows);g.setLossArea(BigDecimal.ZERO);return List.of(g);}
    @Test void firstSplitAutomaticallyKeeps650AndTwoUnprocessedRolls(){var i=firstSlit();var t=tree(i);eq("285",find(t,"root-s0").getWidthMm());eq("2",find(t,"root-s0").getQuantity());eq("650",find(t,"root-width").getWidthMm());eq("1",find(t,"root-width").getQuantity());eq("2",find(t,"root-unused").getQuantity());assertEquals(354L,find(t,"root-unused").getMaterialId());eq("366",i.getQuantity());eq("31110",i.getQuotedAmount());}
    @Test void splitThe650RemainderInto315And335KeepsParentRelationship(){var i=firstSlit();var t=tree(i);find(t,"root-width").setOperation(slit(1,"315",1));saveTree(i,t);t=tree(i);eq("315",find(t,"root-width-s0").getWidthMm());eq("335",find(t,"root-width-width").getWidthMm());assertEquals(2,find(t,"root-width").getChildren().size());assertFalse(f.processing.parse(i.getProcessingPlan()).getGroups().get(0).getOutputs().stream().anyMatch(o->o.getOutputId().equals("root-width")));}
    @Test void cutsCreate634And350PiecesWithExactTailShapesAnd984Total(){var i=full(false);var t=tree(i);eq("634",find(t,"root-s0-pieces").getQuantity());eq("145",find(t,"root-s0-tail").getLengthMm());eq("2",find(t,"root-s0-tail").getQuantity());eq("350",find(t,"root-width-s0-pieces").getQuantity());eq("315",find(t,"root-width-s0-tail").getWidthMm());eq("250",find(t,"root-width-s0-tail").getLengthMm());eq("984",find(t,"root-s0-pieces").getQuantity().add(find(t,"root-width-s0-pieces").getQuantity()));}
    @Test void upstreamChangesRecomputeRemaindersAndDownstreamAutomaticCounts(){var i=full(false);var t=tree(i);t.getOperation().getSlits().get(0).setWidthMm(bd("300"));saveTree(i,t);t=tree(i);eq("620",find(t,"root-width").getWidthMm());eq("305",find(t,"root-width-width").getWidthMm());eq("15",find(t,"root-s0-edge").getWidthMm());eq("634",find(t,"root-s0-pieces").getQuantity());}
    @Test void fewerUsedRollsAndPerRollCountLeaveTheRightUnprocessedQuantityAndTail(){var i=firstSlit();var t=tree(i);var cut=cut(1,"285","315",false);cut.setPiecesPerUnit(200);find(t,"root-s0").setOperation(cut);saveTree(i,t);t=tree(i);eq("200",find(t,"root-s0-pieces").getQuantity());eq("37000",find(t,"root-s0-tail").getLengthMm());eq("1",find(t,"root-s0-unused").getQuantity());eq("2",find(t,"root-unused").getQuantity());}
    @Test void forgedNodeDimensionsCountsAndLeafProjectionAreNeverTrusted(){var i=firstSlit();var p=f.processing.parse(i.getProcessingPlan());p.getTree().setWidthMm(bd("9999"));p.getTree().setQuantity(bd("99"));p.getTree().getChildren().get(0).setQuantity(bd("999"));p.setGroups(List.of());i.setProcessingPlan(f.processing.write(p));normalize(i);eq("1220",tree(i).getWidthMm());eq("3",tree(i).getQuantity());eq("2",find(tree(i),"root-s0").getQuantity());assertEquals(3,f.processing.parse(i.getProcessingPlan()).getGroups().get(0).getOutputs().size());}
    @Test void geometryMayBeSavedAsDraftButApprovalAndReceiptRequireLeafBindings(){var i=full(false);assertNotNull(i.getProcessingPlan());assertThrows(BusinessException.class,()->f.processing.requireTreeBindings(source(i)));assertThrows(BusinessException.class,()->f.processing.preview(source(i),all(i)));var bound=full(true);assertDoesNotThrow(()->f.processing.requireTreeBindings(source(bound)));}
    @Test void wholeReceiptIncludesOnlyTerminalPiecesTailsRemainderAndTwoOriginalRolls(){var i=full(true);var r=f.processing.preview(source(i),all(i));eq("366",r.receivedQuantity());assertEquals(7,r.items().size());eq("984",r.items().stream().filter(x->x.getMaterialId()==501L).map(x->x.getQuantity()).reduce(BigDecimal.ZERO,BigDecimal::add));eq("244",r.items().stream().filter(x->x.getMaterialId()==354L).map(x->x.getQuantity()).reduce(BigDecimal.ZERO,BigDecimal::add));eq("27530.97",r.items().stream().map(x->x.getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));assertTrue(r.items().stream().allMatch(x->x.getPurchaseProcessingJson().contains("nodePath")));}
    @Test void originalRollsAndPiecesCanBeReceivedSeparatelyWithoutTouchingIntermediateNodes(){var i=full(true);var s=source(i);var first=f.processing.preview(s,List.of(f.actual("root","0",f.receive("root-unused","2","100"))));eq("244",first.receivedQuantity());assertEquals(2,first.items().size());when(f.receipts.history(99L)).thenReturn(first.items().stream().map(x->x.getPurchaseProcessingJson()).toList());s.setReceivedQuantity(first.receivedQuantity());var second=f.processing.preview(s,List.of(f.actual("root","0",f.receive("root-s0-pieces","500",null))));eq("44.8875",second.receivedQuantity());assertEquals(1,second.items().size());assertThrows(BusinessException.class,()->f.processing.preview(s,List.of(f.actual("root","0",f.receive("root-unused","1","100")))));assertThrows(BusinessException.class,()->f.processing.preview(s,List.of(f.actual("root","0",f.receive("root-s0","1","100")))));}
    @Test void overWidthOverUseAndImpossibleCutDimensionsAreRejected(){var i=firstSlit();var t=tree(i);t.setOperation(slit(4,"285",2));assertThrows(BusinessException.class,()->saveTree(i,t));t.setOperation(slit(1,"700",2));assertThrows(BusinessException.class,()->saveTree(i,t));t.setOperation(cut(1,"2000","315",false));assertThrows(BusinessException.class,()->saveTree(i,t));}
    @Test void zeroRemainderIsOmittedAndRemovingOperationRestoresSourceLeaf(){var i=item();var t=tree(i);t.setOperation(slit(1,"1220",1));saveTree(i,t);assertNull(find(tree(i),"root-width"));t=tree(i);t.setOperation(null);saveTree(i,t);assertTrue(tree(i).getChildren().isEmpty());eq("3",tree(i).getQuantity());assertEquals(354L,tree(i).getMaterialId());}
    @Test void wrongLeafMaterialIsClearedAndCannotPassApproval(){var i=full(true);var t=tree(i);find(t,"root-s0-tail").setMaterialId(501L);saveTree(i,t);assertNull(find(tree(i),"root-s0-tail").getMaterialId());assertNotNull(find(tree(i),"root-s0-tail").getBindingMessage());assertThrows(BusinessException.class,()->f.processing.requireTreeBindings(source(i)));}
    @Test void sideAndPartialRowRemaindersConserveSourceArea(){var i=item();var t=tree(i);var op=cut(1,"300","300",false);op.setPiecesPerUnit(5);t.setOperation(op);saveTree(i,t);t=tree(i);eq("5",find(t,"root-pieces").getQuantity());eq("20",find(t,"root-edge").getWidthMm());eq("920",find(t,"root-partial").getWidthMm());eq("99400",find(t,"root-tail").getLengthMm());}
    @Test void generatedNodeCountAndDepthAreBoundedBeforeSaving(){
        var i=item();var t=tree(i);var many=slit(1,"1",1);many.setSlits(new ArrayList<>());for(int n=0;n<30;n++){var row=new Slit();row.setWidthMm(bd("1"));row.setRolls(1);many.getSlits().add(row);}t.setOperation(many);saveTree(i,t);t=tree(i);
        for(int n=0;n<30;n++){var op=slit(1,"0.01",1);op.setSlits(new ArrayList<>());for(int k=0;k<30;k++){var row=new Slit();row.setWidthMm(bd("0.01"));row.setRolls(1);op.getSlits().add(row);}find(t,"root-s"+n).setOperation(op);}
        final var wideTree=t;assertTrue(assertThrows(BusinessException.class,()->saveTree(i,wideTree)).getMessage().contains("300"));
        var deep=item();var root=tree(deep);var current=root;String id="root";for(int n=0;n<12;n++){current.setOperation(slit(1,"1220",1));var child=new Node();id+="-s0";child.setNodeId(id);current.setChildren(List.of(child));current=child;}
        assertTrue(assertThrows(BusinessException.class,()->saveTree(deep,root)).getMessage().contains("10层"));
    }
    @Test void splitCountsCannotCreateMoreThanSupportedInventoryQuantity(){
        var i=item();i.setPurchaseQuantity(bd("100000"));var t=tree(i);t.setOperation(slit(100000,"0.0001",10000));assertTrue(assertThrows(BusinessException.class,()->saveTree(i,t)).getMessage().contains("节点数量"));
    }

    PurchaseOrderItemDTO withScrapTails(){var i=full(true);var t=tree(i);for(String id:List.of("root-s0-tail","root-width-s0-tail")){var n=find(t,id);n.setDelivery(PurchaseProcessingDelivery.SCRAP);n.setDisposalReason("尾料不用，报废不回收");}saveTree(i,t);return i;}
    @Test void scrapLeavesRequireReasonButNoMaterialAndCanPassApproval(){
        var i=withScrapTails();assertDoesNotThrow(()->f.processing.requireTreeBindings(source(i)));
        assertNull(find(tree(i),"root-s0-tail").getMaterialId());
        var t=tree(i);find(t,"root-s0-tail").setDisposalReason(" ");assertThrows(BusinessException.class,()->saveTree(i,t));
    }
    @Test void scrapCannotBecomeAnIntermediateNodeAndChangedGeometryClearsOldDecision(){
        var i=withScrapTails();String saved=i.getProcessingPlan();var invalid=tree(i);find(invalid,"root-s0").setDelivery(PurchaseProcessingDelivery.SCRAP);find(invalid,"root-s0").setDisposalReason("不再加工");assertThrows(BusinessException.class,()->saveTree(i,invalid));
        i.setProcessingPlan(saved);var t=tree(i);t.getOperation().getSlits().get(0).setWidthMm(bd("300"));saveTree(i,t);assertEquals(PurchaseProcessingDelivery.INBOUND,find(tree(i),"root-s0-tail").getDelivery());assertNull(find(tree(i),"root-s0-tail").getDisposalReason());
    }
    @Test void mixedGoodsAndScrapSettle366ButOnlyGoodsCreateInboundRows(){
        var i=withScrapTails();var result=f.processing.preview(source(i),all(i));eq("366",result.receivedQuantity());assertEquals(5,result.items().size());assertEquals(2,result.disposals().size());
        assertTrue(result.items().stream().noneMatch(r->r.getMaterialId()==701L||r.getMaterialId()==702L));
        eq("0.1614",result.disposals().stream().map(r->r.getScrapArea()).reduce(BigDecimal.ZERO,BigDecimal::add));
        eq("0",result.disposals().stream().map(r->r.getSettledArea()).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertTrue(result.disposals().stream().allMatch(r->Boolean.TRUE.equals(r.getAllocatedToInbound())));
        eq("27530.97",result.items().stream().map(r->r.getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void pureScrapHasNoInboundRowsAndPersistsQuantityAreaCostForOveruseChecks(){
        var i=withScrapTails();var s=source(i);var one=f.processing.preview(s,List.of(f.actual("root","0",f.receive("root-s0-tail","1",null))));
        assertTrue(one.items().isEmpty());eq("0.0413",one.receivedQuantity());assertEquals(1,one.disposals().size());assertTrue(one.disposals().get(0).getNetCost().signum()>0);
        assertEquals(Boolean.FALSE,one.disposals().get(0).getAllocatedToInbound());
        when(f.dispositions.history(99L)).thenReturn(List.of(f.processing.write(one.disposals())));s.setReceivedQuantity(one.receivedQuantity());
        assertThrows(BusinessException.class,()->f.processing.preview(s,List.of(f.actual("root","0",f.receive("root-s0-tail","2",null)))));
        assertDoesNotThrow(()->f.processing.preview(s,List.of(f.actual("root","0",f.receive("root-s0-tail","1",null)))));
    }
    @Test void separateTailDisposalsThenGoodsCloseExactlyWithoutRoundingDrift(){
        var i=withScrapTails();var s=source(i);var disposed=new ArrayList<String>();
        var a=f.processing.preview(s,List.of(f.actual("root","0",f.receive("root-s0-tail","2",null))));disposed.add(f.processing.write(a.disposals()));s.setReceivedQuantity(a.receivedQuantity());when(f.dispositions.history(99L)).thenReturn(disposed);
        var b=f.processing.preview(s,List.of(f.actual("root","0",f.receive("root-width-s0-tail","1",null))));disposed.add(f.processing.write(b.disposals()));s.setReceivedQuantity(s.getReceivedQuantity().add(b.receivedQuantity()));
        eq("0.1614",s.getReceivedQuantity());
        var goods=all(i);goods.get(0).setOutputs(goods.get(0).getOutputs().stream().filter(o->!o.getOutputId().endsWith("tail")).toList());
        var c=f.processing.preview(s,goods);eq("366",s.getReceivedQuantity().add(c.receivedQuantity()));
        eq("27530.97",a.disposals().get(0).getNetCost().add(b.disposals().get(0).getNetCost()).add(c.items().stream().map(r->r.getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add)));
    }
    @Test void confirmedMixedScrapCannotBeRepeatedAndManualLossCannotDuplicateNodeScrap(){
        var i=withScrapTails();var result=f.processing.preview(source(i),all(i));when(f.receipts.history(99L)).thenReturn(result.items().stream().map(r->r.getPurchaseProcessingJson()).toList());when(f.dispositions.history(99L)).thenReturn(List.of(f.processing.write(result.disposals())));
        assertThrows(BusinessException.class,()->f.processing.preview(source(i),List.of(f.actual("root","0",f.receive("root-s0-tail","1",null)))));
        assertThrows(BusinessException.class,()->f.processing.preview(source(i),List.of(f.actual("root","0.1",f.receive("root-s0-tail","1",null)))));
    }

}
