package com.jjx.notification.service;

import com.jjx.notification.domain.dto.NotifyTaskDTO;

/**
 * 通用催办服务（2026-09-09 dev-20260909-002）
 * 语义：按目标角色展开成员 → 每人发一条站内信（sys_notification）+ 建一条待办任务（sys_task，assign_role=首个角色）。
 * 与事件管线（LocalEventPublisher）互补：事件管线由 sys_event_config 驱动、固定收件人；
 * 本服务由业务方直接调用，收件人/内容每次传参，适合临时性、跨模块的"通知+待办"诉求。
 */
public interface NotifyTaskService {

    /**
     * 发通知 + 建待办任务（同一事务）。
     *
     * @param dto 标题/内容/目标角色/业务溯源
     * @throws com.jjx.common.exception.BusinessException 角色不存在或目标角色无成员时抛错，避免静默丢失
     */
    void notifyAndCreateTask(NotifyTaskDTO dto);
}
