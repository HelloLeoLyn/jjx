package com.jjx.inventory.task;

import com.jjx.inventory.service.InventoryAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 库存预警定时调度（DEV-627）
 * <ul>
 *   <li>每小时整点后 5 分钟：执行全量库存预警检查（安全库存/超储/过期/呆滞）</li>
 * </ul>
 * 说明：executeAlertCheck 原只有手动接口（InventoryAlertController），
 * 缺料预警（order_shortage）仍在订单确认时触发，本任务覆盖其余自动检查。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryAlertTask {

    private final InventoryAlertService alertService;

    /** 每小时第 5 分钟执行库存预警检查 */
    @Scheduled(cron = "0 5 * * * ?")
    public void executeAlertCheck() {
        try {
            log.info("[定时任务] 开始执行库存预警检查");
            alertService.executeAlertCheck();
            log.info("[定时任务] 库存预警检查完成");
        } catch (Exception e) {
            log.error("[定时任务] 库存预警检查异常: {}", e.getMessage(), e);
        }
    }
}
