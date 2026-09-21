package com.jjx.event.impl;

import cn.hutool.json.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jjx.event.EventPublisher;
import com.jjx.kanban.enums.KanbanTaskStatusEnum;
import com.jjx.notification.domain.dto.NotificationCreateDTO;
import com.jjx.notification.domain.entity.Notification;
import com.jjx.notification.mapper.NotificationMapper;
import com.jjx.notification.service.NotificationService;
import com.jjx.system.domain.entity.SysEventConfig;
import com.jjx.system.domain.entity.SysTask;
import com.jjx.system.domain.entity.SysUserRole;
import com.jjx.system.mapper.SysEventConfigMapper;
import com.jjx.system.mapper.SysTaskMapper;
import com.jjx.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地事件联动器
 * 查 sys_event_config 统一配置 → 发通知 + 创任务
 * 同进程同步执行，事务内执行，失败回滚
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final SysEventConfigMapper eventConfigMapper;
    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;
    private final SysTaskMapper sysTaskMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public void fire(String eventCode, Map<String, Object> payload) {
        try {
            applicationEventPublisher.publishEvent(payload);
        } catch (Exception e) {
            log.warn("发布 Spring 本地事件失败，不影响后续通知逻辑: eventCode={}", eventCode, e);
        }

        SysEventConfig event = eventConfigMapper.selectOne(
                new LambdaQueryWrapper<SysEventConfig>()
                        .eq(SysEventConfig::getEventCode, eventCode)
                        .eq(SysEventConfig::getIsEnabled, 1)
        );
        if (event == null) {
            // 2026-09-21（dev-20260921-005）：事件未配置是正常状态（如 quality.iqc.item.approved 未启用），
            // 原来按 WARN 打，每次逐项审核都刷一条噪音、掩盖真问题。降级为 DEBUG。
            log.debug("事件[{}]未配置或已禁用，跳过", eventCode);
            return;
        }
        log.info("🔥 触发事件: {} ({})", eventCode, event.getEventName());

        String eventType = event.getEventType() != null ? event.getEventType() : "notification";

        // 通知处理：展开角色→查用户→每人发一条（exclude_trigger=1 时排除触发者，DEV-641）
        boolean notificationEnabled = "notification".equals(eventType) || "both".equals(eventType);
        boolean notificationMissingTitle = notificationEnabled
                && (event.getTitle() == null || event.getTitle().trim().isEmpty());
        if (notificationMissingTitle) {
            // 发布前校验：启用配置缺通知标题时明确报错并跳过，避免拖到 sys_notification.title NOT NULL 约束才暴露
            log.error("❌ 事件配置缺少通知标题，已跳过通知: eventCode={}, eventId={}，请补全 sys_event_config.title",
                    eventCode, event.getEventId());
        }
        if (notificationEnabled && !notificationMissingTitle) {
            Long directReceiverId = payloadLong(payload, "receiverId");
            if (directReceiverId != null) {
                createNotification(event, eventCode, payload, directReceiverId);
            } else {
                JSONArray roles = parseRoles(event.getTargetRole());
                if (roles == null) {
                    log.warn("事件[{}]未解析到动态收件人或目标角色，跳过通知", eventCode);
                } else {
                    Object triggerUserId = payload != null ? payload.get("triggerUserId") : null;
                    boolean excludeTrigger = event.getExcludeTrigger() != null && event.getExcludeTrigger() == 1;
                    for (int i = 0; i < roles.size(); i++) {
                        Long roleId = roles.getLong(i);
                        List<SysUserRole> userRoles = userRoleMapper.selectList(
                                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId)
                        );
                        for (SysUserRole ur : userRoles) {
                            // 排除触发者：自己操作不通知自己
                            if (excludeTrigger && triggerUserId != null
                                    && ur.getUserId() != null
                                    && ur.getUserId().toString().equals(triggerUserId.toString())) {
                                continue;
                            }
                            createNotification(event, eventCode, payload, ur.getUserId());
                        }
                    }
                }
            }
        }

        // 任务处理：派给角色级别，排除触发者（待任务系统对接后生效）
        if ("task".equals(eventType) || "both".equals(eventType)) {
            Long directAssigneeId = payloadLong(payload, "receiverId");
            Long assignRole = parseSingleRole(event.getTargetRole());
            if (directAssigneeId != null || assignRole != null) {
                try {
                    SysTask task = new SysTask();
                    String rawCode = eventCode + "-" + System.currentTimeMillis();
                    // task_code 列宽 50，超长截断（事件前缀长如 inventory.outbound.created_from_production）
                    task.setTaskCode(rawCode.length() > 50 ? rawCode.substring(0, 50) : rawCode);
                    task.setTitle(resolveTemplate(event.getTitle(), payload));
                    task.setDescription(resolveTemplate(event.getContent(), payload));
                    task.setTaskType(eventCode.contains("sample") ? "sample" : "general");
                    task.setStartTime(java.time.LocalDateTime.now());
                    task.setSourceEvent(eventCode);
                    Long bizId = payloadLong(payload, "bizId");
                    if (bizId != null) {
                        task.setBizId(bizId);
                    }
                    if (directAssigneeId != null) {
                        task.setAssigneeId(directAssigneeId);
                        Object receiverName = payload == null ? null : payload.get("receiverName");
                        if (receiverName != null) task.setAssigneeName(String.valueOf(receiverName));
                    } else {
                        task.setAssignRole(assignRole);
                    }
                    // 业务类型：优先用事件配置的 biz_module（如 sales/purchase），回退 payload
                    Object bizTypeVal = payload != null ? payload.get("bizType") : null;
                    String bizType = event.getBizModule();
                    if (bizType == null || bizType.isEmpty()) {
                        if (bizTypeVal != null) {
                            bizType = String.valueOf(bizTypeVal).replace("'", "");
                        }
                    }
                    if (bizType != null && !bizType.isEmpty() && !"null".equals(bizType)) {
                        task.setBizType(bizType);
                    }
                    task.setPriority(event.getPriority() != null ? event.getPriority() : "normal");
                    // 2026-09-21：看板模块取值统一为 biz/prod/dev（对齐看板页签），不再用 office/emergency
                    task.setKanbanModule(event.getKanbanModule() != null ? event.getKanbanModule() : "biz");
                    task.setStatus(0);
                    // 2026-09-18：触发人落到 create_by（看板卡详情「创建人」，此前为空）
                    String taskTriggerUserName = payloadString(payload, "triggerUserName");
                    if (taskTriggerUserName != null) {
                        task.setCreateBy(taskTriggerUserName);
                    }
                    sysTaskMapper.insert(task);
                    log.info("   📋 任务已创建: title={}, assigneeId={}, assignRole={}",
                            event.getTitle(), directAssigneeId, assignRole);
                } catch (Exception e) {
                    log.error("   ❌ 创建任务失败: {}", e.getMessage());
                }
            }
        }

        // 办结事件按配置关闭同一业务的 office 待办任务，并将同源未读通知置为已读。
        try {
            String closeSourceEvents = event.getCloseSourceEvents();
            Object payloadBizId = payload == null ? null : payload.get("bizId");
            String bizIdText = payloadBizId == null ? null : String.valueOf(payloadBizId).trim();
            if (closeSourceEvents != null && !closeSourceEvents.trim().isEmpty()
                    && bizIdText != null && !bizIdText.isEmpty()) {
                Long taskBizId = payloadLong(payload, "bizId");
                LocalDateTime now = LocalDateTime.now();
                for (String sourceEventValue : closeSourceEvents.split(",")) {
                    String sourceEvent = sourceEventValue.trim();
                    if (sourceEvent.isEmpty()) {
                        continue;
                    }
                    if (taskBizId != null) {
                        sysTaskMapper.update(null, new LambdaUpdateWrapper<SysTask>()
                                // 兼容历史 office 值（2026-09-21 统一为 biz，旧卡仍应能被办结关闭）
                                .in(SysTask::getKanbanModule, "biz", "office")
                                .eq(SysTask::getSourceEvent, sourceEvent)
                                .eq(SysTask::getBizId, taskBizId)
                                .in(SysTask::getStatus,
                                        KanbanTaskStatusEnum.PENDING.getValue(),
                                        KanbanTaskStatusEnum.IN_PROGRESS.getValue())
                                .set(SysTask::getStatus, KanbanTaskStatusEnum.COMPLETED.getValue())
                                .set(SysTask::getCompletedTime, now));
                    }
                    notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                            .eq(Notification::getBizType, sourceEvent)
                            .eq(Notification::getBizId, bizIdText)
                            .eq(Notification::getIsRead, 0)
                            .set(Notification::getIsRead, 1)
                            .set(Notification::getReadTime, now));
                }
            }
        } catch (Exception e) {
            log.warn("办结关闭 office 任务/通知失败，不影响事件主流程: eventCode={}", eventCode, e);
        }

        log.info("✅ 事件[{}]处理完成", eventCode);
    }

    private JSONArray parseRoles(String targetRole) {
        if (targetRole == null || targetRole.isEmpty()) return null;
        try { return new JSONArray(targetRole); }
        catch (Exception e) { return null; }
    }

    private Long parseSingleRole(String targetRole) {
        JSONArray arr = parseRoles(targetRole);
        if (arr == null || arr.isEmpty()) return null;
        return arr.getLong(0);
    }

    private Long payloadLong(Map<String, Object> payload, String key) {
        if (payload == null || payload.get(key) == null) return null;
        Object value = payload.get(key);
        if (value instanceof Number number) return number.longValue();
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    /** 取 payload 里的字符串值；空串与 "null" 都当没有（模板里可能带上 null 字样）。 */
    private String payloadString(Map<String, Object> payload, String key) {
        if (payload == null || payload.get(key) == null) return null;
        String value = String.valueOf(payload.get(key));
        return value.isBlank() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private void createNotification(SysEventConfig event, String eventCode,
                                    Map<String, Object> payload, Long receiverId) {
        try {
            NotificationCreateDTO dto = new NotificationCreateDTO();
            dto.setTitle(resolveTemplate(event.getTitle(), payload));
            dto.setContent(resolveTemplate(event.getContent(), payload));
            dto.setNotificationType("system");
            dto.setBizType(eventCode);
            Object bizId = payload == null ? null : payload.get("bizId");
            dto.setBizId(bizId == null ? null : String.valueOf(bizId));
            dto.setReceiverId(receiverId);
            dto.setPriority(event.getPriority() != null ? event.getPriority() : "normal");
            // 2026-09-18：触发人落库到「发送人」（此前 sender_id/sender_name 一直为 NULL，
            // 通知列表看不出是谁触发的；事件 payload 里本来就有 triggerUserId/triggerUserName）
            // 2026-09-21（dev-20260921-007）：发送人用显示名（triggerRealName），没有才回退账号名。
            Long triggerUserId = payloadLong(payload, "triggerUserId");
            if (triggerUserId != null) {
                dto.setSenderId(triggerUserId);
            }
            String triggerDisplayName = payloadString(payload, "triggerRealName");
            if (triggerDisplayName == null) {
                triggerDisplayName = payloadString(payload, "triggerUserName");
            }
            if (triggerDisplayName != null) {
                dto.setSenderName(triggerDisplayName);
            }
            notificationService.createNotification(dto);
            log.debug("   📨 通知已创建: event={}, userId={}", eventCode, receiverId);
        } catch (Exception e) {
            log.error("   ❌ 通知失败: userId={}, {}", receiverId, e.getMessage());
        }
    }

    private String resolveTemplate(String template, Map<String, Object> payload) {
        if (template == null || payload == null) return template;
        String result = template;
        // 兼容两种写法：${xxx} 和 {xxx}
        Pattern pattern = Pattern.compile("\\$?\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(result);
        while (matcher.find()) {
            String expr = matcher.group(1);
            Object val = payload.get(expr);
            if (val != null) {
                result = result.replace(matcher.group(0), String.valueOf(val));
            }
        }
        return result;
    }
}
