package com.jjx.event.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.event.EventPublisher;
import com.jjx.notification.domain.dto.NotificationCreateDTO;
import com.jjx.notification.service.NotificationService;
import com.jjx.system.domain.entity.SysEventConfig;
import com.jjx.system.domain.entity.SysEventKanban;
import com.jjx.system.domain.entity.SysEventNotification;
import com.jjx.system.domain.entity.SysTask;
import com.jjx.system.mapper.SysEventConfigMapper;
import com.jjx.system.mapper.SysEventKanbanMapper;
import com.jjx.system.mapper.SysEventNotificationMapper;
import com.jjx.system.mapper.SysTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.beans.PropertyDescriptor;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final SysTaskMapper sysTaskMapper;

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
                dto.setTitle(resolveTemplate(event.getEventName(), payload));
                dto.setContent(resolveTemplate(event.getEventName() + " - 请及时处理", payload));
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
                SysTask task = new SysTask();
                task.setTaskCode(eventCode + "-" + System.currentTimeMillis());
                task.setTitle(resolveTemplate(kb.getCardTitleTemplate(), payload));
                task.setDescription(resolveTemplate(kb.getCardDescTemplate(), payload));
                task.setTaskType(kb.getKanbanType());
                task.setStartTime(java.time.LocalDateTime.now());
                task.setSourceEvent(eventCode);
                task.setAssignRole(kb.getAssignRoleId());
                task.setPriority("normal");
                task.setStatus("pending");
                sysTaskMapper.insert(task);
                log.info("   📋 已创建看板任务: id={}, type={}, column={}", task.getTaskId(), kb.getKanbanType(), kb.getTargetColumn());
            } catch (Exception e) {
                log.error("   ❌ 创建看板任务失败: {}", e.getMessage());
            }
        }

        log.info("✅ 事件[{}]处理完成", eventCode);
    }

    private String resolveTemplate(String template, Map<String, Object> payload) {
        if (template == null || payload == null) return template;
        String result = template;

        // 正则匹配所有 ${xxx} 占位符
        Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(result);

        while (matcher.find()) {
            String expr = matcher.group(1);
            String value = resolvePlaceholder(expr, payload);
            if (value != null) {
                result = result.replace("${" + expr + "}", value);
            }
        }
        return result;
    }

    /**
     * 解析单个占位符
     * 支持：
     *   直接key: ${productName}
     *   嵌套属性: ${dto.productName}
     *   自动扫描: ${productName} 从payload中任意对象查找匹配的getter
     */
    private String resolvePlaceholder(String expr, Map<String, Object> payload) {
        // 1. 直接 key 匹配
        if (payload.containsKey(expr)) {
            Object val = payload.get(expr);
            return val != null ? String.valueOf(val) : null;
        }

        // 2. 点号嵌套：${dto.productName}
        if (expr.contains(".")) {
            String[] parts = expr.split("\\.", 2);
            Object obj = payload.get(parts[0]);
            if (obj != null) {
                return getPropertyValue(obj, parts[1]);
            }
            return null;
        }

        // 3. 自动扫描：从payload中所有Java Bean对象查找匹配的getter
        for (Object obj : payload.values()) {
            if (obj == null || obj instanceof String) continue;
            String val = getPropertyValue(obj, expr);
            if (val != null) return val;
        }

        return null;
    }

    /**
     * 通过getter反射获取对象的属性值
     */
    private String getPropertyValue(Object obj, String property) {
        try {
            PropertyDescriptor pd = new PropertyDescriptor(property, obj.getClass());
            Object val = pd.getReadMethod().invoke(obj);
            return val != null ? String.valueOf(val) : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
