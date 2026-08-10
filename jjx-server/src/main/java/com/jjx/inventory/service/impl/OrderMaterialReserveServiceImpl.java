package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.OrderMaterialReserve;
import com.jjx.inventory.mapper.OrderMaterialReserveMapper;
import com.jjx.inventory.service.OrderMaterialReserveService;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesOrderProduct;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesOrderProductMapper;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单材料预占服务实现（094定稿）
 * 口径：可用量 = 总量 - 预留 - 预占占用；预占解决"已审核未确认订单不占料"盲区
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMaterialReserveServiceImpl implements OrderMaterialReserveService {

    private final OrderMaterialReserveMapper reserveMapper;
    private final OrderMapper orderMapper;
    private final SalesOrderProductMapper orderProductMapper;
    private final EngineeringBomMapper bomMapper;
    private final EngineeringBomItemMapper bomItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reserveForOrder(Long orderId, Integer days) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在: " + orderId);
        }
        if (days == null || days < 1 || days > 7) {
            days = 3; // 默认3天
        }
        // 幂等：先释放旧预占
        releaseByOrder(orderId, "重新预占");

        // 按BOM展开原料需求
        List<SalesOrderProduct> products = orderProductMapper.selectList(
                new LambdaQueryWrapper<SalesOrderProduct>().eq(SalesOrderProduct::getOrderId, orderId));
        Map<Long, BigDecimal> materialDemand = new HashMap<>();
        Map<Long, String> codeMap = new HashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        if (products != null) {
            for (SalesOrderProduct p : products) {
                if (p.getProductId() == null) continue;
                EngineeringBom bom = bomMapper.selectOne(new LambdaQueryWrapper<EngineeringBom>()
                        .eq(EngineeringBom::getProductId, p.getProductId())
                        .eq(EngineeringBom::getIsCurrent, true)
                        .eq(EngineeringBom::getApproveStatus, 3));
                if (bom == null) continue;
                List<EngineeringBomItem> items = bomItemMapper.selectList(
                        new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, bom.getBomId()));
                BigDecimal orderQty = BigDecimal.valueOf(p.getQuantity() == null ? 0 : p.getQuantity());
                for (EngineeringBomItem item : items) {
                    if (item.getMaterialId() == null) continue;
                    BigDecimal unitQty = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
                    BigDecimal loss = BigDecimal.valueOf(item.getLossRate() == null ? 0 : item.getLossRate());
                    BigDecimal need = orderQty.multiply(unitQty)
                            .multiply(BigDecimal.ONE.add(loss.divide(BigDecimal.valueOf(100))));
                    materialDemand.merge(item.getMaterialId(), need, BigDecimal::add);
                    codeMap.putIfAbsent(item.getMaterialId(), item.getMaterialCode());
                    nameMap.putIfAbsent(item.getMaterialId(), item.getMaterialName());
                }
            }
        }
        if (materialDemand.isEmpty()) {
            throw new BusinessException("订单无BOM可预占（产品需有已审批BOM）");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expire = now.plusDays(days);
        for (Map.Entry<Long, BigDecimal> e : materialDemand.entrySet()) {
            OrderMaterialReserve r = new OrderMaterialReserve();
            r.setOrderId(orderId);
            r.setOrderNo(order.getOrderNo());
            r.setMaterialId(e.getKey());
            r.setMaterialCode(codeMap.get(e.getKey()));
            r.setMaterialName(nameMap.get(e.getKey()));
            r.setReserveQuantity(e.getValue());
            r.setStatus(0);
            r.setReserveDays(days);
            r.setReserveTime(now);
            r.setExpireTime(expire);
            r.setCreateBy(com.jjx.system.utils.SecurityUtils.getUsername());
            reserveMapper.insert(r);
        }

        // 订单标记已预占
        order.setMaterialReserveFlag(1);
        order.setMaterialReserveTime(now);
        order.setMaterialReserveBy(com.jjx.system.utils.SecurityUtils.getUsername());
        order.setMaterialReserveExpire(expire);
        orderMapper.updateById(order);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("materialCount", materialDemand.size());
        result.put("reserveDays", days);
        result.put("expireTime", expire.toString());
        log.info("订单{}材料预占完成：{}种物料，{}天，到期{}", orderId, materialDemand.size(), days, expire);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void extendReserve(Long orderId) {
        List<OrderMaterialReserve> list = reserveMapper.selectActiveByOrder(orderId);
        if (list == null || list.isEmpty()) {
            throw new BusinessException("订单无占用中的预占记录，无法延迟");
        }
        LocalDateTime now = LocalDateTime.now();
        SalesOrder order = orderMapper.selectById(orderId);
        for (OrderMaterialReserve r : list) {
            // 每次+3天
            LocalDateTime newExpire = (r.getExpireTime() != null && r.getExpireTime().isAfter(now))
                    ? r.getExpireTime().plusDays(3) : now.plusDays(3);
            r.setExpireTime(newExpire);
            r.setReserveDays(r.getReserveDays() + 3);
            reserveMapper.updateById(r);
        }
        if (order != null) {
            order.setMaterialReserveExpire(now.plusDays(3));
            orderMapper.updateById(order);
        }
        log.info("订单{}预占延迟+3天，操作人：{}", orderId, com.jjx.system.utils.SecurityUtils.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseByOrder(Long orderId, String reason) {
        String operator = com.jjx.system.utils.SecurityUtils.getUsername();
        int rows = reserveMapper.releaseByOrder(orderId, reason != null ? reason : "手动释放", operator);
        SalesOrder order = orderMapper.selectById(orderId);
        if (order != null) {
            order.setMaterialReserveFlag(0);
            order.setMaterialReserveExpire(null);
            orderMapper.updateById(order);
        }
        if (rows > 0) {
            log.info("订单{}材料预占已释放{}条，原因：{}，操作人：{}", orderId, rows, reason, operator);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseByOrderAndMaterial(Long orderId, Long materialId, String reason) {
        List<OrderMaterialReserve> list = reserveMapper.selectActiveByOrder(orderId);
        if (list != null) {
            for (OrderMaterialReserve r : list) {
                if (r.getMaterialId().equals(materialId)) {
                    r.setStatus(1);
                    r.setReleaseReason(reason);
                    r.setReleaseBy(com.jjx.system.utils.SecurityUtils.getUsername());
                    r.setReleaseTime(LocalDateTime.now());
                    reserveMapper.updateById(r);
                }
            }
        }
    }

    @Override
    public BigDecimal getReservedQtyByMaterial(Long materialId) {
        List<Map<String, Object>> rows = reserveMapper.selectGroupByMaterial();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object mid = row.get("material_id");
                if (mid != null && ((Number) mid).longValue() == materialId) {
                    Object qty = row.get("total_reserve");
                    if (qty != null) {
                        return new BigDecimal(qty.toString());
                    }
                }
            }
        }
        return BigDecimal.ZERO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processTimeout() {
        // 1. 剩余1天提醒（记录日志+事件，实际通知由事件监听处理）
        try {
            List<OrderMaterialReserve> expiring = reserveMapper.selectExpiringSoon();
            for (OrderMaterialReserve r : expiring) {
                log.info("材料预占快到期提醒：订单{}物料{}到期{}", r.getOrderNo(), r.getMaterialCode(), r.getExpireTime());
            }
        } catch (Exception e) {
            log.warn("预占快到期提醒失败: {}", e.getMessage());
        }
        // 2. 到期自动释放
        try {
            List<OrderMaterialReserve> expired = reserveMapper.selectExpired();
            if (!expired.isEmpty()) {
                for (OrderMaterialReserve r : expired) {
                    reserveMapper.releaseByOrder(r.getOrderId(), "预占到期自动释放", "system");
                    log.info("订单{}材料预占到期自动释放，物料{}", r.getOrderNo(), r.getMaterialCode());
                }
            }
        } catch (Exception e) {
            log.warn("预占到期自动释放失败: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getOrderReserveInfo(Long orderId) {
        Map<String, Object> result = new HashMap<>();
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return result;
        }
        result.put("orderId", orderId);
        result.put("orderNo", order.getOrderNo());
        result.put("materialReserveFlag", order.getMaterialReserveFlag());
        result.put("materialReserveTime", order.getMaterialReserveTime());
        result.put("materialReserveBy", order.getMaterialReserveBy());
        result.put("materialReserveExpire", order.getMaterialReserveExpire());
        List<OrderMaterialReserve> list = reserveMapper.selectActiveByOrder(orderId);
        result.put("items", list);
        return result;
    }
}
