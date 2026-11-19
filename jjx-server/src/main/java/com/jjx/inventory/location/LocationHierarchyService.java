package com.jjx.inventory.location;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.util.UnitCodes;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.enums.StorageLocationStatusEnum;
import com.jjx.inventory.enums.TransactionTypeEnum;
import com.jjx.inventory.enums.TransferItemStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryStockMutationService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LocationHierarchyService {
    /** 细分凭证直接过账；通用调拨入口不得再次审批、取消或改状态。 */
    public static void requireMutableTransfer(InventoryTransferOrder order) {
        if(order!=null && "location".equals(order.getTransferType()) && order.getTransferNo()!=null && order.getTransferNo().startsWith("LS"))
            throw new BusinessException("库位细分凭证已即时过账，不能再次提交、审批或取消；需要调整时请另建移库凭证");
    }
    private final LocationHierarchyMapper queries;
    private final LocationHierarchyPolicy policy;
    private final InventoryStorageLocationMapper locations;
    private final InventoryWarehouseMapper warehouses;
    private final InventoryStockItemMapper stocks;
    private final InventoryItemMapper items;
    private final InventoryStockMutationService mutations;
    private final InventoryTransferOrderMapper transfers;
    private final InventoryTransferItemMapper transferItems;
    private final SysOperLogMapper logs;
    private final ObjectMapper json;

    public record Balance(String unit,BigDecimal received,BigDecimal issued,BigDecimal quantity,BigDecimal reserved) { }
    public static class Node extends InventoryStorageLocation {
        public boolean leaf;
        public String path;
        public List<Balance> balances = new ArrayList<>();
        public List<Node> children = new ArrayList<>();
    }
    public record Allocation(Long stockItemId,int childIndex,BigDecimal quantity) { }
    public record Subdivision(List<InventoryStorageLocation> children,List<Allocation> allocations,Boolean confirmed) { }
    public record SubdivisionResult(Long transferId,String transferNo,List<Long> childIds) { }

    /** 所有树与收发存聚合在后端计算；不创建容量或库存的第二份真源。 */
    public List<Node> tree(Long warehouseId) {
        if(warehouseId==null || warehouses.selectById(warehouseId)==null)throw new BusinessException("请选择有效仓库");
        var index=policy.index(warehouseId); var nodes=new LinkedHashMap<Long,Node>();
        var balances=queries.balances(warehouseId); var flows=queries.flows(warehouseId);
        for(var location:index.values()) {
            var node=new Node(); BeanUtils.copyProperties(location,node); nodes.put(location.getLocationId(),node);
            node.path=policy.path(location,index);
            Set<Long> ids=policy.subtree(location.getLocationId(),index);
            var byUnit=new TreeMap<String,BigDecimal[]>();
            for(var row:balances) if(inScope(row,ids)) {
                var values=byUnit.computeIfAbsent(unit(row),key->zeroArray()); values[2]=values[2].add(decimal(row.get("quantity"))); values[3]=values[3].add(decimal(row.get("reserved")));
            }
            for(var row:flows) if(inScope(row,ids)) {
                var values=byUnit.computeIfAbsent(unit(row),key->zeroArray()); values[0]=values[0].add(decimal(row.get("received"))); values[1]=values[1].add(decimal(row.get("issued")));
            }
            byUnit.forEach((unit,values)->node.balances.add(new Balance(unit,values[0],values[1],values[2],values[3])));
            node.setUsedCapacity(null);
            if(Boolean.TRUE.equals(node.getCapacityEnabled()) && node.getCapacityUnit()!=null)
                node.setUsedCapacity(node.balances.stream().filter(value->node.getCapacityUnit().equals(value.unit())).map(Balance::quantity).reduce(BigDecimal.ZERO,BigDecimal::add));
        }
        var roots=new ArrayList<Node>();
        nodes.values().forEach(node->{if(node.getParentId()==null)roots.add(node);else nodes.get(node.getParentId()).children.add(node);});
        nodes.values().forEach(node->node.leaf=node.children.isEmpty());
        return roots;
    }
    private static BigDecimal[] zeroArray(){return new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO};}
    private static BigDecimal decimal(Object value){return value==null?BigDecimal.ZERO:new BigDecimal(value.toString());}
    private static String unit(Map<String,Object> row){return Objects.toString(UnitCodes.normalize(Objects.toString(row.get("unit"),"")),"未设单位");}
    private static boolean inScope(Map<String,Object> row,Set<Long> ids){return row.get("location_id") instanceof Number id && ids.contains(id.longValue());}

    public List<Map<String,Object>> inventory(Long id) {
        if(locations.selectById(id)==null)throw new BusinessException("库位不存在");
        var rows=queries.inventory(id);
        if(rows.size()>500)throw new BusinessException("当前库位批次超过500条，请先分批移库后再细分");
        return rows;
    }

    @Transactional(rollbackFor=Exception.class)
    public InventoryStorageLocation saveNode(InventoryStorageLocation draft,String operator) {
        if(draft==null)throw new BusinessException("库位不能为空");
        policy.lockWarehouse(draft.getWarehouseId());
        var old=draft.getLocationId()==null?null:locations.selectById(draft.getLocationId());
        if(draft.getLocationId()!=null && old==null)throw new BusinessException("库位不存在");
        if(old!=null && !Objects.equals(old.getWarehouseId(),draft.getWarehouseId())) throw new BusinessException("已有库位不能直接改所属仓库，请通过移库处理");
        var index=policy.index(draft.getWarehouseId());
        if(draft.getParentId()!=null && draft.getParentId()<=0)draft.setParentId(null);
        draft.setLocationName(required(draft.getLocationName(),"库位名称",100));
        if(draft.getLocationType()==null || draft.getLocationType().isBlank())draft.setLocationType("normal");
        if(draft.getLocationType().length()>20)throw new BusinessException("库位类型不能超过20字");
        if(draft.getStatus()==null)draft.setStatus(old==null?StorageLocationStatusEnum.NORMAL.getCode():old.getStatus());
        validateStatus(draft.getStatus());
        policy.validateParent(draft,index);
        if(draft.getParentId()!=null && (old==null || !Objects.equals(old.getParentId(),draft.getParentId())) && queries.occupied(draft.getParentId())>0)
            throw new BusinessException("上级位置已有库存或预留，请使用“细分并分配库存”");
        if(old!=null && queries.children(old.getLocationId())>0 && queries.occupied(old.getLocationId())>0)
            throw new BusinessException("非末级存在直接库存，请先处理库存位置");
        if(StorageLocationStatusEnum.DISABLED.getCode().equals(draft.getStatus()))ensureCanDisable(draft.getLocationId(),index);
        if(draft.getRemark()!=null && draft.getRemark().length()>500)throw new BusinessException("备注不能超过500字");
        positiveOrEmpty(draft.getCapacity(),"容量"); positiveOrEmpty(draft.getWidth(),"宽"); positiveOrEmpty(draft.getHeight(),"高"); positiveOrEmpty(draft.getDepth(),"深");
        draft.setCapacityEnabled(Boolean.TRUE.equals(draft.getCapacityEnabled()));
        if(Boolean.TRUE.equals(draft.getCapacityEnabled())) {
            draft.setCapacityUnit(UnitCodes.requireDictUnit(draft.getCapacityUnit(),"容量单位"));
            policy.checkCapacity(draft,old==null?Set.of():policy.subtree(old.getLocationId(),index),queries.balances(draft.getWarehouseId()),null,BigDecimal.ZERO);
        } else draft.setCapacityUnit(null);
        if(draft.getLocationCode()==null || draft.getLocationCode().isBlank()) {
            if(old!=null)draft.setLocationCode(old.getLocationCode());
            else {
                String prefix=warehouses.selectById(draft.getWarehouseId()).getWarehouseCode()+"-L";
                draft.setLocationCode(prefix+String.format("%06d",queries.codeFloor(prefix)+1));
            }
        }
        draft.setLocationCode(required(draft.getLocationCode(),"库位编码",50));
        if(locations.selectCount(new LambdaQueryWrapper<InventoryStorageLocation>().eq(InventoryStorageLocation::getLocationCode,draft.getLocationCode())
                .ne(old!=null,InventoryStorageLocation::getLocationId,old==null?null:old.getLocationId()))>0) throw new BusinessException("库位编码已存在");
        draft.setUpdateBy(operator);draft.setUpdateTime(LocalDateTime.now());
        draft.setUsedCapacity(old==null?BigDecimal.ZERO:old.getUsedCapacity());
        if(old==null){draft.setCreateBy(operator);draft.setCreateTime(LocalDateTime.now());locations.insert(draft);}
        else {
            // 显式允许清空上级/容量配置；其余历史字段由旧记录承接。
            var update=new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<InventoryStorageLocation>();
            update.eq("location_id",old.getLocationId()).set("parent_id",draft.getParentId()).set("capacity",draft.getCapacity())
                    .set("capacity_unit",draft.getCapacityUnit()).set("width",draft.getWidth()).set("height",draft.getHeight()).set("depth",draft.getDepth()).set("remark",draft.getRemark());
            if(locations.update(draft,update)!=1)throw new BusinessException("库位保存失败");
        }
        // 移动有容量限制的子树时同时核查新上级，不能通过改父级绕过限制。
        var next=policy.index(draft.getWarehouseId());
        for(var node:policy.ancestors(draft,next))policy.checkCapacity(node,policy.subtree(node.getLocationId(),next),queries.balances(draft.getWarehouseId()),null,BigDecimal.ZERO);
        return locations.selectById(draft.getLocationId());
    }
    private static String required(String value,String label,int max){if(value==null || value.isBlank() || value.length()>max)throw new BusinessException(label+"必填且最多"+max+"字");return value.trim();}
    private static void positiveOrEmpty(BigDecimal value,String label){if(value!=null && value.signum()<0)throw new BusinessException(label+"不能为负数");}
    private static void validateStatus(String status){if(!Set.of(StorageLocationStatusEnum.NORMAL.getCode(),StorageLocationStatusEnum.DISABLED.getCode()).contains(status))throw new BusinessException("库位状态无效");}
    private void ensureCanDisable(Long id,Map<Long,InventoryStorageLocation> index){if(id!=null)for(Long child:policy.subtree(id,index))if(queries.occupied(child)>0)throw new BusinessException("位置或下级存在库存/预留，不能停用");}

    @Transactional(rollbackFor=Exception.class)
    public void status(Long id,String status,String operator){var node=locations.selectById(id);if(node==null)throw new BusinessException("库位不存在");node.setStatus(status);saveNode(node,operator);}

    @Transactional(rollbackFor=Exception.class)
    public void batchStatus(List<Long> ids,String status,String operator){
        if(ids==null || ids.isEmpty() || ids.size()>1000)throw new BusinessException("请选择1至1000个位置");
        // 同批跨仓按仓库ID锁定，避免状态批量操作彼此逆序。
        var warehouseIds=new TreeSet<Long>();
        for(Long id:ids){var node=locations.selectById(id);if(node==null)throw new BusinessException("库位不存在");warehouseIds.add(node.getWarehouseId());}
        warehouseIds.forEach(policy::lockWarehouse);
        for(Long id:ids)status(id,status,operator);
    }

    @Transactional(rollbackFor=Exception.class)
    public void delete(Long id){
        var node=locations.selectById(id);if(node==null)throw new BusinessException("库位不存在");policy.lockWarehouse(node.getWarehouseId());
        if(queries.children(id)>0 || queries.batches(id)>0 || queries.history(id)>0 || queries.defaults(id)>0 || queries.summaryReferences(id)>0 || queries.inboundReferences(id)>0 || queries.outboundReferences(id)>0 || queries.transferReferences(id)>0)
            throw new BusinessException("该位置有下级、库存或历史引用，请保留记录；无库存时可停用");
        if(locations.deleteById(id)!=1)throw new BusinessException("库位删除失败");
    }

    /** 新增子位与移库同一事务；任何分配失败，子位、单据、余额、流水一起回滚。 */
    @Transactional(rollbackFor=Exception.class)
    public SubdivisionResult subdivide(Long id,Subdivision request,String operator,Long userId,String displayName){
        var parent=locations.selectById(id);if(parent==null)throw new BusinessException("上级库位不存在");policy.lockWarehouse(parent.getWarehouseId());
        parent=locations.selectById(id);
        if(!StorageLocationStatusEnum.NORMAL.getCode().equals(parent.getStatus()) || queries.children(id)>0)throw new BusinessException("请选择正常且尚无下级的位置进行细分");
        if(request==null || !Boolean.TRUE.equals(request.confirmed()) || request.children()==null || request.children().isEmpty() || request.children().size()>50)throw new BusinessException("请确认细分方案，子位置须为1至50个");
        var sourceRows=stocks.selectList(new LambdaQueryWrapper<InventoryStockItem>().eq(InventoryStockItem::getLocationId,id).orderByAsc(InventoryStockItem::getItemId).last("FOR UPDATE"));
        sourceRows=sourceRows.stream().filter(row->decimal(row.getQuantity()).signum()!=0 || decimal(row.getReservedQuantity()).signum()!=0).toList();
        if(sourceRows.size()>500)throw new BusinessException("批次过多，请先分批移库");
        var sourceIndex=new HashMap<Long,InventoryStockItem>();sourceRows.forEach(row->sourceIndex.put(row.getItemId(),row));
        var allocations=request.allocations()==null?List.<Allocation>of():request.allocations();
        if(allocations.size()>5000)throw new BusinessException("分配记录过多");
        var totals=new HashMap<Long,BigDecimal>();var pairs=new HashSet<String>();
        for(var allocation:allocations){
            if(allocation==null || !sourceIndex.containsKey(allocation.stockItemId()) || allocation.childIndex()<0 || allocation.childIndex()>=request.children().size()
                    || allocation.quantity()==null || allocation.quantity().signum()<=0 || allocation.quantity().stripTrailingZeros().scale()>4
                    || !pairs.add(allocation.stockItemId()+":"+allocation.childIndex()))throw new BusinessException("库存分配记录无效或重复");
            totals.merge(allocation.stockItemId(),allocation.quantity(),BigDecimal::add);
        }
        for(var source:sourceRows){
            if(decimal(source.getReservedQuantity()).signum()!=0)throw new BusinessException("批次存在预留，请先处理占用再细分："+source.getBatchNo());
            if(source.getQuantity().signum()<0 || totals.getOrDefault(source.getItemId(),BigDecimal.ZERO).compareTo(source.getQuantity())!=0)throw new BusinessException("每批分配合计须等于原库存："+source.getBatchNo());
            if(source.getRollNo()!=null && allocations.stream().filter(row->row.stockItemId().equals(source.getItemId())).count()!=1)throw new BusinessException("物理卷只能整卷移动，不能拆成多个位置");
        }
        // 已占用父节点不能走普通新增；先以同仓根节点建子位，移空原位后再挂接。
        var children=new ArrayList<InventoryStorageLocation>();
        for(var child:request.children()){
            if(child==null || child.getLocationId()!=null)throw new BusinessException("细分只能新建子位置");
            child.setWarehouseId(parent.getWarehouseId());child.setParentId(null);child.setStatus(StorageLocationStatusEnum.NORMAL.getCode());
            children.add(saveNode(child,operator));
        }
        InventoryTransferOrder order=null;
        if(!allocations.isEmpty()){
            order=new InventoryTransferOrder();order.setTransferNo("LS"+UUID.randomUUID().toString().replace("-",""));order.setTransferType("location");
            order.setFromWarehouseId(parent.getWarehouseId());order.setToWarehouseId(parent.getWarehouseId());order.setFromLocationId(id);
            order.setTransferDate(LocalDate.now());order.setActualDate(LocalDate.now());order.setOrderStatus(InventoryOrderStatusEnum.COMPLETED.getValue());order.setApproveStatus(InventoryOrderStatusEnum.APPROVED.getValue());
            order.setApproverId(userId);order.setApproverName(displayName);order.setApproveTime(LocalDateTime.now());order.setApproveRemark("用户确认库位细分方案");
            order.setOutOperator(displayName);order.setInOperator(displayName);order.setOutTime(LocalDateTime.now());order.setInTime(LocalDateTime.now());
            order.setCreateBy(operator);order.setCreateTime(LocalDateTime.now());order.setRemark("库位细分："+parent.getLocationName());transfers.insert(order);
            for(var allocation:allocations)move(sourceIndex.get(allocation.stockItemId()),children.get(allocation.childIndex()),allocation.quantity(),order,operator,userId,displayName);
            var units=new HashSet<String>();BigDecimal total=BigDecimal.ZERO;BigDecimal amount=BigDecimal.ZERO;boolean knownCost=true;
            for(var source:sourceRows){units.add(UnitCodes.normalize(items.selectById(source.getInventoryItemId()).getUnit()));total=total.add(source.getQuantity());if(source.getUnitCost()==null)knownCost=false;else amount=amount.add(source.getUnitCost().multiply(source.getQuantity()));}
            // 不同单位的数量不能求和；成本缺失不能冒充0，显式清空数据库默认值。
            transfers.update(null,new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<InventoryTransferOrder>().eq("transfer_id",order.getTransferId())
                    .set("total_quantity",units.size()==1?total:null).set("total_amount",knownCost?amount:null));
        }
        for(var child:children){child.setParentId(id);saveNode(child,operator);}
        var log=new SysOperLog();log.setModule("库位管理");log.setBusinessType(BusinessType.UPDATE.getCode());log.setBizType("storage_location");log.setBizId(id.toString());log.setAction("细分并分配库存");
        log.setUsername(operator);log.setUserId(userId);log.setRealName(displayName);log.setStatus(com.jjx.common.enums.StatusEnum.NORMAL.getCode());log.setCreateTime(LocalDateTime.now());
        try{log.setDetail(json.writeValueAsString(Map.of("children",children.stream().map(InventoryStorageLocation::getLocationId).toList(),"allocations",allocations,"transferNo",order==null?"无库存移库":order.getTransferNo())));}catch(Exception e){throw new BusinessException("细分日志生成失败");}
        if(logs.insert(log)!=1)throw new BusinessException("细分日志保存失败");
        return new SubdivisionResult(order==null?null:order.getTransferId(),order==null?null:order.getTransferNo(),children.stream().map(InventoryStorageLocation::getLocationId).toList());
    }

    private void move(InventoryStockItem source,InventoryStorageLocation target,BigDecimal quantity,InventoryTransferOrder order,String operator,Long userId,String name){
        var identity=items.selectById(source.getInventoryItemId());if(identity==null)throw new BusinessException("库存物品不存在");
        var line=new InventoryTransferItem();line.setTransferId(order.getTransferId());line.setInventoryItemId(source.getInventoryItemId());line.setMaterialId(source.getMaterialId());line.setMaterialCode(source.getMaterialCode());line.setMaterialName(source.getMaterialName());
        line.setSpecification(identity.getSpecification());line.setUnit(identity.getUnit());line.setQuantity(quantity);line.setOutQuantity(quantity);line.setInQuantity(quantity);line.setUnitCost(source.getUnitCost());
        line.setAmount(source.getUnitCost()==null?null:source.getUnitCost().multiply(quantity));line.setBatchNo(source.getBatchNo());line.setFromLocationId(source.getLocationId());line.setToLocationId(target.getLocationId());line.setStatus(TransferItemStatusEnum.COMPLETED.getValue());transferItems.insert(line);
        var lots=new TreeSet<Long>(queries.stockLots(source.getItemId()));
        if(source.getSourceInboundItemId()!=null){var lot=queries.sourceLot(source.getSourceInboundItemId());if(lot!=null)lots.add(lot);}
        if(lots.size()>1)throw new BusinessException("批次检验来源不唯一，请先核对原入库凭证");
        Long lot=lots.isEmpty()?null:lots.first();
        mutations.applyDelta(source,quantity.negate(),transaction(order,line,TransactionTypeEnum.TRANSFER_OUT,userId,name,lot));
        var destination=new InventoryStockItem();BeanUtils.copyProperties(source,destination);destination.setItemId(null);destination.setLocationId(target.getLocationId());destination.setQuantity(BigDecimal.ZERO);destination.setReservedQuantity(BigDecimal.ZERO);destination.setCreateTime(null);destination.setUpdateTime(null);
        mutations.applyDelta(destination,quantity,transaction(order,line,TransactionTypeEnum.TRANSFER_IN,userId,name,lot));
    }
    private InventoryTransaction transaction(InventoryTransferOrder order,InventoryTransferItem line,TransactionTypeEnum type,Long userId,String name,Long lot){
        var tx=new InventoryTransaction();tx.setLotId(lot);tx.setTransactionType(type.getCode());tx.setSourceType("INVENTORY_TRANSFER");tx.setSourceId(order.getTransferId());tx.setSourceNo(order.getTransferNo());tx.setSourceItemId(line.getItemId());tx.setOperatorId(userId);tx.setOperatorName(name);tx.setUnitCost(line.getUnitCost());tx.setRemark("库位细分移库");return tx;
    }
}
