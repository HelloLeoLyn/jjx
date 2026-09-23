package com.jjx.system.controller.system;

import com.jjx.common.constant.LogActions;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.system.annotation.Log;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.entity.SysEventConfig;
import com.jjx.system.domain.entity.SysEventLastPayload;
import com.jjx.system.mapper.SysEventConfigMapper;
import com.jjx.system.mapper.SysEventLastPayloadMapper;
import com.jjx.notification.domain.entity.Notification;
import com.jjx.notification.mapper.NotificationMapper;
import com.jjx.event.EventTemplateRenderer;
import com.jjx.event.EventVariableRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 事件配置管理（通知/任务）
 */
@Slf4j
@RestController
@RequestMapping("/system/event-config")
@RequiredArgsConstructor
public class EventConfigController extends BaseController {

    private final SysEventConfigMapper eventConfigMapper;
    private final NotificationMapper notificationMapper;
    /** 2026-09-23（dev-20260921-014）：最近一次真实 payload（试渲染）+ 模板键名校验用。 */
    private final SysEventLastPayloadMapper eventLastPayloadMapper;
    private final ObjectMapper objectMapper;

    /**
     * 列表（全量）
     */
    @GetMapping("/list")
    public Result<List<SysEventConfig>> list(SysEventConfig config) {
        List<SysEventConfig> list = eventConfigMapper.selectList(
                new LambdaQueryWrapper<SysEventConfig>()
                        .like(StringUtils.hasText(config.getEventCode()), SysEventConfig::getEventCode, config.getEventCode())
                        .like(StringUtils.hasText(config.getEventName()), SysEventConfig::getEventName, config.getEventName())
                        .eq(StringUtils.hasText(config.getEventType()), SysEventConfig::getEventType, config.getEventType())
                        .eq(StringUtils.hasText(config.getBizModule()), SysEventConfig::getBizModule, config.getBizModule())
                        .eq(config.getIsEnabled() != null, SysEventConfig::getIsEnabled, config.getIsEnabled())
                        .orderByAsc(SysEventConfig::getEventCode)
        );
        return Result.success(list);
    }

    /**
     * 分页列表
     */
    @GetMapping("/page")
    public Result<PageResult<SysEventConfig>> page(SysEventConfig config) {
        Page<SysEventConfig> page = eventConfigMapper.selectPage(
                new Page<>(getPageNum(), getPageSize()),
                new LambdaQueryWrapper<SysEventConfig>()
                        .like(StringUtils.hasText(config.getEventCode()), SysEventConfig::getEventCode, config.getEventCode())
                        .like(StringUtils.hasText(config.getEventName()), SysEventConfig::getEventName, config.getEventName())
                        .eq(StringUtils.hasText(config.getEventType()), SysEventConfig::getEventType, config.getEventType())
                        .eq(StringUtils.hasText(config.getBizModule()), SysEventConfig::getBizModule, config.getBizModule())
                        .eq(config.getIsEnabled() != null, SysEventConfig::getIsEnabled, config.getIsEnabled())
                        .orderByAsc(SysEventConfig::getEventCode)
        );
        return Result.success(new PageResult<>(page.getRecords(), page.getTotal()));
    }

    /**
     * 详情
     */
    @GetMapping("/{eventId}")
    public Result<SysEventConfig> getInfo(@PathVariable Long eventId) {
        SysEventConfig config = eventConfigMapper.selectById(eventId);
        return Result.success(config);
    }

    /** 配置页所需的可用变量与最近一次实际通知。 */
    @GetMapping("/{eventCode}/metadata")
    public Result<Map<String, Object>> metadata(@PathVariable String eventCode) {
        Notification latest = notificationMapper.selectOne(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getEventCode, eventCode)
                        .orderByDesc(Notification::getNotificationId)
                        .last("LIMIT 1"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("variables", EventVariableRegistry.variables(eventCode));
        result.put("latest", latest);
        // 2026-09-23（dev-20260921-014）：最近一次真实 payload —— 让「试渲染」用真实数据预览
        SysEventLastPayload lastPayload = eventLastPayloadMapper.selectById(eventCode);
        result.put("lastPayload", lastPayload == null ? null : parsePayload(lastPayload.getPayload()));
        result.put("lastPayloadTime", lastPayload == null ? null : lastPayload.getUpdateTime());
        result.put("payloadSource", lastPayload == null ? "sample" : "lastEvent");
        return Result.success(result);
    }

    /** 解析 sys_event_last_payload.payload 的 JSON 文本；坏数据不报错，返回 null。 */
    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析事件最近 payload 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 模板占位符键名校验（2026-09-23 dev-20260921-014）：
     * 未登记的键不作为错误，仅回传 warnings 供前端提示（不阻断保存）。
     */
    private List<String> validateTemplateVariables(SysEventConfig config) {
        Set<String> allowed = new LinkedHashSet<>();
        EventVariableRegistry.variables(config.getEventCode())
                .forEach(variable -> allowed.add(variable.key()));
        LinkedHashSet<String> unknown = new LinkedHashSet<>();
        List<String> templates = new ArrayList<>();
        templates.add(config.getTitle() == null ? "" : config.getTitle());
        templates.add(config.getContent() == null ? "" : config.getContent());
        for (String template : templates) {
            for (String expression : EventTemplateRenderer.placeholderExpressions(template)) {
                for (String candidate : expression.split("\\|")) {
                    String key = candidate.trim();
                    if (!key.isEmpty() && !allowed.contains(key)) {
                        unknown.add(key);
                    }
                }
            }
        }
        if (!unknown.isEmpty()) {
            log.warn("事件配置[{}] 模板含未登记变量（未阻断保存）: {}", config.getEventCode(), unknown);
        }
        return new ArrayList<>(unknown);
    }

    /**
     * 新增
     */
    @PostMapping
    @Log(module = "事件配置", businessType = BusinessType.INSERT, action = LogActions.EVENT_CONFIG_CREATE)
    @SaCheckPermission("system:eventConfig:add")
    public Result<Map<String, Object>> add(@Validated @RequestBody SysEventConfig config) {
        if (config.getIsEnabled() == null) config.setIsEnabled(1);
        if (config.getExcludeTrigger() == null) config.setExcludeTrigger(0);
        List<String> warnings = validateTemplateVariables(config);
        int rows = eventConfigMapper.insert(config);
        return rows > 0 ? Result.success(warningsData(warnings)) : Result.error("保存失败");
    }

    /**
     * 编辑
     */
    @PutMapping
    @Log(module = "事件配置", businessType = BusinessType.UPDATE, action = LogActions.EVENT_CONFIG_EDIT)
    @SaCheckPermission("system:eventConfig:edit")
    public Result<Map<String, Object>> edit(@Validated @RequestBody SysEventConfig config) {
        List<String> warnings = validateTemplateVariables(config);
        int rows = eventConfigMapper.updateById(config);
        return rows > 0 ? Result.success(warningsData(warnings)) : Result.error("保存失败");
    }

    private Map<String, Object> warningsData(List<String> warnings) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("warnings", warnings);
        return data;
    }

    /**
     * 删除
     */
    @DeleteMapping("/{eventIds}")
    @Log(module = "事件配置", businessType = BusinessType.DELETE, action = LogActions.EVENT_CONFIG_DELETE)
    @SaCheckPermission("system:eventConfig:delete")
    public Result<Void> remove(@PathVariable List<Long> eventIds) {
        return toAjax(eventConfigMapper.deleteBatchIds(eventIds));
    }
}
