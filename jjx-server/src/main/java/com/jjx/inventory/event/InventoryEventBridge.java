package com.jjx.inventory.event;

import com.jjx.inventory.service.InventoryInboundService;
import com.jjx.inventory.service.InventoryOutboundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 库存事件桥接器
 * 监听业务事件 → 自动创建库存单据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventBridge {

    private final InventoryInboundService inboundService;
    private final InventoryOutboundService outboundService;

    /**
     * 生产完工 → 自动创建完工入库单
     */
    @EventListener(condition = "#payload?.eventCode == 'production.completed'")
    public void onProductionCompleted(Map<String, Object> payload) {
        log.info("🏭 生产完工联动入库: {}", payload);
        try {
            // dev-20260930-028：关键业务键缺失时明确报错，不再拿空单号去建「无来源」入库单
            Object orderNoVal = payload.get("orderNo");
            String orderNo = orderNoVal == null ? "" : String.valueOf(orderNoVal).trim();
            if (orderNo.isEmpty()) {
                log.error("   ❌ 生产完工联动缺少 orderNo，未建完工入库单: payload={}", payload);
                return;
            }
            Map<String, Object> params = new HashMap<>();
            params.put("sourceType", "production");
            params.put("sourceNo", orderNo);
            params.put("inboundType", "production");
            params.put("remark", "生产完工自动入库");
            Long inboundId = inboundService.create(params);
            log.info("   ✅ 完工入库单已创建: inboundId={}", inboundId);
        } catch (Exception e) {
            log.error("   ❌ 创建完工入库单失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 销售发货联动（监听 order.delivering 事件）
     */
    @EventListener(condition = "#payload?.eventCode == 'order.delivering'")
    public void onSalesDelivery(Map<String, Object> payload) {
        log.info("🚛 销售发货联动出库: {}", payload);
        try {
            // 2026-09-21 dev-20260921-039（分批发货）：优先按发货单明细出库，数量与发货单一致
            Object deliveryIdVal = payload.get("deliveryId");
            if (deliveryIdVal != null) {
                Long deliveryId = Long.valueOf(deliveryIdVal.toString());
                Long outboundId = outboundService.createFromSalesByDelivery(deliveryId);
                log.info("   ✅ 销售出库单已按发货单明细创建并扣库存: deliveryId={}, outboundId={}", deliveryId, outboundId);
                return;
            }
            // 先尝试使用更完善的 createFromSales 方式（含明细行 + 自动审批）
            Object salesOrderId = payload.get("salesOrderId");
            if (salesOrderId != null) {
                Long orderId = Long.valueOf(salesOrderId.toString());
                Long outboundId = outboundService.createFromSales(orderId);
                if (outboundId != null) {
                    log.info("   ✅ 销售出库单已创建并审批: outboundId={}", outboundId);
                    return;
                }
            }

            // dev-20260930-028：删掉原「通用兜底建单」（create(params)+confirm）——
            // 它建出的出库单没有明细、没有来源单号，正是「账对不上」的第二个来源。
            // 关键键缺失时明确报错（不再静默，也不造脏单）。
            throw new IllegalStateException(
                    "发货联动缺少 deliveryId / salesOrderId，未建销售出库单：payload=" + payload);
        } catch (Exception e) {
            log.error("   ❌ 创建销售出库单失败: {}", e.getMessage(), e);
        }
    }
}
