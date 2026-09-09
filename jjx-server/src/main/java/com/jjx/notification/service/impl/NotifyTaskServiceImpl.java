package com.jjx.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.notification.domain.dto.NotificationCreateDTO;
import com.jjx.notification.domain.dto.NotifyTaskDTO;
import com.jjx.notification.service.NotificationService;
import com.jjx.notification.service.NotifyTaskService;
import com.jjx.system.domain.entity.SysRole;
import com.jjx.system.domain.entity.SysTask;
import com.jjx.system.domain.entity.SysUserRole;
import com.jjx.system.mapper.SysRoleMapper;
import com.jjx.system.mapper.SysTaskMapper;
import com.jjx.system.mapper.SysUserRoleMapper;
import com.jjx.system.service.SysConfigService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 通用催办实现（2026-09-09 dev-20260909-002）
 * 展开 role_key → role_id → 成员 userId（去重）→ 每人一条站内信；任务 assign_role = 第一个角色。
 * 角色不存在 / 目标角色无任何成员 → 直接抛错（沿用"空角色通知静默丢失"教训，宁缺勿静默）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyTaskServiceImpl implements NotifyTaskService {

    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final NotificationService notificationService;
    private final SysTaskMapper sysTaskMapper;
    private final SysConfigService sysConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void notifyAndCreateTask(NotifyTaskDTO dto) {
        List<String> roleKeys = dto.getRoleKeys();
        // 未传 roleKeys → 取系统配置默认角色（dev-20260909-003：可在系统参数修改，不用改代码）
        if (roleKeys == null || roleKeys.isEmpty()) {
            String cfg = sysConfigService.getValue("notify_task_default_roles");
            if (cfg == null || cfg.trim().isEmpty()) {
                throw new BusinessException("未配置默认通知角色，请在系统参数设置 notify_task_default_roles");
            }
            roleKeys = new java.util.ArrayList<>();
            for (String key : cfg.split("[,，;；]")) {
                if (key != null && !key.trim().isEmpty()) {
                    roleKeys.add(key.trim());
                }
            }
        }
        if (roleKeys == null || roleKeys.isEmpty()) {
            throw new BusinessException("目标角色 roleKeys 不能为空");
        }
        // 1. 角色存在性校验（避免拼错 role_key 导致静默无收件人）
        List<SysRole> roles = sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleKey, roleKeys));
        Set<String> foundKeys = new HashSet<>();
        for (SysRole r : roles) {
            foundKeys.add(r.getRoleKey());
        }
        List<String> missing = new ArrayList<>();
        for (String key : roleKeys) {
            if (!foundKeys.contains(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("目标角色不存在: " + String.join(",", missing));
        }
        // 2. 展开成员（去重）
        List<Long> roleIds = new ArrayList<>();
        for (SysRole r : roles) {
            roleIds.add(r.getRoleId());
        }
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, roleIds));
        Set<Long> userIds = new HashSet<>();
        for (SysUserRole ur : userRoles) {
            if (ur.getUserId() != null) {
                userIds.add(ur.getUserId());
            }
        }
        if (userIds.isEmpty()) {
            throw new BusinessException("目标角色无成员，通知未发送（角色可能空置）: " + String.join(",", roleKeys));
        }
        // 3. 每人一条站内信（与 LocalEventPublisher 通知口径一致）
        String senderName = SecurityUtils.getUsername();
        Long senderId = SecurityUtils.getUserId();
        String priority = dto.getPriority() != null && !dto.getPriority().isEmpty()
                ? dto.getPriority() : "urgent";
        String bizIdText = dto.getBizId() == null ? null : String.valueOf(dto.getBizId());
        for (Long userId : userIds) {
            try {
                NotificationCreateDTO n = new NotificationCreateDTO();
                n.setTitle(dto.getTitle());
                n.setContent(dto.getContent());
                n.setNotificationType("system");
                n.setBizType(dto.getBizType());
                n.setBizId(bizIdText);
                n.setSenderId(senderId);
                n.setSenderName(senderName);
                n.setReceiverId(userId);
                n.setPriority(priority);
                notificationService.createNotification(n);
            } catch (Exception e) {
                log.error("通用催办发通知失败: userId={}, err={}", userId, e.getMessage());
                throw new BusinessException("通知发送失败");
            }
        }
        // 4. 建一条待办任务（assign 给第一个角色；与 LocalEventPublisher 任务口径一致）
        try {
            SysTask task = new SysTask();
            String prefix = "URGE-" + (dto.getBizType() != null && !dto.getBizType().isEmpty()
                    ? dto.getBizType() : "task");
            String rawCode = prefix + "-" + System.currentTimeMillis();
            task.setTaskCode(rawCode.length() > 50 ? rawCode.substring(0, 50) : rawCode);
            task.setTitle(dto.getTitle());
            task.setDescription(dto.getContent());
            task.setTaskType("general");
            task.setStartTime(LocalDateTime.now());
            task.setSourceEvent("common.notify-task");
            task.setBizId(dto.getBizId());
            task.setBizType(dto.getBizType());
            task.setAssignRole(roles.get(0).getRoleId());
            task.setPriority(priority);
            task.setKanbanModule(dto.getKanbanModule() != null && !dto.getKanbanModule().isEmpty()
                    ? dto.getKanbanModule() : "office");
            task.setStatus(0);
            task.setCreateBy(senderName);
            sysTaskMapper.insert(task);
            log.info("通用催办完成: title={}, roles={}, 通知 {} 人, 任务 assignRole={}",
                    dto.getTitle(), roleKeys, userIds.size(), roles.get(0).getRoleId());
        } catch (Exception e) {
            log.error("通用催办建任务失败: title={}, err={}", dto.getTitle(), e.getMessage());
            throw new BusinessException("待办任务创建失败");
        }
    }
}
