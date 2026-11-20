package com.jjx.inventory.stocktake;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.enums.StatusEnum;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.*;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryStockMutationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import static com.jjx.inventory.stocktake.QuickStocktakeData.*;

/** 一次只盘一个明确库存行；既有盘点凭证承接审计，不修改原入出库单。 */
@Service
@RequiredArgsConstructor
public class QuickStocktakeService {
    private final QuickStocktakeMapper quick;
    private final InventoryStocktakeOrderMapper orders;
    private final InventoryStocktakeItemMapper details;
    private final InventoryStockItemMapper stocks;
    private final InventoryItemMapper identities;
    private final InventoryInboundItemMapper sourceItems;
    private final InventoryWarehouseMapper warehouses;
    private final InventoryStorageLocationMapper locations;
    private final InventoryStockMutationService mutation;

    public IPage<Batch> batches(Long inventoryItemId,Long warehouseId,Long locationId,int pageNum,int pageSize) {
        var item=identity(inventoryItemId);
        var filter=new LambdaQueryWrapper<InventoryStockItem>().eq(InventoryStockItem::getInventoryItemId,inventoryItemId)
            .eq(InventoryStockItem::getStatus,StockItemStatusEnum.ACTIVE.getValue())
            .eq(warehouseId!=null,InventoryStockItem::getWarehouseId,warehouseId)
            .eq(locationId!=null,InventoryStockItem::getLocationId,locationId).orderByAsc(InventoryStockItem::getItemId);
        var page=stocks.selectPage(new Page<InventoryStockItem>(Math.max(1,pageNum),Math.min(100,Math.max(1,pageSize))),filter);
        var result=new Page<Batch>(page.getCurrent(),page.getSize(),page.getTotal());
        var warehouseNames=new HashMap<Long,String>();var locationNames=new HashMap<Long,String>();
        result.setRecords(page.getRecords().stream().map(s->{
            String wn=warehouseNames.computeIfAbsent(s.getWarehouseId(),id->{var w=warehouses.selectById(id);return w==null?"—":w.getWarehouseName();});
            String ln=s.getLocationId()==null?"未分配":locationNames.computeIfAbsent(s.getLocationId(),id->{var l=locations.selectById(id);return l==null?"—":l.getLocationCode()+" · "+l.getLocationName();});
            return new Batch(s,item.getUnit(),wn,ln,fingerprint(s,item));
        }).toList());return result;
    }
    public IPage<Receipt> records(Long inventoryItemId,int pageNum,int pageSize) {
        identity(inventoryItemId);
        var p=quick.records(new Page<>(Math.max(1,pageNum),Math.min(100,Math.max(1,pageSize))),inventoryItemId);
        var result=new Page<Receipt>(p.getCurrent(),p.getSize(),p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::receipt).toList());return result;
    }
    public Preview preview(Save request) {
        if(request==null || request.stockItemId()==null)throw new BusinessException("请选择库存批次");
        amount(request.actualQuantity());
        var stock=stocks.selectById(request.stockItemId());var item=identity(request.inventoryItemId());validate(stock,item);
        if(!Objects.equals(stock.getInventoryItemId(),request.inventoryItemId()) || !fingerprint(stock,item).equals(request.fingerprint()))
            throw new BusinessException("库存已变化，请刷新批次并重新核对");
        if(request.actualQuantity().compareTo(zero(stock.getReservedQuantity()))<0)throw new BusinessException("实盘数量不能小于已预留数量");
        return new Preview(stock.getQuantity(),request.actualQuantity(),request.actualQuantity().subtract(stock.getQuantity()),item.getUnit());
    }
    @Transactional(rollbackFor=Exception.class)
    public Receipt save(Save request,Long operatorId,String operatorName,String account) {
        if(request==null || request.requestId()==null || !request.requestId().matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))throw new BusinessException("盘点请求标识无效");
        if(request.inventoryItemId()==null || request.stockItemId()==null || request.stockItemId()<=0 || request.inventoryItemId()<=0)
            throw new BusinessException("库存物品及批次必填");
        amount(request.actualQuantity());
        if(request.reason()==null || request.reason().isBlank() || request.reason().length()>300)throw new BusinessException("请填写300字以内的盘点原因");
        var found=stocks.selectById(request.stockItemId());if(found==null)throw new BusinessException("库存批次不存在");
        if(quick.lockWarehouse(found.getWarehouseId())==null)throw new BusinessException("仓库不存在");
        String signature=hash(String.join("|",Objects.toString(request.inventoryItemId(),""),Objects.toString(request.stockItemId(),""),
            Objects.toString(request.fingerprint(),""),request.actualQuantity().stripTrailingZeros().toPlainString(),request.reason().trim(),Objects.toString(operatorId,"")));
        String prefix="QST:"+request.requestId()+":";
        String documentNo="STQ"+request.requestId().replace("-","");
        var existing=quick.findRequest(documentNo);
        if(existing!=null) {
            if(!metadata(existing)[2].equals(signature))throw new BusinessException("同一请求标识的盘点内容已变化，请重新发起");
            return receipt(existing);
        }
        var stock=stocks.selectByIdForUpdate(request.stockItemId());
        var item=identity(request.inventoryItemId());validate(stock,item);
        if(!Objects.equals(stock.getInventoryItemId(),request.inventoryItemId()) || !fingerprint(stock,item).equals(request.fingerprint()))
            throw new BusinessException("库存已变化，请刷新批次并重新核对实盘数量");
        if(request.actualQuantity().compareTo(zero(stock.getReservedQuantity()))<0)throw new BusinessException("实盘数量不能小于已预留数量，请先处理预留");
        var now=LocalDateTime.now();var order=new InventoryStocktakeOrder();
        order.setStocktakeNo(documentNo);
        order.setStocktakeType("partial");order.setWarehouseId(stock.getWarehouseId());
        order.setLocationIds(stock.getLocationId()==null?"[]":"["+stock.getLocationId()+"]");
        order.setMaterialIds(InventoryItemTypeEnum.MATERIAL.getCode().equals(item.getItemType())?"["+item.getSourceId()+"]":"[]");
        order.setStocktakerId(operatorId);order.setStocktakerName(operatorName);order.setActualStartTime(now);order.setActualEndTime(now);
        order.setTotalSystemQuantity(stock.getQuantity());order.setTotalActualQuantity(request.actualQuantity());
        var delta=request.actualQuantity().subtract(stock.getQuantity());order.setTotalDiffQuantity(delta);
        order.setTotalDiffAmount(stock.getUnitCost()==null?null:stock.getUnitCost().multiply(delta));
        order.setOrderStatus(InventoryOrderStatusEnum.CONFIRMED.getValue());order.setApproveStatus(InventoryOrderStatusEnum.PENDING.getValue());
        order.setRemark(prefix+signature+":"+stock.getItemId()+":"+request.fingerprint());
        order.setCreateBy(account);order.setUpdateBy(account);orders.insert(order);
        if(stock.getUnitCost()==null)quick.markUnknownCost(order.getStocktakeId());
        var detail=new InventoryStocktakeItem();detail.setStocktakeId(order.getStocktakeId());
        detail.setInventoryItemId(stock.getInventoryItemId());detail.setMaterialId(stock.getMaterialId());
        detail.setMaterialCode(stock.getMaterialCode());detail.setMaterialName(stock.getMaterialName());detail.setBatchNo(stock.getBatchNo());detail.setLocationId(stock.getLocationId());
        detail.setSystemQuantity(stock.getQuantity());detail.setActualQuantity(request.actualQuantity());detail.setUnitCost(stock.getUnitCost());
        detail.setAdjustStatus(QuickAdjustStatus.PENDING.getValue());detail.setReason(request.reason().trim());
        detail.setStocktakeTime(now);detail.setStocktakeBy(operatorName);detail.setRemark(item.getUnit());
        detail.setCreateBy(account);detail.setUpdateBy(account);details.insert(detail);
        return receipt(order,detail);
    }
    @Transactional(rollbackFor=Exception.class)
    public Receipt confirm(Long id,Long operatorId,String operatorName,String account) {
        var before=orders.selectById(id);if(before==null)throw new BusinessException("盘点凭证不存在");metadata(before);
        if(quick.lockWarehouse(before.getWarehouseId())==null)throw new BusinessException("仓库不存在");
        var order=quick.lockOrder(id);metadata(order);
        if(InventoryOrderStatusEnum.PROCESSED.getValue().equals(order.getOrderStatus()))return receipt(order);
        if(!InventoryOrderStatusEnum.CONFIRMED.getValue().equals(order.getOrderStatus()))throw new BusinessException("盘点凭证状态不可过账");
        var detail=detail(order);Long stockId=Long.valueOf(metadata(order)[3]);
        var stock=stocks.selectByIdForUpdate(stockId);var item=identity(detail.getInventoryItemId());validate(stock,item);
        if(!Objects.equals(stock.getInventoryItemId(),detail.getInventoryItemId()) || !Objects.equals(stock.getWarehouseId(),order.getWarehouseId())
            || !Objects.equals(stock.getLocationId(),detail.getLocationId()) || !Objects.equals(stock.getBatchNo(),detail.getBatchNo())
            || !fingerprint(stock,item).equals(metadata(order)[4]))throw new BusinessException("保存后库存已变化，请作废此未生效凭证后重新盘点，原凭证保留");
        if(detail.getActualQuantity().compareTo(zero(stock.getReservedQuantity()))<0)throw new BusinessException("盘亏会侵占预留数量，请先处理预留");
        BigDecimal delta=detail.getActualQuantity().subtract(stock.getQuantity());
        if(delta.signum()!=0) {
            var tx=new InventoryTransaction();tx.setTransactionType(TransactionTypeEnum.ADJUST.getCode());
            tx.setSourceType("STOCKTAKE");tx.setSourceId(id);tx.setSourceNo(order.getStocktakeNo());tx.setSourceItemId(detail.getItemId());
            if(stock.getSourceInboundItemId()!=null) {
                var source=sourceItems.selectById(stock.getSourceInboundItemId());
                if(source!=null)tx.setLotId(source.getLotId());
            }
            tx.setUnitCost(stock.getUnitCost());tx.setOperatorId(operatorId);tx.setOperatorName(operatorName);
            tx.setRemark(detail.getReason());tx.setCreateBy(account);tx.setUpdateBy(account);
            mutation.applyDelta(stock,delta,tx);
        }
        detail.setAdjustStatus(QuickAdjustStatus.PROCESSED.getValue());detail.setUpdateBy(account);details.updateById(detail);
        order.setOrderStatus(InventoryOrderStatusEnum.PROCESSED.getValue());order.setApproveStatus(InventoryOrderStatusEnum.APPROVED.getValue());
        order.setApproverId(operatorId);order.setApproverName(operatorName);order.setApproveTime(LocalDateTime.now());order.setUpdateBy(account);orders.updateById(order);
        return receipt(order,detail);
    }
    @Transactional(rollbackFor=Exception.class)
    public Receipt cancel(Long id,String account) {
        var before=orders.selectById(id);metadata(before);
        if(quick.lockWarehouse(before.getWarehouseId())==null)throw new BusinessException("仓库不存在");
        var order=quick.lockOrder(id);metadata(order);
        if(InventoryOrderStatusEnum.CANCELLED.getValue().equals(order.getOrderStatus()))return receipt(order);
        if(!InventoryOrderStatusEnum.CONFIRMED.getValue().equals(order.getOrderStatus()))throw new BusinessException("仅未生效盘点凭证可作废；已生效记录须走冲销纠错");
        order.setOrderStatus(InventoryOrderStatusEnum.CANCELLED.getValue());order.setApproveStatus(InventoryOrderStatusEnum.CANCELLED.getValue());
        order.setUpdateBy(account);orders.updateById(order);return receipt(order);
    }
    private InventoryItem identity(Long id) {
        var item=id==null?null:identities.selectById(id);
        if(item==null || item.getUnit()==null)throw new BusinessException("库存物品不存在或记账单位缺失");return item;
    }
    private void validate(InventoryStockItem stock,InventoryItem item) {
        if(stock==null || !StockItemStatusEnum.ACTIVE.getValue().equals(stock.getStatus()) || !StatusEnum.NORMAL.getCode().equals(item.getStatus()))
            throw new BusinessException("库存批次或物品已停用，不可调整");
        if(stock.getBatchNo()==null || stock.getBatchNo().isBlank())throw new BusinessException("库存批次缺少追溯身份，不可直接调整");
    }
    public static String fingerprint(InventoryStockItem s,InventoryItem item) {
        return hash(Arrays.asList(s.getItemId(),s.getInventoryItemId(),s.getWarehouseId(),s.getLocationId(),s.getBatchNo(),s.getRollNo(),
            s.getQuantity(),s.getReservedQuantity(),s.getUnitCost(),s.getStatus(),s.getUpdateTime(),s.getSourceInboundItemId(),s.getMeasurementSnapshot(),s.getRollWidthMm(),s.getOriginalRollLengthM(),s.getIqcBatchId(),s.getMaterialId(),item.getUnit(),item.getStatus(),item.getItemType(),item.getSourceId()).toString());
    }
    private static void amount(BigDecimal n) {
        if(n==null || n.signum()<0 || n.stripTrailingZeros().scale()>4 || n.compareTo(new BigDecimal("99999999.9999"))>0)
            throw new BusinessException("实盘数量须非负，最多4位小数且不超出记账范围");
    }
    private static BigDecimal zero(BigDecimal n){return n==null?BigDecimal.ZERO:n;}
    private static String hash(String text) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private static String[] metadata(InventoryStocktakeOrder order) {
        if(order==null || order.getRemark()==null || !order.getRemark().startsWith("QST:"))throw new BusinessException("请通过原盘点流程操作此凭证");
        var parts=order.getRemark().split(":");if(parts.length!=5)throw new BusinessException("快捷盘点追溯信息不完整");return parts;
    }
    private InventoryStocktakeItem detail(InventoryStocktakeOrder o) {
        var rows=details.selectByStocktakeId(o.getStocktakeId());if(rows.size()!=1)throw new BusinessException("快捷盘点明细异常");return rows.getFirst();
    }
    private Receipt receipt(InventoryStocktakeOrder o){return receipt(o,detail(o));}
    private Receipt receipt(InventoryStocktakeOrder o,InventoryStocktakeItem d) {
        return new Receipt(o.getStocktakeId(),o.getStocktakeNo(),o.getOrderStatus(),d.getInventoryItemId(),Long.valueOf(metadata(o)[3]),
            d.getSystemQuantity(),d.getActualQuantity(),d.getActualQuantity().subtract(d.getSystemQuantity()),d.getRemark(),d.getReason());
    }
}
