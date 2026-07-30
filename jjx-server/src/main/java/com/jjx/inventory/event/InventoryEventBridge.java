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
            Map<String, Object> params = new HashMap<>();
            params.put("sourceType", "production");
            params.put("sourceNo", payload.getOrDefault("orderNo", ""));
            params.put("inboundType", "production");
            params.put("remark", "生产完工自动入库");
            Long inboundId = inboundService.create(params);
            inboundService.confirm(inboundId, null, "system");
            log.info("   ✅ 完工入库单已创建: inboundId={}", inboundId);
        } catch (Exception e) {
            log.error("   ❌ 创建完工入库单失败: {}", e.getMessage());
        }
    }

    /**
     * 销售发货联动（监听 order.delivering 事件）
     */
    @EventListener(condition = "#payload?.eventCode == 'order.delivering'")
    public void onSalesDelivery(Map<String, Object> payload) {
        log.info("🚛 销售发货联动出库: {}", payload);
        try {
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

            // 兜底：使用通用方式创建
            Map<String, Object> params = new HashMap<>();
            params.put("sourceType", "sales");
            params.put("sourceNo", payload.getOrDefault("orderNo", ""));
            params.put("outboundType", "sales");
            params.put("remark", "销售发货自动出库");
            Long outboundId = outboundService.create(params);
            outboundService.confirm(outboundId, null, "system");
            log.info("   ✅ 销售出库单已创建并确认: outboundId={}", outboundId);
        } catch (Exception e) {
            log.error("   ❌ 创建销售出库单失败: {}", e.getMessage(), e);
        }
    }
}
