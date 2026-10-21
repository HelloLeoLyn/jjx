package com.jjx.product.task;

import com.jjx.product.service.StandardProcessUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StandardProcessUsageTask {
    private final StandardProcessUsageService usageService;

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void aggregateUsage() {
        try {
            usageService.aggregateUsage();
            log.info("[定时任务] 全公司标准工序近90天使用量聚合完成");
        } catch (Exception e) {
            log.error("[定时任务] 标准工序使用量聚合失败", e);
        }
    }
}
