package com.jjx.event;

import java.util.Map;

/**
 * 事件发布器接口
 * 业务代码只调 eventPublisher.fire(eventCode, payload)
 * 通知谁、创什么看板任务，全由配置表控制
 */
public interface EventPublisher {

    /**
     * 发布业务事件
     * @param eventCode 事件编码，如 order.confirmed
     * @param payload   事件数据，模板变量从这里取
     */
    void fire(String eventCode, Map<String, Object> payload);
}
