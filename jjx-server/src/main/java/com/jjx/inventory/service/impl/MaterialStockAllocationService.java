package com.jjx.inventory.service.impl;

import com.jjx.inventory.enums.MaterialReserveStatusEnum;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.mapper.*;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.sales.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 订单归属明确的批次分配。只改变预留量；结存始终由流水变更服务负责。 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor=Exception.class)
public class MaterialStockAllocationService {
    private final InventoryStockItemMapper stocks;
    private final InventoryStockMapper summaries;
    private final OrderMaterialReserveMapper reserves;
    private final ProductionOrderMapper productionOrders;
    private final OrderMapper orders;

    public void allocate(OrderMaterialReserve template, BigDecimal demand) {
        BigDecimal remaining=demand;
        for (InventoryStockItem stock:stocks.selectFIFOAvailableForUpdate(template.getMaterialId())) {
            if (remaining.signum()<=0) break;
            BigDecimal available=zero(stock.getQuantity()).subtract(zero(stock.getReservedQuantity()));
            boolean roll=stock.getRollNo()!=null;
            if (available.signum()<=0 || (roll && zero(stock.getReservedQuantity()).signum()!=0)) continue;
            BigDecimal occupied=roll?stock.getQuantity():remaining.min(available);
            BigDecimal covered=remaining.min(occupied);
            OrderMaterialReserve row=new OrderMaterialReserve(); BeanUtils.copyProperties(template,row);
            row.setId(null);row.setStockItemId(stock.getItemId());row.setRollNo(stock.getRollNo());
            row.setReserveQuantity(occupied);row.setDemandQuantity(covered);row.setConsumedQuantity(BigDecimal.ZERO);
            if(stocks.addReserved(stock.getItemId(),occupied)!=1) throw new BusinessException("库存预留失败，请重试");
            reserves.insert(row); summaries.refreshSummaryByInventoryItemId(stock.getInventoryItemId());
            remaining=remaining.subtract(covered);
        }
        if(remaining.signum()>0) throw new BusinessException("物料"+template.getMaterialCode()+"可用库存不足，缺少 "+remaining);
    }

    public Long owner(InventoryOutboundOrder outbound) {
        if(outbound==null || outbound.getSourceId()==null) return null;
        if("sales_order".equals(outbound.getSourceType())) return outbound.getSourceId();
        if(!"work_order".equals(outbound.getSourceType())) return null;
        var production=productionOrders.selectById(outbound.getSourceId());
        return production==null?null:production.getSalesOrderId();
    }
    public boolean hasAllocation(InventoryOutboundOrder outbound,Long materialId) {
        Long owner=owner(outbound); if(owner==null) return false;
        return reserves.selectAllocations(owner).stream().anyMatch(r->Objects.equals(materialId,r.getMaterialId()) && (MaterialReserveStatusEnum.ACTIVE.getValue()==r.getStatus() || MaterialReserveStatusEnum.CONSUMED.getValue()==r.getStatus()));
    }

    /** 必须与实际出库处于同一事务；失败后预留消费一并回滚。 */
    public Map<Long,BigDecimal> consume(InventoryOutboundOrder outbound,InventoryOutboundItem item) {
        Map<Long,BigDecimal> consumed=new LinkedHashMap<>();
        Long owner=owner(outbound); if(owner==null) return consumed;
        orders.selectByIdForUpdate(owner);
        BigDecimal remaining=item.getQuantity();
        for(var row:reserves.selectAllocations(owner)) {
            if(MaterialReserveStatusEnum.ACTIVE.getValue()!=row.getStatus() || !Objects.equals(row.getMaterialId(),item.getMaterialId())) continue;
            if(item.getStockItemId()!=null && !Objects.equals(item.getStockItemId(),row.getStockItemId())) continue;
            if(item.getStockItemId()==null && row.getRollNo()!=null) continue;
            BigDecimal outstanding=zero(row.getReserveQuantity()).subtract(zero(row.getConsumedQuantity()));
            if(outstanding.signum()<=0) continue;
            var stock=stocks.selectByIdForUpdate(row.getStockItemId());
            if(stock==null) throw new BusinessException("预留批次不存在，请核实");
            BigDecimal used=remaining.min(outstanding);
            if(row.getRollNo()!=null && used.compareTo(outstanding)!=0) throw new BusinessException("预留卷必须按整卷实际数量领出");
            if(stocks.releaseReserved(stock.getItemId(),used)!=1) throw new BusinessException("本订单预留数量与库存不一致");
            row.setConsumedQuantity(zero(row.getConsumedQuantity()).add(used));
            if(used.compareTo(outstanding)==0) {row.setStatus(MaterialReserveStatusEnum.CONSUMED.getValue());row.setReleaseReason("本订单领料消费");row.setReleaseTime(LocalDateTime.now());}
            reserves.updateById(row);summaries.refreshSummaryByInventoryItemId(stock.getInventoryItemId());
            consumed.merge(stock.getItemId(),used,BigDecimal::add);
            remaining=remaining.subtract(used); if(remaining.signum()<=0) break;
        }
        return consumed;
    }

    public void release(OrderMaterialReserve row) {
        if(MaterialReserveStatusEnum.ACTIVE.getValue()!=row.getStatus() || row.getStockItemId()==null) return;
        var stock=stocks.selectByIdForUpdate(row.getStockItemId());
        BigDecimal remaining=zero(row.getReserveQuantity()).subtract(zero(row.getConsumedQuantity()));
        if(stock==null || (remaining.signum()>0 && stocks.releaseReserved(stock.getItemId(),remaining)!=1))
            throw new BusinessException("订单预留释放失败，批次数量不一致");
        summaries.refreshSummaryByInventoryItemId(stock.getInventoryItemId());
    }
    public BigDecimal ownedQuantity(InventoryOutboundOrder outbound,Long stockId) {
        Long owner=owner(outbound);if(owner==null)return BigDecimal.ZERO;
        return reserves.selectAllocations(owner).stream().filter(r->MaterialReserveStatusEnum.ACTIVE.getValue()==r.getStatus() && Objects.equals(stockId,r.getStockItemId()))
            .map(r->zero(r.getReserveQuantity()).subtract(zero(r.getConsumedQuantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    public BigDecimal protectedQuantity(Long stockItemId) {return zero(reserves.allocatedOnStock(stockItemId));}
    private static BigDecimal zero(BigDecimal value) {return value==null?BigDecimal.ZERO:value;}
}
