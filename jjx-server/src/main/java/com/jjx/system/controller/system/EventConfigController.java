package com.jjx.system.controller.system;

import com.jjx.common.constant.LogActions;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.system.annotation.Log;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.entity.SysEventConfig;
import com.jjx.system.mapper.SysEventConfigMapper;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 事件配置管理（通知/任务）
 */
@RestController
@RequestMapping("/system/event-config")
@RequiredArgsConstructor
public class EventConfigController extends BaseController {

    private final SysEventConfigMapper eventConfigMapper;

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

    /**
     * 新增
     */
    @PostMapping
    @Log(module = "事件配置", businessType = BusinessType.INSERT, action = LogActions.EVENT_CONFIG_CREATE)
    @SaCheckPermission("system:eventConfig:add")
    public Result<Void> add(@Validated @RequestBody SysEventConfig config) {
        if (config.getIsEnabled() == null) config.setIsEnabled(1);
        if (config.getExcludeTrigger() == null) config.setExcludeTrigger(0);
        return toAjax(eventConfigMapper.insert(config));
    }

    /**
     * 编辑
     */
    @PutMapping
    @Log(module = "事件配置", businessType = BusinessType.UPDATE, action = LogActions.EVENT_CONFIG_EDIT)
    @SaCheckPermission("system:eventConfig:edit")
    public Result<Void> edit(@Validated @RequestBody SysEventConfig config) {
        return toAjax(eventConfigMapper.updateById(config));
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
