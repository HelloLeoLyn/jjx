package com.jjx.system.task;

import com.jjx.system.config.AttachmentCleanupProperties;
import com.jjx.system.service.AttachmentLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 到期 TEMP 附件自动收口（dev-20261014-035，口径 b，只做①）。
 * <p>复用 {@link AttachmentLifecycleService#autoRecycleExpired} 的既有判定，把到期、无有效引用、非受控的
 * TEMP 附件**软删除移入回收站**（可恢复）；**只标记、绝不删磁盘字节**，不调用物理删除（V1-A 红线）。
 * <p>默认关闭：{@code erp.attachment.cleanup.enabled=true} 才执行（保守，用户确认后再开）。
 * 每轮从游标 0 起按 {@code batchSize}（≤100）分页调用，直到某批返回条数不足即到表尾。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentCleanupTask {

    /** 单次运行最多翻页轮数，防止异常情况下无限循环（100×1000=10 万行）。 */
    private static final int MAX_ROUNDS = 1000;

    private final AttachmentLifecycleService lifecycle;
    private final AttachmentCleanupProperties properties;

    @Scheduled(cron = "${erp.attachment.cleanup.cron:0 0 3 * * ?}")
    public void autoRecycleExpiredTemp() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            log.debug("附件自动清理未启用（erp.attachment.cleanup.enabled=false），跳过");
            return;
        }
        int batch = properties.getBatchSize() == null ? 100 : Math.max(1, Math.min(100, properties.getBatchSize()));
        long afterId = 0L;
        int totalScanned = 0, totalRecycled = 0, rounds = 0;
        while (true) {
            Map<String, Object> result = lifecycle.autoRecycleExpired(afterId, batch);
            int scanned = numberAt(result, "scanned");
            totalScanned += scanned;
            totalRecycled += numberAt(result, "recycled");
            afterId = ((Number) result.getOrDefault("nextAfterId", afterId)).longValue();
            rounds++;
            if (scanned < batch) {
                break;
            }
            if (rounds >= MAX_ROUNDS) {
                log.warn("附件自动清理达到最大轮次 {}，本轮终止（下轮从表头继续）", MAX_ROUNDS);
                break;
            }
        }
        if (totalRecycled > 0) {
            lifecycle.recordAutoCleanupRun(totalScanned, totalRecycled);
        }
        log.info("附件自动清理完成 scanned={} recycled={} rounds={} physicalDelete=false",
                totalScanned, totalRecycled, rounds);
    }

    private int numberAt(Map<String, Object> result, String key) {
        Object value = result.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
