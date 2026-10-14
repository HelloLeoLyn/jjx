package com.jjx.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 到期 TEMP 附件自动收口（dev-20261014-035，口径 b，只做①）的开关与批次配置。
 * <p>只做「到期 → 标记删除进回收站（可恢复）」，不做无人值守的物理删除（V1-A 红线）。
 * 默认 {@code enabled=false}（保守，用户确认后再开）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp.attachment.cleanup")
public class AttachmentCleanupProperties {
    /** 是否启用到期 TEMP 附件自动收口；默认关闭。 */
    private Boolean enabled = false;
    /** 每批处理条数（游标分页），上限 100。 */
    private Integer batchSize = 100;
}
