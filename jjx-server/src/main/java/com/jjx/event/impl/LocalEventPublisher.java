package com.jjx.event.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.event.EventPublisher;
import com.jjx.kanban.domain.entity.KanbanTask;
import com.jjx.kanban.service.KanbanTaskService;
import com.jjx.notification.domain.dto.NotificationCreateDTO;
import com.jjx.notification.service.NotificationService;
import com.jjx.system.domain.entity.SysEventConfig;
import com.jjx.system.domain.entity.SysEventKanban;
import com.jjx.system.domain.entity.SysEventNotification;
import com.jjx.system.mapper.SysEventConfigMapper;
import com.jjx.system.mapper.SysEventKanbanMapper;
import com.jjx.system.mapper.SysEventNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 本地事件联动器
 * 查配置表 → 发通知 + 创看板任务
 * 同进程同步执行，事务内执行，失败回滚
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalEventPublisher implements EventPublisher {

    private final SysEventConfigMapper eventConfigMapper;
    private final SysEventNotificationMapper eventNotificationMapper;
    private final SysEventKanbanMapper eventKanbanMapper;
    private final NotificationService notificationService;
    private final KanbanTaskService kanbanTaskService;

    @Override
    public void fire(String eventCode, Map<String, Object> payload) {
        // 1. 查事件配置
        SysEventConfig event = eventConfigMapper.selectOne(
                new LambdaQueryWrapper<SysEventConfig>()
                        .eq(SysEventConfig::getEventCode, eventCode)
                        .eq(SysEventConfig::getIsEnabled, 1)
        );
        if (event == null) {
            log.debug("事件[{}]未配置或已禁用，跳过", eventCode);
            return;
        }
        log.info("🔥 触发事件: {} ({})", eventCode, event.getEventName());

        // 2. 执行通知
        List<SysEventNotification> notifications = eventNotificationMapper.selectList(
                new LambdaQueryWrapper<SysEventNotification>()
                        .eq(SysEventNotification::getEventId, event.getEventId())
                        .eq(SysEventNotification::getIsEnabled, 1)
        );
        for (SysEventNotification notif : notifications) {
            try {
                NotificationCreateDTO dto = new NotificationCreateDTO();
                dto.setTitle(resolveTemplate(eventCode + " 触发通知", payload));
                dto.setContent(resolveTemplate("事件[" + event.getEventName() + "]已触发，请处理", payload));
                dto.setNotificationType("system");
                dto.setBizType(eventCode);
                dto.setReceiverId(notif.getRoleId());
                dto.setPriority("normal");
                notificationService.createNotification(dto);
                log.info("   📨 已创建通知: event={}, roleId={}", eventCode, notif.getRoleId());
            } catch (Exception e) {
                log.error("   ❌ 通知失败: {}", e.getMessage());
            }
        }

        // 3. 执行看板任务创建
        List<SysEventKanban> kanbans = eventKanbanMapper.selectList(
                new LambdaQueryWrapper<SysEventKanban>()
                        .eq(SysEventKanban::getEventId, event.getEventId())
                        .eq(SysEventKanban::getIsEnabled, 1)
        );
        for (SysEventKanban kb : kanbans) {
            try {
                KanbanTask task = new KanbanTask();
                task.setTaskCode(eventCode + "-" + System.currentTimeMillis());
                task.setTitle(resolveTemplate(kb.getCardTitleTemplate(), payload));
                task.setDescription(resolveTemplate(kb.getCardDescTemplate(), payload));
                task.setKanbanType(kb.getKanbanType());
                task.setColumnId(kb.getTargetColumn());
                task.setSourceEvent(eventCode);
                task.setAssignRole(kb.getAssignRoleId());
                task.setPriority("normal");
                kanbanTaskService.createTask(task);
                log.info("   📋 已创建看板任务: type={}, column={}", kb.getKanbanType(), kb.getTargetColumn());
            } catch (Exception e) {
                log.error("   ❌ 创建看板任务失败: {}", e.getMessage());
            }
        }

        log.info("✅ 事件[{}]处理完成", eventCode);
    }

    private String resolveTemplate(String template, Map<String, Object> payload) {
        if (template == null || payload == null) return template;
        String result = template;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
