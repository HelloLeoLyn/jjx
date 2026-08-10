package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.ProductStock;
import com.jjx.inventory.domain.SalesOrderStockReserve;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.ProductStockMapper;
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
 * 销售订单成品库存预留服务实现（DEV-578 / 040）
 *
 * 逻辑：
 * 1. 订单确认(4→6)时检查成品可用库存 available_quantity
 * 2. 库存充足→预留全部；不足→预留库存部分，缺货量(订单量-预留量)进生产
 * 3. 预留走【产品库存表 product_stock】（040定稿：成品预留→产品预留，非物料库存）
 *    available = total_quantity - total_reserved
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStockReserveServiceImpl implements OrderStockReserveService {

    private final SalesOrderStockReserveMapper reserveMapper;
    private final InventoryMaterialMapper materialMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryStockItemMapper stockItemMapper;
    private final ProductStockMapper productStockMapper;
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
            // 040定稿：预留走【产品库存表 product_stock】（产品维度），不再走物料F维度
            // 可用库存 = 总库存 - 预留（available_quantity 生成列）
            ProductStock pStock = productStockMapper.selectByProductId(p.getProductId());
            BigDecimal total = (pStock != null && pStock.getTotalQuantity() != null)
                    ? pStock.getTotalQuantity() : BigDecimal.ZERO;
            BigDecimal reserved = (pStock != null && pStock.getTotalReserved() != null)
                    ? pStock.getTotalReserved() : BigDecimal.ZERO;
            BigDecimal available = total.subtract(reserved);
            BigDecimal orderQty = BigDecimal.valueOf(p.getQuantity() == null ? 0 : p.getQuantity());
            BigDecimal reserveQty = orderQty.min(available);
            if (reserveQty.compareTo(BigDecimal.ZERO) > 0) {
                // 产品库存预留（无记录先初始化，保证可预留）
                if (pStock == null) {
                    productStockMapper.increaseStock(p.getProductId(), p.getProductCode(), p.getProductName(), BigDecimal.ZERO);
                }
                int rows = productStockMapper.addReserved(p.getProductId(), reserveQty);
                if (rows == 0) {
                    log.warn("产品{}预留失败（可用不足），按0预留处理", p.getProductId());
                } else {
                    // 记录预留（保留原表做订单维度追溯）
                    SalesOrderStockReserve r = new SalesOrderStockReserve();
                    r.setOrderId(orderId);
                    r.setOrderNo(order.getOrderNo());
                    r.setProductId(p.getProductId());
                    r.setMaterialId(p.getProductId()); // 产品维度：material_id 字段暂存产品ID（兼容旧结构）
                    r.setMaterialCode(p.getProductCode());
                    r.setMaterialName(p.getProductName());
                    r.setReserveQuantity(reserveQty);
                    r.setStatus(0);
                    reserveMapper.insert(r);
                }
            }
            BigDecimal shortage = orderQty.subtract(reserveQty);
            if (shortage.compareTo(BigDecimal.ZERO) > 0) {
                shortageMap.put(p.getProductId(), shortage);
            }
        }
        log.info("订单{}成品库存预留完成（产品维度），缺货产品数: {}", order.getOrderNo(), shortageMap.size());
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
            // 040：产品维度释放（material_id 暂存产品ID）
            productStockMapper.releaseReserved(r.getMaterialId(), r.getReserveQuantity());
            r.setStatus(1);
            reserveMapper.updateById(r);
        }
        log.info("订单{}释放成品预留{}条（产品维度）", orderId, reserves.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseForOutbound(Long orderId, Long materialId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        // 040：出库联动释放产品预留（materialId 在产品维度=产品ID）
        productStockMapper.releaseReserved(materialId, quantity);
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
            BigDecimal left = reservedQty.subtract(release);
            if (left.compareTo(BigDecimal.ZERO) <= 0) {
                r.setStatus(1);
            } else {
                r.setReserveQuantity(left);
            }
            reserveMapper.updateById(r);
            remaining = remaining.subtract(release);
        }
        log.info("订单{}出库联动释放产品{}预留{}（剩余待释放{}）", orderId, materialId, quantity.subtract(remaining), remaining);
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
}
