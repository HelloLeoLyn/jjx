package com.jjx.purchase.service.impl;

import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.util.UnitCodes;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import com.jjx.purchase.domain.dto.PurchaseProcessingPlan;
import com.jjx.purchase.domain.dto.PurchaseProcessingPlan.*;
import com.jjx.purchase.domain.enums.PurchaseProcessingOperation;
import com.jjx.purchase.domain.model.PurchaseQuantityPricing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

/** 树几何唯一实现：客户端仅提供操作参数，节点尺寸/数量/余料/叶交付均由本类重建。 */
@Service
@RequiredArgsConstructor
public class PurchaseProcessingTreeService {
    private final InventoryMaterialMapper materials;
    private static final BigDecimal ZERO=BigDecimal.ZERO;
    private static void require(boolean ok,String message){if(!ok)throw new BusinessException(message);}
    private static BigDecimal positive(BigDecimal n,String label){
        require(n!=null&&n.signum()>0,label+"须大于0");PurchaseQuantityPricing.bounded(n,4,12,label,true);return n;
    }
    public static BigDecimal area(BigDecimal width,BigDecimal length,BigDecimal quantity){return width.multiply(length).multiply(quantity).movePointLeft(6);}
    private InventoryMaterial material(Long id){var m=id==null?null:materials.selectById(id);require(m!=null&&StatusEnum.NORMAL.getCode().equals(m.getStatus()),"加工树请选择正常启用的物料");return m;}
    public void normalize(PurchaseOrderItemDTO item,PurchaseProcessingPlan plan,boolean bindingsRequired){
        require(UnitCodes.SQM.equals(UnitCodes.normalize(item.getUnit()))&&UnitCodes.ROLL.equals(item.getPurchaseUnit()),"加工树须按母卷采购，基本单位为㎡");
        positive(item.getPurchaseQuantity(),"采购卷数");require(item.getPurchaseQuantity().stripTrailingZeros().scale()<=0&&item.getPurchaseQuantity().compareTo(BigDecimal.valueOf(100000))<=0,"采购卷数须为1至100000的整数");
        positive(item.getRollWidthMm(),"母卷宽度");positive(item.getRollLengthM(),"母卷长度");material(item.getMaterialId());
        var root=plan.getTree();require(root!=null,"加工树不能为空");
        set(root,"root","采购原卷",UnitCodes.ROLL,item.getRollWidthMm(),item.getRollLengthM().movePointRight(3),item.getPurchaseQuantity());
        var counter=new int[]{0};rebuild(root,item,bindingsRequired,0,counter);
        var group=new Group();group.setGroupId(root.getNodeId());group.setName("最终交付（原卷 / 切片 / 余料）");group.setMotherRolls(item.getPurchaseQuantity().intValueExact());group.setSlits(List.of());
        var outputs=new ArrayList<Output>();collect(root,"",outputs);require(outputs.size()<=100,"最终交货规格最多100项，请分采购行安排");group.setOutputs(outputs);plan.setGroups(List.of(group));
        BigDecimal leaves=outputs.stream().map(o->area(o.getWidthMm(),o.getUnit().equals(UnitCodes.ROLL)?o.getRollLengthM().movePointRight(3):o.getLengthMm(),o.getQuantity())).reduce(ZERO,BigDecimal::add);
        require(leaves.compareTo(area(root.getWidthMm(),root.getLengthMm(),root.getQuantity()))==0,"加工树面积不守恒，请核对操作参数");
    }
    private void set(Node n,String id,String role,String unit,BigDecimal width,BigDecimal length,BigDecimal qty){n.setNodeId(id);n.setRole(role);n.setUnit(unit);n.setWidthMm(width);n.setLengthMm(length);n.setQuantity(qty);}
    private void rebuild(Node node,PurchaseOrderItemDTO item,boolean bindingsRequired,int depth,int[] counter){
        require(depth<=10&&++counter[0]<=300,"加工树最多10层、300个节点");
        require(node.getRemark()==null||node.getRemark().length()<=200,"节点说明最多200字");
        var old=new HashMap<String,Node>();if(node.getChildren()!=null){require(node.getChildren().size()<=100,"单层节点最多100项");for(var c:node.getChildren())if(c!=null&&c.getNodeId()!=null)require(old.put(c.getNodeId(),c)==null,"子节点标识重复");}
        var op=node.getOperation();
        if(op==null){node.setChildren(List.of());bind(node,item,bindingsRequired);return;}
        require(op.getType()!=null,"请选择分切或切片");require(op.getUseQuantity()!=null&&op.getUseQuantity()>0&&BigDecimal.valueOf(op.getUseQuantity()).compareTo(node.getQuantity())<=0,"本次加工数量超过来源卷数/块数");
        var children=new ArrayList<Node>();BigDecimal used=BigDecimal.valueOf(op.getUseQuantity());
        if(op.getType()==PurchaseProcessingOperation.SLIT){
            require(op.getSlits()!=null&&!op.getSlits().isEmpty()&&op.getSlits().size()<=30,"请填写1至30种分切宽度");BigDecimal taken=ZERO;int index=0;
            for(var slit:op.getSlits()){
                require(slit!=null&&slit.getRolls()!=null&&slit.getRolls()>0&&slit.getRolls()<=10000,"每卷/块分切份数须为正整数");positive(slit.getWidthMm(),"分切宽度");taken=taken.add(slit.getWidthMm().multiply(BigDecimal.valueOf(slit.getRolls())));
                child(node,"s"+index++,"分切料",node.getUnit(),slit.getWidthMm(),node.getLengthMm(),used.multiply(BigDecimal.valueOf(slit.getRolls())),old,children);
            }
            require(taken.compareTo(node.getWidthMm())<=0,"分切宽度合计超过来源宽度");
            child(node,"width","宽度余料",node.getUnit(),node.getWidthMm().subtract(taken),node.getLengthMm(),used,old,children);
        }else{
            BigDecimal requestedWidth=positive(op.getPieceWidthMm(),"切片宽度"),requestedLength=positive(op.getPieceLengthMm(),"切片长度");
            BigDecimal layoutWidth=Boolean.TRUE.equals(op.getRotated())?requestedLength:requestedWidth,layoutLength=Boolean.TRUE.equals(op.getRotated())?requestedWidth:requestedLength;
            BigDecimal columns=node.getWidthMm().divideToIntegralValue(layoutWidth),rows=node.getLengthMm().divideToIntegralValue(layoutLength);
            BigDecimal capacity=columns.multiply(rows);require(capacity.signum()>0,"切片尺寸或方向超出来源尺寸");
            BigDecimal count=op.getPiecesPerUnit()==null?capacity:BigDecimal.valueOf(op.getPiecesPerUnit());require(count.signum()>0&&count.compareTo(capacity)<=0,"每卷/块切片数量超过可切数量");
            require(count.multiply(used).compareTo(BigDecimal.valueOf(100000000))<0,"切片数量超出支持范围");
            child(node,"pieces","切片",UnitCodes.PCS,requestedWidth,requestedLength,count.multiply(used),old,children);
            BigDecimal fullRows=count.divideToIntegralValue(columns),partialColumns=count.remainder(columns);
            child(node,"edge","边料",UnitCodes.PCS,node.getWidthMm().subtract(columns.multiply(layoutWidth)),fullRows.multiply(layoutLength),used,old,children);
            BigDecimal occupiedLength=fullRows.multiply(layoutLength);
            if(partialColumns.signum()>0){
                child(node,"partial","边料",UnitCodes.PCS,node.getWidthMm().subtract(partialColumns.multiply(layoutWidth)),layoutLength,used,old,children);
                occupiedLength=occupiedLength.add(layoutLength);
            }
            child(node,"tail","尾料",UnitCodes.PCS,node.getWidthMm(),node.getLengthMm().subtract(occupiedLength),used,old,children);
        }
        child(node,"unused","未加工原料",node.getUnit(),node.getWidthMm(),node.getLengthMm(),node.getQuantity().subtract(used),old,children);
        node.setChildren(children);node.setMaterialId(null);node.setMaterialCode(null);node.setMaterialName(null);node.setBindingMessage(null);
        for(var child:children)rebuild(child,item,bindingsRequired,depth+1,counter);
    }
    private void child(Node parent,String suffix,String role,String unit,BigDecimal width,BigDecimal length,BigDecimal qty,Map<String,Node> old,List<Node> children){
        if(width.signum()==0||length.signum()==0||qty.signum()==0)return;require(width.signum()>0&&length.signum()>0&&qty.signum()>0,"加工参数导致负余料");
        PurchaseQuantityPricing.bounded(qty,0,8,"节点数量",true);
        String id=parent.getNodeId()+"-"+suffix;require(id.length()<=128,"加工树路径过长");
        var n=old.getOrDefault(id,new Node());set(n,id,role,unit,width,length,qty);children.add(n);
    }
    private void bind(Node n,PurchaseOrderItemDTO item,boolean required){
        boolean original=UnitCodes.ROLL.equals(n.getUnit())&&n.getWidthMm().compareTo(item.getRollWidthMm())==0&&n.getLengthMm().compareTo(item.getRollLengthM().movePointRight(3))==0;
        if(original)n.setMaterialId(item.getMaterialId());
        n.setMaterialCode(null);n.setMaterialName(null);n.setBindingMessage(null);
        if(n.getMaterialId()!=null){
            var m=material(n.getMaterialId());String base=UnitCodes.normalize(m.getUnit());
            boolean match=m.getWidthMm()!=null&&m.getWidthMm().compareTo(n.getWidthMm())==0
                &&(UnitCodes.ROLL.equals(n.getUnit())?UnitCodes.SQM.equals(base):m.getLengthMm()!=null&&m.getLengthMm().compareTo(n.getLengthMm())==0&&(UnitCodes.SQM.equals(base)||UnitCodes.PCS.equals(base)));
            if(match){n.setMaterialCode(m.getMaterialCode());n.setMaterialName(m.getMaterialName());}
            else{n.setMaterialId(null);n.setBindingMessage("规格变化或物料不匹配，请重新指定终端交货物料");}
        }
        require(!required||n.getMaterialId()!=null,"终端规格 "+n.getWidthMm().stripTrailingZeros().toPlainString()+"×"+n.getLengthMm().stripTrailingZeros().toPlainString()+"mm 尚未指定匹配的交货物料");
    }
    private void collect(Node n,String path,List<Output> outputs){
        String label=n.getRole()+" "+n.getWidthMm().stripTrailingZeros().toPlainString()+"×"+n.getLengthMm().stripTrailingZeros().toPlainString()+"mm";
        String full=path.isEmpty()?label:path+" / "+label;
        if(n.getChildren()!=null&&!n.getChildren().isEmpty()){for(var c:n.getChildren())collect(c,full,outputs);return;}
        var o=new Output();o.setOutputId(n.getNodeId());o.setNodePath(full);o.setMaterialId(n.getMaterialId());o.setMaterialCode(n.getMaterialCode());o.setMaterialName(n.getMaterialName());
        o.setMaterialSpec(n.getWidthMm().stripTrailingZeros().toPlainString()+"×"+n.getLengthMm().stripTrailingZeros().toPlainString()+"mm");o.setUnit(n.getUnit());o.setQuantity(n.getQuantity());o.setWidthMm(n.getWidthMm());o.setLengthMm(n.getLengthMm());
        if(UnitCodes.ROLL.equals(n.getUnit()))o.setRollLengthM(n.getLengthMm().movePointLeft(3));o.setRemark(n.getRemark());outputs.add(o);
    }
}
