package com.jjx.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysEventConfig;
import com.jjx.system.domain.entity.SysTask;
import com.jjx.system.mapper.SysEventConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 只读取事件配置，不维护事件前缀映射。通知保存快照，待办读取当前配置。 */
@Service
@RequiredArgsConstructor
public class EventJumpPathService {
    private final SysEventConfigMapper eventConfigMapper;

    public String resolve(String eventCode, Object bizId) {
        if (!StringUtils.hasText(eventCode)) return null;
        SysEventConfig config = eventConfigMapper.selectOne(new LambdaQueryWrapper<SysEventConfig>()
                .eq(SysEventConfig::getEventCode, eventCode));
        return config == null ? null : render(config.getJumpPath(), bizId);
    }

    /** 一批任务只查询一次配置，避免看板逐卡查库。 */
    public void populateTasks(Collection<SysTask> tasks) {
        var codes = tasks.stream().map(SysTask::getSourceEvent).filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (codes.isEmpty()) return;
        Map<String, String> paths = eventConfigMapper.selectList(new LambdaQueryWrapper<SysEventConfig>()
                        .in(SysEventConfig::getEventCode, codes)).stream()
                .filter(config -> StringUtils.hasText(config.getJumpPath()))
                .collect(Collectors.toMap(SysEventConfig::getEventCode, SysEventConfig::getJumpPath));
        tasks.forEach(task -> task.setJumpPath(render(paths.get(task.getSourceEvent()), task.getBizId())));
    }

    public static void validateTemplate(String path) {
        // 仅允许站内绝对路径；唯一模板变量为查询参数中的 {bizId}。
        String sample = path == null ? "" : path.replace("{bizId}", "1");
        if (path != null && path.length() > 512 || !sample.matches("^/(?!/)[A-Za-z0-9_/-]+(?:\\?[A-Za-z0-9_=&%.~-]+)?$")) {
            throw new BusinessException("跳转路径须为站内绝对路径，查询参数可使用 {bizId}，最多512字符");
        }
        if (path.split("\\?", 2)[0].contains("{")) {
            throw new BusinessException("{bizId} 仅支持放在查询参数中");
        }
    }

    public static String render(String template, Object bizId) {
        if (!StringUtils.hasText(template)) return null;
        validateTemplate(template);
        if (template.contains("{bizId}") && !StringUtils.hasText(Objects.toString(bizId, ""))) {
            // 没有业务编号时仍可到列表；只去掉依赖业务编号的参数。
            String[] parts = template.split("\\?", 2);
            String query = java.util.Arrays.stream(parts[1].split("&"))
                    .filter(param -> !param.contains("{bizId}")).collect(Collectors.joining("&"));
            return parts[0] + (query.isEmpty() ? "" : "?" + query);
        }
        String result = template.replace("{bizId}", URLEncoder.encode(Objects.toString(bizId, ""), StandardCharsets.UTF_8));
        if (result.length() > 512) throw new BusinessException("生成的跳转路径超过512字符");
        return result;
    }
}
