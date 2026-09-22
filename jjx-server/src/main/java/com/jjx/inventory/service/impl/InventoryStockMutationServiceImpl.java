package com.jjx.inventory.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.service.InventoryStockMutationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InventoryStockMutationServiceImpl implements InventoryStockMutationService {

    private final InventoryStockItemMapper stockItemMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryTransactionMapper transactionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryStockItem applyDelta(InventoryStockItem stock, BigDecimal delta, InventoryTransaction transaction) {
        if (stock == null || stock.getInventoryItemId() == null) {
            throw new BusinessException("库存变动缺少批次或库存物品身份");
        }
        if (delta == null || delta.signum() == 0) {
            throw new BusinessException("库存变动数量不能为 0");
        }
        if (transaction == null || transaction.getTransactionType() == null) {
            throw new BusinessException("库存变动必须携带流水业务类型");
        }

        if (stock.getItemId() != null) {
            Long requestedInventoryItemId = stock.getInventoryItemId();
            InventoryStockItem locked = stockItemMapper.selectByIdForUpdate(stock.getItemId());
            if (locked == null || (locked.getInventoryItemId() != null
                    && !requestedInventoryItemId.equals(locked.getInventoryItemId()))) {
                throw new BusinessException("库存批次不存在或身份已变化");
            }
            if (locked.getInventoryItemId() == null) locked.setInventoryItemId(requestedInventoryItemId);
            stock = locked;
        }
        BigDecimal before = stock.getItemId() == null ? BigDecimal.ZERO : value(stock.getQuantity());
        BigDecimal after = before.add(delta);
        if (after.signum() < 0) {
            throw new BusinessException("库存变动后不能为负数（批次 " + stock.getBatchNo()
                    + "，当前 " + before.toPlainString() + "，变动 " + delta.toPlainString() + "）");
        }

        stock.setQuantity(after);
        if (delta.signum() > 0) {
            stock.setLastInboundTime(LocalDateTime.now());
        } else {
            stock.setLastOutboundTime(LocalDateTime.now());
        }
        if (stock.getItemId() == null) {
            stock.setReservedQuantity(value(stock.getReservedQuantity()));
            stock.setStatus(1);
            stockItemMapper.insert(stock);
        } else {
            stockItemMapper.updateById(stock);
        }

        stockMapper.refreshSummaryByInventoryItemId(stock.getInventoryItemId());

        fillTransactionIdentity(transaction, stock);
        transaction.setQuantity(delta);
        transaction.setBeforeQuantity(before);
        transaction.setAfterQuantity(after);
        transaction.setTransactionTime(transaction.getTransactionTime() == null
                ? LocalDateTime.now() : transaction.getTransactionTime());
        if (transaction.getAmount() == null && transaction.getUnitCost() != null) {
            transaction.setAmount(transaction.getUnitCost().multiply(delta));
        }
        transactionMapper.insert(transaction);
        return stock;
    }

    private void fillTransactionIdentity(InventoryTransaction tx, InventoryStockItem stock) {
        if (tx.getInventoryItemId() == null) tx.setInventoryItemId(stock.getInventoryItemId());
        if (tx.getMaterialId() == null) tx.setMaterialId(stock.getMaterialId());
        if (tx.getMaterialCode() == null) tx.setMaterialCode(stock.getMaterialCode());
        if (tx.getMaterialName() == null) tx.setMaterialName(stock.getMaterialName());
        if (tx.getWarehouseId() == null) tx.setWarehouseId(stock.getWarehouseId());
        if (tx.getLocationId() == null) tx.setLocationId(stock.getLocationId());
        if (tx.getBatchNo() == null) tx.setBatchNo(stock.getBatchNo());
        if (tx.getIqcBatchId() == null) tx.setIqcBatchId(stock.getIqcBatchId());
        if (tx.getUnitCost() == null) tx.setUnitCost(stock.getUnitCost());
    }

    private BigDecimal value(BigDecimal quantity) {
        return quantity == null ? BigDecimal.ZERO : quantity;
    }
}
