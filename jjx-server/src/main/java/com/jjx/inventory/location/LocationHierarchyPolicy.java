package com.jjx.inventory.location;

import com.jjx.common.exception.BusinessException;
import com.jjx.common.util.UnitCodes;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryStorageLocation;
import com.jjx.inventory.enums.StorageLocationStatusEnum;
import com.jjx.inventory.mapper.InventoryItemMapper;
import com.jjx.inventory.mapper.InventoryStorageLocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

/** 库位结构与库存规则唯一入口；仓库行锁串行化结构修改、入账和容量判断。 */
@Service
@RequiredArgsConstructor
public class LocationHierarchyPolicy {
    private final LocationHierarchyMapper queries;
    private final InventoryStorageLocationMapper locations;
    private final InventoryItemMapper items;

    public void lockWarehouse(Long warehouseId) {
        if (warehouseId == null || queries.lockWarehouse(warehouseId) == null) throw new BusinessException("所属仓库不存在");
    }

    public Map<Long,InventoryStorageLocation> index(Long warehouseId) {
        var result = new LinkedHashMap<Long,InventoryStorageLocation>();
        queries.locations(warehouseId).forEach(row -> result.put(row.getLocationId(),row));
        return result;
    }

    public List<InventoryStorageLocation> ancestors(InventoryStorageLocation location, Map<Long,InventoryStorageLocation> tree) {
        var chain = new ArrayList<InventoryStorageLocation>(); var visited = new HashSet<Long>();
        for (var node=location; node!=null;) {
            if (node.getLocationId()!=null && !visited.add(node.getLocationId())) throw new BusinessException("库位上下级循环");
            chain.add(node);
            if (node.getParentId()==null) break;
            var parent=tree.get(node.getParentId());
            if (parent==null || !Objects.equals(parent.getWarehouseId(),location.getWarehouseId())) throw new BusinessException("上级库位不存在或不属于当前仓库");
            node=parent;
        }
        return chain;
    }

    public Set<Long> subtree(Long id, Map<Long,InventoryStorageLocation> tree) {
        var children=new HashMap<Long,List<Long>>();
        for(var node:tree.values())if(node.getParentId()!=null)children.computeIfAbsent(node.getParentId(),key->new ArrayList<>()).add(node.getLocationId());
        var ids=new HashSet<Long>();var pending=new ArrayDeque<Long>();pending.add(id);
        while(!pending.isEmpty()){Long next=pending.removeFirst();if(ids.add(next))pending.addAll(children.getOrDefault(next,List.of()));}
        return ids;
    }

    public String path(InventoryStorageLocation location,Map<Long,InventoryStorageLocation> tree){
        var chain=ancestors(location,tree);Collections.reverse(chain);
        return String.join(" / ",chain.stream().map(InventoryStorageLocation::getLocationName).toList());
    }

    public void validateParent(InventoryStorageLocation location, Map<Long,InventoryStorageLocation> tree) {
        for(var node:ancestors(location,tree)) {
            if(!StorageLocationStatusEnum.NORMAL.getCode().equals(node.getStatus()) && node!=location)
                throw new BusinessException("不能放在已停用的位置下面");
        }
    }

    public void validateIncoming(InventoryStockItem stock, BigDecimal delta) {
        if (stock.getLocationId()==null) return; // 保留历史未分配库位的账本口径。
        var location=locations.selectById(stock.getLocationId());
        if(location==null || !Objects.equals(location.getWarehouseId(),stock.getWarehouseId())) throw new BusinessException("库存库位不存在或仓库不匹配");
        if(delta.signum()<0)return;
        var tree=index(location.getWarehouseId());
        var chain=ancestors(location,tree);
        if(chain.stream().anyMatch(node -> !StorageLocationStatusEnum.NORMAL.getCode().equals(node.getStatus())))
            throw new BusinessException("库位或上级位置已停用");
        if(queries.children(location.getLocationId())>0) throw new BusinessException("库存只能登记在没有下级的末级位置");
        var item=items.selectById(stock.getInventoryItemId());
        if(item==null)throw new BusinessException("库存物品不存在");
        var balances=queries.balances(location.getWarehouseId());
        for(var node:chain) checkCapacity(node,subtree(node.getLocationId(),tree),balances,item.getUnit(),delta);
    }

    public void checkCapacity(InventoryStorageLocation node,Set<Long> ids,List<Map<String,Object>> balances,String incomingUnit,BigDecimal delta) {
        if(!Boolean.TRUE.equals(node.getCapacityEnabled()))return;
        if(node.getCapacity()==null || node.getCapacity().signum()<=0) throw new BusinessException("启用容量管理时须填写大于0的容量");
        String unit=UnitCodes.requireDictUnit(node.getCapacityUnit(),"容量单位");
        if(incomingUnit!=null && !unit.equals(UnitCodes.normalize(incomingUnit)))throw new BusinessException("库存记账单位与容量单位不同，无法直接比较");
        BigDecimal used=BigDecimal.ZERO;
        for(var row:balances) {
            Object key=row.get("location_id");
            if(key==null || !ids.contains(((Number)key).longValue()))continue;
            BigDecimal quantity=new BigDecimal(row.get("quantity").toString());
            if(quantity.signum()==0)continue;
            if(!unit.equals(UnitCodes.normalize(Objects.toString(row.get("unit"),"")))) throw new BusinessException("范围内存在不同单位库存，不能直接相加计算容量");
            used=used.add(quantity);
        }
        if(used.add(delta).compareTo(node.getCapacity())>0) throw new BusinessException("超过位置容量："+node.getLocationName());
    }
}
