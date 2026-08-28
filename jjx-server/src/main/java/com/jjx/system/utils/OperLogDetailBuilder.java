package com.jjx.system.utils;

import cn.hutool.json.JSONUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一构造操作日志 detail，固定输出 changes 与 attachments 两个数组。
 */
public final class OperLogDetailBuilder {

    private OperLogDetailBuilder() {
    }

    public static String changes(List<String> changes) {
        return build(changes, List.of());
    }

    public static String attachments(List<?> attachments) {
        return build(List.of(), attachments);
    }

    public static String build(List<String> changes, List<?> attachments) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("changes", changes == null ? List.of() : changes);
        detail.put("attachments", attachments == null ? List.of() : attachments);
        return JSONUtil.toJsonStr(detail);
    }
}
