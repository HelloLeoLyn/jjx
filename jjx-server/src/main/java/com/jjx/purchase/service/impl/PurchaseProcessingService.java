package com.jjx.purchase.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.util.UnitCodes;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.purchase.domain.dto.*;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.purchase.domain.model.PurchaseQuantityPricing;
import com.jjx.purchase.mapper.PurchaseProcessingReceiptMapper;
import lombok.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;

/** 采购行所属加工计划及实际交付校核；不操作库存，金额/面积规则只在后端实现。 */
@Service
@RequiredArgsConstructor
public class PurchaseProcessingService {
    private final InventoryMaterialMapper materials;
    private final PurchaseProcessingReceiptMapper receipts;
    private final ObjectMapper json;
    private final PurchaseProcessingTreeService trees;
    private static final BigDecimal ZERO=BigDecimal.ZERO;
    @Data public static class ReceiptFact {
        private String groupId;
        private String outputId;
        private String nodePath;
        private BigDecimal quantity;
        private BigDecimal rollLengthM;
        private BigDecimal settledArea;
        private BigDecimal netCost;
        private BigDecimal lossArea;
        private String lossReason;
    }
    public record ReceiptPreview(BigDecimal receivedQuantity,List<InventoryInboundItem> items) {}
    public record History(String processingPlan,List<ReceiptFact> receipts) {}
    public PurchaseProcessingPlan parse(String value) {
        if(value==null||value.isBlank())return null;
        if(value.length()>100000)throw new BusinessException("加工方案过长");
        try{return json.readValue(value,PurchaseProcessingPlan.class);}
        catch(Exception ex){throw new BusinessException("加工方案格式无效，请重新编辑");}
    }
    public String write(Object value) {
        try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("加工快照序列化失败",ex);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new BusinessException(message);}
    private static BigDecimal positive(BigDecimal n,String label){
        require(n!=null&&n.signum()>0,label+"须大于0");
        PurchaseQuantityPricing.bounded(n,4,8,label,true);return n;
    }
    private static void whole(BigDecimal n,String label){positive(n,label);require(n.stripTrailingZeros().scale()<=0,label+"须为整数");}
    private static void key(String key,String label){require(key!=null&&key.matches("[A-Za-z0-9-]{1,64}"),label+"无效");}
    private InventoryMaterial material(Long id){
        var m=id==null?null:materials.selectById(id);
        require(m!=null&&StatusEnum.NORMAL.getCode().equals(m.getStatus()),"请选择正常启用的交付物料");return m;
    }
    private static BigDecimal area(PurchaseProcessingPlan.Output output,BigDecimal qty,BigDecimal length){
        return PurchaseProcessingTreeService.area(output.getWidthMm(),UnitCodes.ROLL.equals(output.getUnit())?length.movePointRight(3):output.getLengthMm(),qty);
    }
    private static BigDecimal motherArea(PurchaseOrderItemDTO item,PurchaseProcessingPlan.Group group){
        return item.getRollWidthMm().multiply(item.getRollLengthM()).multiply(BigDecimal.valueOf(group.getMotherRolls())).movePointLeft(3);
    }
    /** 保存、预览共用；输出元数据以档案为准，调用者不能用名称或相同面积冒充物料。 */
    @Transactional(readOnly=true)
    public String normalize(PurchaseOrderItemDTO item){
        var plan=parse(item.getProcessingPlan());if(plan==null)return null;
        if(plan.getTree()!=null){trees.normalize(item,plan,false);return write(plan);}
        material(item.getMaterialId());
        require(UnitCodes.SQM.equals(UnitCodes.normalize(item.getUnit()))&&UnitCodes.ROLL.equals(item.getPurchaseUnit()),"加工方案须按母卷采购，母卷基本单位为㎡");
        positive(item.getRollWidthMm(),"母卷宽度");positive(item.getRollLengthM(),"母卷长度");
        whole(item.getPurchaseQuantity(),"母卷采购数量");
        require(plan.getGroups()!=null&&!plan.getGroups().isEmpty()&&plan.getGroups().size()<=100,"请填写1至100组母卷加工要求");
        var groupKeys=new HashSet<String>();var outputKeys=new HashSet<String>();int totalRolls=0;
        for(var g:plan.getGroups()){
            require(g!=null,"母卷分组不能为空");key(g.getGroupId(),"母卷分组标识");require(groupKeys.add(g.getGroupId()),"母卷分组重复");
            require(g.getName()!=null&&!g.getName().isBlank()&&g.getName().length()<=80,"请填写80字以内的母卷分组名称");
            require(g.getMotherRolls()!=null&&g.getMotherRolls()>0&&g.getMotherRolls()<=10000,"分组母卷数无效");totalRolls+=g.getMotherRolls();
            require(g.getSlits()!=null&&!g.getSlits().isEmpty()&&g.getSlits().size()<=100,"请填写分切宽度与卷数");
            BigDecimal widths=ZERO,maxWidth=ZERO;
            for(var slit:g.getSlits()){
                require(slit!=null&&slit.getRolls()!=null&&slit.getRolls()>0&&slit.getRolls()<=10000,"分切卷数无效");
                positive(slit.getWidthMm(),"分切宽度");widths=widths.add(slit.getWidthMm().multiply(BigDecimal.valueOf(slit.getRolls())));maxWidth=maxWidth.max(slit.getWidthMm());
            }
            require(widths.compareTo(item.getRollWidthMm())<=0,"每个母卷的分切宽度合计超过母卷宽度");
            require(g.getOutputs()!=null&&!g.getOutputs().isEmpty()&&g.getOutputs().size()<=100,"请填写切片或余卷交货要求");
            BigDecimal plannedArea=ZERO;
            for(var o:g.getOutputs()){
                require(o!=null,"交货要求不能为空");key(o.getOutputId(),"交付行标识");require(outputKeys.add(o.getOutputId()),"交付行标识重复");
                whole(o.getQuantity(),"计划交货片数/卷数");
                require(UnitCodes.PCS.equals(o.getUnit())||UnitCodes.ROLL.equals(o.getUnit()),"交货单位请选择片或卷");
                var m=material(o.getMaterialId());
                require(!Objects.equals(m.getMaterialId(),item.getMaterialId()),"加工交付须选择切片或余卷对应规格物料，不能仍选择母卷");
                String base=UnitCodes.requireDictUnit(m.getUnit(),"交付物料基本单位");
                require(UnitCodes.SQM.equals(base)||UnitCodes.PCS.equals(base),"加工交付物料基本单位须为㎡或片");
                require(!UnitCodes.ROLL.equals(o.getUnit())||UnitCodes.SQM.equals(base),"余卷物料基本单位须为㎡");
                positive(m.getWidthMm(),"交付物料档案宽度");
                o.setMaterialCode(m.getMaterialCode());o.setMaterialName(m.getMaterialName());o.setMaterialSpec(m.getSpecification());o.setWidthMm(m.getWidthMm());o.setLengthMm(m.getLengthMm());
                require(o.getWidthMm().compareTo(maxWidth)<=0,"交付宽度超过本组分切条带宽度");
                if(UnitCodes.ROLL.equals(o.getUnit())){
                    positive(o.getRollLengthM(),"余卷预计每卷长度");require(o.getRollLengthM().compareTo(item.getRollLengthM())<=0,"余卷预计长度超过母卷长度");
                }else{positive(o.getLengthMm(),"切片物料档案长度");o.setRollLengthM(null);}
                require(o.getRemark()==null||o.getRemark().length()<=300,"交货说明最多300字");
                plannedArea=plannedArea.add(area(o,o.getQuantity(),o.getRollLengthM()));
            }
            require(plannedArea.compareTo(motherArea(item,g))<=0,"本组计划交付面积超过母卷面积，请核对规格或数量");
        }
        require(BigDecimal.valueOf(totalRolls).compareTo(item.getPurchaseQuantity())==0,"分组母卷数合计须等于采购卷数");
        return write(plan);
    }
    /** 草稿可先安排几何，提交审核时终端交货物料必须明确；中间加工料不建库存身份。 */
    public void requireTreeBindings(PurchaseOrderItem item){
        var plan=parse(item.getProcessingPlan());if(plan==null||plan.getTree()==null)return;
        var dto=new PurchaseOrderItemDTO();org.springframework.beans.BeanUtils.copyProperties(item,dto);trees.normalize(dto,plan,true);
    }
    public List<ReceiptFact> facts(Long itemId){
        var facts=new ArrayList<ReceiptFact>();
        for(String raw:receipts.history(itemId))try{facts.add(json.readValue(raw,ReceiptFact.class));}
        catch(Exception ex){throw new BusinessException("原加工收货快照无效，不能继续收货");}
        return facts;
    }
    @Transactional(readOnly=true)
    public History history(PurchaseOrderItem item){return new History(item.getProcessingPlan(),facts(item.getItemId()));}
    @Transactional(readOnly=true)
    public ReceiptPreview preview(PurchaseOrderItem source,List<PurchaseProcessingPlan.ReceiptGroup> input){
        var plan=parse(source.getProcessingPlan());require(plan!=null,"该采购行没有加工方案");
        var mother=new PurchaseOrderItemDTO();org.springframework.beans.BeanUtils.copyProperties(source,mother);
        if(plan.getTree()!=null)trees.normalize(mother,plan,true);
        var past=facts(source.getItemId());
        require(input!=null&&!input.isEmpty()&&input.size()<=100,"请填写本次加工交货明细");
        var groupKeys=new HashSet<String>();var outputKeys=new HashSet<String>();var items=new ArrayList<InventoryInboundItem>();BigDecimal totalSettled=ZERO;
        for(var actual:input){
            require(actual!=null&&groupKeys.add(actual.getGroupId()),"本次母卷分组重复或无效");
            var group=plan.getGroups().stream().filter(g->g.getGroupId().equals(actual.getGroupId())).findFirst().orElseThrow(()->new BusinessException("母卷分组不属于该采购行"));
            require(actual.getOutputs()!=null&&!actual.getOutputs().isEmpty()&&actual.getOutputs().size()<=100,"本次分组须至少有一项实际交付，不能只登记损耗");
            BigDecimal loss=actual.getLossArea()==null?ZERO:actual.getLossArea();PurchaseQuantityPricing.bounded(loss,4,8,"损耗面积",false);
            require(loss.signum()==0||actual.getLossReason()!=null&&!actual.getLossReason().isBlank(),"有加工损耗时必须填写原因");
            require(actual.getLossReason()==null||actual.getLossReason().length()<=200,"损耗原因最多200字");
            var groupItems=new ArrayList<InventoryInboundItem>();var groupFacts=new ArrayList<ReceiptFact>();var areas=new ArrayList<BigDecimal>();BigDecimal totalArea=ZERO;
            for(var receive:actual.getOutputs()){
                require(receive!=null&&outputKeys.add(receive.getOutputId()),"本次交付行重复或无效");
                var output=group.getOutputs().stream().filter(o->o.getOutputId().equals(receive.getOutputId())).findFirst().orElseThrow(()->new BusinessException("交付行不属于该母卷分组"));
                whole(receive.getQuantity(),"实际交货片数/卷数");
                BigDecimal already=past.stream().filter(f->output.getOutputId().equals(f.getOutputId())).map(ReceiptFact::getQuantity).reduce(ZERO,BigDecimal::add);
                require(already.add(receive.getQuantity()).compareTo(output.getQuantity())<=0,"本次交付超过该规格的计划未收片数/卷数");
                BigDecimal length=receive.getRollLengthM();
                if(UnitCodes.ROLL.equals(output.getUnit())){require(receive.getQuantity().compareTo(BigDecimal.valueOf(500))<=0,"一次余卷收货最多500卷");positive(length,"余卷实际每卷长度");require(length.compareTo(output.getRollLengthM())<=0,"余卷实际长度超过计划，请先核对方案");}
                else require(length==null,"切片交货不能填写卷长");
                var m=material(output.getMaterialId());
                require(m.getWidthMm()!=null&&m.getWidthMm().compareTo(output.getWidthMm())==0,"交付档案宽度已变化，请核对方案");
                if(UnitCodes.PCS.equals(output.getUnit()))require(m.getLengthMm()!=null&&m.getLengthMm().compareTo(output.getLengthMm())==0,"交付档案长度已变化，请核对方案");
                BigDecimal a=area(output,receive.getQuantity(),length);totalArea=totalArea.add(a);areas.add(a);
                var row=new InventoryInboundItem();row.setSourcePurchaseItemId(source.getItemId());row.setMaterialId(m.getMaterialId());row.setMaterialCode(m.getMaterialCode());row.setMaterialName(m.getMaterialName());row.setSpecification(m.getSpecification());
                String base=UnitCodes.requireDictUnit(m.getUnit(),"交付物料基本单位");row.setUnit(base);
                require(UnitCodes.SQM.equals(base)||UnitCodes.PCS.equals(base)&&UnitCodes.PCS.equals(output.getUnit()),"交付物料单位已变化，请核对方案");
                BigDecimal basic=UnitCodes.SQM.equals(base)?a.setScale(4,RoundingMode.HALF_UP):receive.getQuantity();positive(basic,"实收基本数量");row.setQuantity(basic);
                if(UnitCodes.ROLL.equals(output.getUnit())){row.setRollWidthMm(output.getWidthMm());row.setRollLengthM(length);}
                String origin=output.getNodePath()==null?group.getName():output.getNodePath();
                if(origin.length()>330)origin=origin.substring(origin.length()-330);
                row.setRemark(origin+" · "+receive.getQuantity().stripTrailingZeros().toPlainString()+" "+output.getUnit()+"；来源采购行 "+source.getItemId());
                var f=new ReceiptFact();f.setGroupId(group.getGroupId());f.setOutputId(output.getOutputId());f.setNodePath(output.getNodePath());f.setQuantity(receive.getQuantity());f.setRollLengthM(length);groupItems.add(row);groupFacts.add(f);
            }
            BigDecimal settled=totalArea.add(loss).setScale(4,RoundingMode.HALF_UP);
            BigDecimal previous=past.stream().filter(f->group.getGroupId().equals(f.getGroupId())).map(ReceiptFact::getSettledArea).reduce(ZERO,BigDecimal::add);
            require(previous.add(settled).compareTo(motherArea(mother,group))<=0,"本组累计交付及损耗超过母卷面积");
            BigDecimal assignedArea=ZERO,cumulativeArea=ZERO;
            for(int i=0;i<groupItems.size();i++){
                boolean last=i==groupItems.size()-1;var row=groupItems.get(i);var f=groupFacts.get(i);
                cumulativeArea=cumulativeArea.add(areas.get(i));
                BigDecimal nextArea=last?settled:settled.multiply(cumulativeArea).divide(totalArea,4,RoundingMode.HALF_UP);
                BigDecimal allocated=nextArea.subtract(assignedArea);assignedArea=nextArea;
                f.setSettledArea(allocated);f.setLossArea(last?loss:ZERO);f.setLossReason(actual.getLossReason());row.setPurchaseProcessingJson(write(f));
            }
            totalSettled=totalSettled.add(settled);items.addAll(groupItems);
        }
        require(items.size()<=500,"一次加工收货最多500项");
        require(source.getQuantity()!=null&&totalSettled.add(source.getReceivedQuantity()==null?ZERO:source.getReceivedQuantity()).compareTo(source.getQuantity())<=0,"本次加工交付超过母卷采购未收数量");
        BigDecimal priorArea=past.stream().map(ReceiptFact::getSettledArea).reduce(ZERO,BigDecimal::add);
        BigDecimal priorCost=past.stream().map(ReceiptFact::getNetCost).reduce(ZERO,BigDecimal::add);
        BigDecimal batchCost=source.getAmount().multiply(priorArea.add(totalSettled)).divide(source.getQuantity(),2,RoundingMode.HALF_UP).subtract(priorCost);
        BigDecimal cumulative=ZERO,assigned=ZERO;
        for(int i=0;i<items.size();i++){
            var row=items.get(i);ReceiptFact f;
            try{f=json.readValue(row.getPurchaseProcessingJson(),ReceiptFact.class);}catch(Exception ex){throw new IllegalStateException(ex);}
            cumulative=cumulative.add(f.getSettledArea());
            BigDecimal next=i==items.size()-1?batchCost:batchCost.multiply(cumulative).divide(totalSettled,2,RoundingMode.HALF_UP);
            BigDecimal cost=next.subtract(assigned);assigned=next;
            require(cost.signum()>=0,"分摊成本不能为负，请核对原始收货凭证");f.setNetCost(cost);
            row.setAmount(cost);row.setUnitPrice(cost.divide(row.getQuantity(),4,RoundingMode.HALF_UP));row.setPurchaseProcessingJson(write(f));
        }
        // 每个余卷生成独立原始明细，仓库沿用既有逐卷入库与红冲，不把多卷揉成一个批次。
        var physicalItems=new ArrayList<InventoryInboundItem>();
        for(var row:items){
            ReceiptFact fact;
            try{fact=json.readValue(row.getPurchaseProcessingJson(),ReceiptFact.class);}catch(Exception ex){throw new IllegalStateException(ex);}
            int rolls=fact.getRollLengthM()==null?1:fact.getQuantity().intValueExact();
            BigDecimal allocatedArea=ZERO,allocatedCost=ZERO,allocatedQuantity=ZERO;
            for(int n=1;n<=rolls;n++){
                var physical=new InventoryInboundItem();org.springframework.beans.BeanUtils.copyProperties(row,physical);
                var f=new ReceiptFact();org.springframework.beans.BeanUtils.copyProperties(fact,f);
                if(fact.getRollLengthM()!=null){
                    f.setQuantity(BigDecimal.ONE);
                    BigDecimal nextQuantity=row.getQuantity().multiply(BigDecimal.valueOf(n)).divide(BigDecimal.valueOf(rolls),4,RoundingMode.HALF_UP);
                    physical.setQuantity(nextQuantity.subtract(allocatedQuantity));allocatedQuantity=nextQuantity;
                    require(physical.getQuantity().signum()>0,"余卷数量小于库存精度，请核对实际尺寸");
                    BigDecimal nextArea=fact.getSettledArea().multiply(BigDecimal.valueOf(n)).divide(BigDecimal.valueOf(rolls),4,RoundingMode.HALF_UP);
                    BigDecimal nextCost=fact.getNetCost().multiply(BigDecimal.valueOf(n)).divide(BigDecimal.valueOf(rolls),2,RoundingMode.HALF_UP);
                    f.setSettledArea(nextArea.subtract(allocatedArea));f.setNetCost(nextCost.subtract(allocatedCost));allocatedArea=nextArea;allocatedCost=nextCost;
                    f.setLossArea(n==rolls?fact.getLossArea():ZERO);
                    physical.setAmount(f.getNetCost());physical.setUnitPrice(f.getNetCost().divide(physical.getQuantity(),4,RoundingMode.HALF_UP));
                }
                physical.setPurchaseProcessingJson(write(f));physicalItems.add(physical);
            }
        }
        require(physicalItems.size()<=500,"一次实际交付最多500项，余卷按每卷计一项");
        return new ReceiptPreview(totalSettled,physicalItems);
    }
}
