package com.jjx.sales.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryItem;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.enums.InventoryItemTypeEnum;
import com.jjx.inventory.mapper.InventoryItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.service.OrderStockReserveService;
import com.jjx.sales.domain.entity.SalesDeliveryItem;
import com.jjx.sales.domain.entity.SalesOrderProduct;
import com.jjx.sales.domain.vo.DeliveryPendingQuantityVO;
import com.jjx.sales.enums.SalesDeliveryStatusEnum;
import com.jjx.sales.mapper.DeliveryArrangeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/** 订单自有实预留优先、共享成品其次；待发货占用由凭证派生，不变更库存。 */
@Service
@RequiredArgsConstructor
public class DeliveryCapacityService {
    private final InventoryItemMapper itemMapper;
    private final InventoryStockMapper stockMapper;
    private final DeliveryArrangeMapper arrangeMapper;
    private final OrderStockReserveService reserveService;

    public record Capacity(BigDecimal shared, Map<Long, BigDecimal> owned, BigDecimal productAvailable) {
        public BigDecimal forOrder(Long orderId) {
            return shared.add(owned.getOrDefault(orderId, BigDecimal.ZERO)).min(productAvailable);
        }
    }

    public Map<Long, Capacity> capacities(Collection<SalesOrderProduct> sources, boolean lock) {
        Map<Long, Set<Long>> ordersByProduct = new TreeMap<>();
        for (SalesOrderProduct source : sources) {
            if (source.getProductId() != null) ordersByProduct.computeIfAbsent(source.getProductId(), ignored -> new TreeSet<>()).add(source.getOrderId());
        }
        Map<Long, Map<Long, BigDecimal>> reservations = new HashMap<>();
        Map<Long, Capacity> result = new LinkedHashMap<>();
        for (var entry : ordersByProduct.entrySet()) {
            Long productId = entry.getKey();
            InventoryItem identity = itemMapper.selectBySource(InventoryItemTypeEnum.PRODUCT.getCode(), productId);
            InventoryStock stock = identity == null ? null : lock
                ? stockMapper.selectByInventoryItemIdForUpdate(identity.getInventoryItemId())
                : stockMapper.selectByInventoryItemId(identity.getInventoryItemId());
            BigDecimal total = stock == null ? BigDecimal.ZERO : positive(stock.getTotalQuantity());
            BigDecimal reserved = stock == null ? BigDecimal.ZERO : positive(stock.getTotalReserved()).min(total);
            BigDecimal shared = total.subtract(reserved);
            BigDecimal pendingTotal = BigDecimal.ZERO;
            Map<Long, BigDecimal> pending = new HashMap<>();
            for (DeliveryPendingQuantityVO row : arrangeMapper.pendingByOrder(productId, SalesDeliveryStatusEnum.PENDING.getValue())) {
                BigDecimal quantity = positive(row.getQuantity());
                pending.merge(row.getOrderId(), quantity, BigDecimal::add);
                pendingTotal = pendingTotal.add(quantity);
            }
            Set<Long> orderIds = new TreeSet<>(entry.getValue());
            orderIds.addAll(pending.keySet());
            Map<Long, BigDecimal> owned = new HashMap<>();
            for (Long orderId : orderIds) {
                BigDecimal ownReserve = positive(reservations.computeIfAbsent(orderId, reserveService::getReservedQty)
                    .getOrDefault(productId, BigDecimal.ZERO)).min(reserved);
                BigDecimal awaiting = pending.getOrDefault(orderId, BigDecimal.ZERO);
                shared = shared.subtract(awaiting.subtract(ownReserve).max(BigDecimal.ZERO));
                owned.put(orderId, ownReserve.subtract(awaiting).max(BigDecimal.ZERO));
            }
            result.put(productId, new Capacity(shared.max(BigDecimal.ZERO), owned, total.subtract(pendingTotal).max(BigDecimal.ZERO)));
        }
        return result;
    }

    /** 多行同产品/跨订单共用的未预留库存只计一次，不能逐行分别校验后叠加。 */
    public void validate(List<SalesDeliveryItem> requested, Map<Long, SalesOrderProduct> sources, Map<Long, Capacity> capacities) {
        Map<Long, Map<Long, BigDecimal>> required = new TreeMap<>();
        for (SalesDeliveryItem line : requested) {
            SalesOrderProduct source = sources.get(line.getOrderProductId());
            required.computeIfAbsent(source.getProductId(), ignored -> new TreeMap<>())
                .merge(source.getOrderId(), BigDecimal.valueOf(line.getQuantity()), BigDecimal::add);
        }
        for (var product : required.entrySet()) {
            Capacity capacity = capacities.get(product.getKey());
            BigDecimal total = BigDecimal.ZERO, sharedNeeded = BigDecimal.ZERO;
            for (var order : product.getValue().entrySet()) {
                total = total.add(order.getValue());
                sharedNeeded = sharedNeeded.add(order.getValue().subtract(capacity.owned().getOrDefault(order.getKey(), BigDecimal.ZERO)).max(BigDecimal.ZERO));
            }
            if (total.compareTo(capacity.productAvailable()) > 0 || sharedNeeded.compareTo(capacity.shared()) > 0)
                throw new BusinessException("本次建单数量超过可用成品量，请刷新后分批安排（产品ID " + product.getKey() + "）");
        }
    }

    private static BigDecimal positive(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO); }
}
