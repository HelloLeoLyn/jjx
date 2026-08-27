package com.jjx.inventory.task;

import com.jjx.inventory.service.OrderMaterialReserveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 材料预占超时定时任务（094定稿）
 * 每日扫描：剩余1天提醒 + 到期未处理自动释放（释放留痕）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialReserveTimeoutTask {

    private final OrderMaterialReserveService reserveService;

    /** 每日 8:00 执行一次 */
    @Scheduled(cron = "0 0 8 * * ?")
    public void processTimeout() {
        try {
            log.info("[定时任务] 开始处理材料预占超时");
            reserveService.processTimeout();
            log.info("[定时任务] 材料预占超时处理完成");
        } catch (Exception e) {
            log.error("[定时任务] 材料预占超时处理异常: {}", e.getMessage(), e);
        }
    }
}
