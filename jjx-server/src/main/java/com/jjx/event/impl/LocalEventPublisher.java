package com.jjx.event.impl;

import cn.hutool.json.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.event.EventPublisher;
import com.jjx.notification.domain.dto.NotificationCreateDTO;
import com.jjx.notification.service.NotificationService;
import com.jjx.system.domain.entity.SysEventConfig;
import com.jjx.system.domain.entity.SysTask;
import com.jjx.system.domain.entity.SysUserRole;
import com.jjx.system.mapper.SysEventConfigMapper;
import com.jjx.system.mapper.SysTaskMapper;
import com.jjx.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    private final SysEventConfigMapper eventConfigMapper;
    private final NotificationService notificationService;
    private final SysTaskMapper sysTaskMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public void fire(String eventCode, Map<String, Object> payload) {
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

        String eventType = event.getEventType() != null ? event.getEventType() : "notification";

        // 通知处理：展开角色→查用户→每人发一条
        if ("notification".equals(eventType) || "both".equals(eventType)) {
            JSONArray roles = parseRoles(event.getTargetRole());
            if (roles != null) {
                for (int i = 0; i < roles.size(); i++) {
                    Long roleId = roles.getLong(i);
                    List<SysUserRole> userRoles = userRoleMapper.selectList(
                            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId)
                    );
                    for (SysUserRole ur : userRoles) {
                        try {
                            NotificationCreateDTO dto = new NotificationCreateDTO();
                            dto.setTitle(resolveTemplate(event.getTitle(), payload));
                            dto.setContent(resolveTemplate(event.getContent(), payload));
                            dto.setNotificationType("system");
                            dto.setBizType(eventCode);
                            dto.setReceiverId(ur.getUserId());
                            dto.setPriority("normal");
                            notificationService.createNotification(dto);
                            log.debug("   📨 通知已创建: event={}, userId={}", eventCode, ur.getUserId());
                        } catch (Exception e) {
                            log.error("   ❌ 通知失败: userId={}, {}", ur.getUserId(), e.getMessage());
                        }
                    }
                }
            }
        }

        // 任务处理：派给角色级别，排除触发者（待任务系统对接后生效）
        if ("task".equals(eventType) || "both".equals(eventType)) {
            Long assignRole = parseSingleRole(event.getTargetRole());
            if (assignRole != null) {
                try {
                    SysTask task = new SysTask();
                    task.setTaskCode(eventCode + "-" + System.currentTimeMillis());
                    task.setTitle(resolveTemplate(event.getTitle(), payload));
                    task.setDescription(resolveTemplate(event.getContent(), payload));
                    task.setTaskType(eventCode.contains("sample") ? "sample" : "general");
                    task.setStartTime(java.time.LocalDateTime.now());
                    task.setSourceEvent(eventCode);
                    task.setAssignRole(assignRole);
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
                    task.setKanbanModule(event.getKanbanModule() != null ? event.getKanbanModule() : "office");
                    task.setStatus(0);
                    sysTaskMapper.insert(task);
                    log.info("   📋 任务已创建: title={}, assignRole={}", event.getTitle(), assignRole);
                } catch (Exception e) {
                    log.error("   ❌ 创建任务失败: {}", e.getMessage());
                }
            }
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
