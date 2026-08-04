package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.SalesOrderStockReserve;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.SalesOrderStockReserveMapper;
import com.jjx.inventory.service.OrderStockReserveService;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesOrderProduct;
import com.jjx.sales.enums.OrderTypeEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesOrderProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售订单成品库存预留服务实现（DEV-578）
 *
 * 逻辑：
 * 1. 订单确认(4→6)时检查成品可用库存 available_quantity
 * 2. 库存充足→预留全部；不足→预留库存部分，缺货量(订单量-预留量)进生产
 * 3. 预留按 FIFO 批次占用 reserved_quantity，释放/扣减同步
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStockReserveServiceImpl implements OrderStockReserveService {

    private final SalesOrderStockReserveMapper reserveMapper;
    private final InventoryMaterialMapper materialMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryStockItemMapper stockItemMapper;
    private final OrderMapper orderMapper;
    private final SalesOrderProductMapper orderProductMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<Long, BigDecimal> reserveForOrder(Long orderId) {
        log.info("订单成品库存预留开始: orderId={}", orderId);
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在，跳过成品预留: {}", orderId);
            return new HashMap<>();
        }
        // 样品单跳过
        if (OrderTypeEnum.SAMPLE.getCode().equals(order.getOrderType())) {
            log.info("样品单{}跳过成品库存预留", order.getOrderNo());
            return new HashMap<>();
        }

        // 幂等：清掉该订单旧的未释放预留
        releaseByOrder(orderId);

        // 订单明细
        List<SalesOrderProduct> products = orderProductMapper.selectList(
                new LambdaQueryWrapper<SalesOrderProduct>()
                        .eq(SalesOrderProduct::getOrderId, orderId));
        if (products == null || products.isEmpty()) {
            log.info("订单{}无明细，跳过成品预留", order.getOrderNo());
            return new HashMap<>();
        }

        Map<Long, BigDecimal> shortageMap = new HashMap<>();
        for (SalesOrderProduct p : products) {
            if (p.getProductId() == null) {
                log.info("订单{}明细产品ID为空，跳过", order.getOrderNo());
                continue;
            }
            // 找成品物料档案（product_id 映射，material_type=F）
            InventoryMaterial material = materialMapper.selectOne(
                    new LambdaQueryWrapper<InventoryMaterial>()
                            .eq(InventoryMaterial::getProductId, p.getProductId())
                            .eq(InventoryMaterial::getMaterialType, "F")
                            .last("LIMIT 1"));
            if (material == null) {
                // 无库存档案/新产品 → 视为0库存全量生产
                shortageMap.put(p.getProductId(), BigDecimal.valueOf(p.getQuantity() == null ? 0 : p.getQuantity()));
                log.info("产品{}无成品物料档案，按0库存全量生产", p.getProductId());
                continue;
            }

            // 可用库存 = 总量 - 预留（available_quantity 为生成列，实体未映射，手动算）
            InventoryStock stock = stockMapper.selectByMaterialId(material.getMaterialId());
            BigDecimal total = (stock != null && stock.getTotalQuantity() != null)
                    ? stock.getTotalQuantity() : BigDecimal.ZERO;
            BigDecimal reserved = (stock != null && stock.getTotalReserved() != null)
                    ? stock.getTotalReserved() : BigDecimal.ZERO;
            BigDecimal available = total.subtract(reserved);
            BigDecimal orderQty = BigDecimal.valueOf(p.getQuantity() == null ? 0 : p.getQuantity());
            BigDecimal reserveQty = orderQty.min(available);
            if (reserveQty.compareTo(BigDecimal.ZERO) > 0) {
                reserveStock(material.getMaterialId(), reserveQty);
                // 记录预留
                SalesOrderStockReserve r = new SalesOrderStockReserve();
                r.setOrderId(orderId);
                r.setOrderNo(order.getOrderNo());
                r.setProductId(p.getProductId());
                r.setMaterialId(material.getMaterialId());
                r.setMaterialCode(material.getMaterialCode());
                r.setMaterialName(material.getMaterialName());
                r.setReserveQuantity(reserveQty);
                r.setStatus(0);
                reserveMapper.insert(r);
            }
            BigDecimal shortage = orderQty.subtract(reserveQty);
            if (shortage.compareTo(BigDecimal.ZERO) > 0) {
                shortageMap.put(p.getProductId(), shortage);
            }
        }
        log.info("订单{}成品库存预留完成，缺货产品数: {}", order.getOrderNo(), shortageMap.size());
        return shortageMap;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseByOrder(Long orderId) {
        List<SalesOrderStockReserve> reserves = reserveMapper.selectList(
                new LambdaQueryWrapper<SalesOrderStockReserve>()
                        .eq(SalesOrderStockReserve::getOrderId, orderId)
                        .eq(SalesOrderStockReserve::getStatus, 0));
        if (reserves == null || reserves.isEmpty()) {
            return;
        }
        for (SalesOrderStockReserve r : reserves) {
            releaseStock(r.getMaterialId(), r.getReserveQuantity());
            r.setStatus(1);
            reserveMapper.updateById(r);
        }
        log.info("订单{}释放成品预留{}条", orderId, reserves.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseForOutbound(Long orderId, Long materialId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        List<SalesOrderStockReserve> reserves = reserveMapper.selectList(
                new LambdaQueryWrapper<SalesOrderStockReserve>()
                        .eq(SalesOrderStockReserve::getOrderId, orderId)
                        .eq(SalesOrderStockReserve::getMaterialId, materialId)
                        .eq(SalesOrderStockReserve::getStatus, 0));
        if (reserves == null || reserves.isEmpty()) {
            return;
        }
        BigDecimal remaining = quantity;
        for (SalesOrderStockReserve r : reserves) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal reservedQty = r.getReserveQuantity() == null ? BigDecimal.ZERO : r.getReserveQuantity();
            if (reservedQty.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal release = remaining.min(reservedQty);
            releaseStock(r.getMaterialId(), release);
            BigDecimal left = reservedQty.subtract(release);
            if (left.compareTo(BigDecimal.ZERO) <= 0) {
                r.setStatus(1);
            } else {
                r.setReserveQuantity(left);
            }
            reserveMapper.updateById(r);
            remaining = remaining.subtract(release);
        }
        log.info("订单{}出库联动释放物料{}预留{}（剩余待释放{}）", orderId, materialId, quantity.subtract(remaining), remaining);
    }

    @Override
    public Map<Long, BigDecimal> getReservedQty(Long orderId) {
        Map<Long, BigDecimal> map = new HashMap<>();
        List<SalesOrderStockReserve> reserves = reserveMapper.selectList(
                new LambdaQueryWrapper<SalesOrderStockReserve>()
                        .eq(SalesOrderStockReserve::getOrderId, orderId)
                        .eq(SalesOrderStockReserve::getStatus, 0));
        if (reserves != null) {
            for (SalesOrderStockReserve r : reserves) {
                map.merge(r.getProductId(), r.getReserveQuantity(), BigDecimal::add);
            }
        }
        return map;
    }

    @Override
    public Map<Long, BigDecimal> getShortageQty(Long orderId) {
        Map<Long, BigDecimal> reserved = getReservedQty(orderId);
        Map<Long, BigDecimal> shortage = new HashMap<>();
        List<SalesOrderProduct> products = orderProductMapper.selectList(
                new LambdaQueryWrapper<SalesOrderProduct>()
                        .eq(SalesOrderProduct::getOrderId, orderId));
        if (products != null) {
            for (SalesOrderProduct p : products) {
                if (p.getProductId() == null) continue;
                BigDecimal orderQty = BigDecimal.valueOf(p.getQuantity() == null ? 0 : p.getQuantity());
                BigDecimal res = reserved.getOrDefault(p.getProductId(), BigDecimal.ZERO);
                BigDecimal gap = orderQty.subtract(res);
                if (gap.compareTo(BigDecimal.ZERO) > 0) {
                    shortage.put(p.getProductId(), gap);
                }
            }
        }
        return shortage;
    }

    /**
     * 按 FIFO 批次占用预留
     */
    private void reserveStock(Long materialId, BigDecimal qty) {
        List<InventoryStockItem> items = stockItemMapper.selectFIFOAvailable(materialId);
        BigDecimal remaining = qty;
        for (InventoryStockItem item : items) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal free = item.getQuantity().subtract(item.getReservedQuantity());
            if (free.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal take = remaining.min(free);
            stockItemMapper.addReserved(item.getItemId(), take);
            remaining = remaining.subtract(take);
        }
        stockMapper.refreshSummary(materialId);
    }

    /**
     * 释放预留（从最新批次开始释放）
     */
    private void releaseStock(Long materialId, BigDecimal qty) {
        List<InventoryStockItem> items = stockItemMapper.selectActiveByMaterialId(materialId);
        BigDecimal remaining = qty;
        // 倒序：后预留的先释放
        for (int i = items.size() - 1; i >= 0; i--) {
            InventoryStockItem item = items.get(i);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal reserved = item.getReservedQuantity() == null ? BigDecimal.ZERO : item.getReservedQuantity();
            if (reserved.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal release = remaining.min(reserved);
            stockItemMapper.releaseReserved(item.getItemId(), release);
            remaining = remaining.subtract(release);
        }
        stockMapper.refreshSummary(materialId);
    }
}
