package com.jjx.event;

import com.jjx.system.utils.SecurityUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;

/**
 * 手写 payload 的事件发布工具（2026-09-21 dev-20260921-009）。
 *
 * <p>为什么需要它：@Event 注解的 params 只能从方法参数/返回值取 SpEL，而业务单号常常不在参数里
 * （delete(Long id)、approve(Long returnId, ...) 这类），模板里写 {receiptNo} 会原样显示
 * （quality.iqc.submitted 的「入库单【{inboundNo}】」实例）。这里在业务方法内部用已经加载的实体
 * 组装 payload，事务提交后再发布，语义与 @Event 切面一致。</p>
 */
public final class EventPublishSupport {

    private EventPublishSupport() {
    }

    /**
     * 组装基础 payload：bizType + bizId + 触发人。
     * 账号进 triggerUserName（任务 create_by 用），姓名进 triggerRealName（通知「发送人」用）。
     */
    public static Map<String, Object> payload(String bizType, Long bizId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bizType", bizType);
        if (bizId != null) {
            payload.put("bizId", bizId);
        }
        try {
            payload.put("triggerUserId", SecurityUtils.getUserId());
            payload.put("triggerUserName", SecurityUtils.getUsername());
            payload.put("triggerRealName", SecurityUtils.getDisplayName());
        } catch (Exception ignored) {
            // 无登录上下文（定时任务/内部调用）时只发布业务字段
        }
        return payload;
    }

    /** 事务提交后发布（无活动事务时立即发布）；payload 做快照，避免提交后被继续修改。 */
    public static void fireAfterCommit(EventPublisher publisher, String eventCode, Map<String, Object> payload) {
        if (publisher == null || eventCode == null || payload == null) {
            return;
        }
        Map<String, Object> snapshot = new HashMap<>(payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.fire(eventCode, snapshot);
                }
            });
        } else {
            publisher.fire(eventCode, snapshot);
        }
    }
}
