package com.jjx.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知/任务模板渲染器（2026-09-21 dev-20260921-012）。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>占位符：{key} 或 ${key}（两种写法都兼容）；</li>
 *   <li>兜底写法：{key|备选键} —— 从左到右取第一个非 null 的值，
 *       让模板可以写成 {bizNo|bizId} 兼容还没带业务单号的事件；</li>
 *   <li>取不到的键：渲染成空串，并把键名回传给调用方（由调用方打 WARN 日志），
 *       不再像旧实现那样把「{inboundNo}」原样留在通知标题里。</li>
 * </ul>
 */
public final class EventTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$?\\{([^}]+)\\}");

    private EventTemplateRenderer() {
    }

    /** 渲染结果：rendered=渲染后文本；missingKeys=取不到值的占位符（原始表达式，如 bizNo 或 a|b）。 */
    public record Result(String rendered, List<String> missingKeys) {
    }

    public static String render(String template, Map<String, Object> payload) {
        return renderWithMissing(template, payload).rendered();
    }

    public static Result renderWithMissing(String template, Map<String, Object> payload) {
        if (template == null) {
            return new Result(null, List.of());
        }
        if (payload == null || payload.isEmpty()) {
            return new Result(template, List.of());
        }
        List<String> missing = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String expr = matcher.group(1);
            Object value = null;
            for (String candidate : expr.split("\\|")) {
                String key = candidate.trim();
                if (key.isEmpty()) {
                    continue;
                }
                Object v = payload.get(key);
                if (v != null) {
                    value = v;
                    break;
                }
            }
            if (value == null) {
                missing.add(expr.trim());
                matcher.appendReplacement(out, "");
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(String.valueOf(value)));
            }
        }
        matcher.appendTail(out);
        return new Result(out.toString(), missing);
    }
}
