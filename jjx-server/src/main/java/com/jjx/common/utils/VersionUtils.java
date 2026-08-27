package com.jjx.common.utils;

import java.util.List;

/**
 * 版本号工具（2026-08-10 DEV-765：统一三处版本递增逻辑）
 * 版本格式：V1.0 / V1 / V10.2（主版本号.次版本号，或仅主版本号）
 * 原三处实现：SampleOrderServiceImpl.computeNextVersion、
 *           EngineeringRoutingServiceImpl.computeNextRoutingVersion、
 *           EngineeringBomServiceImpl.getNextVersion —— 逻辑一致，抽公共
 */
public final class VersionUtils {

    private VersionUtils() {
    }

    /**
     * 计算下一个版本号：取所有版本主版本号最大值 + 1
     * 例：["V1.0", "V2.0", "V10.2"] → V11.0；空/全脏数据 → V1.0
     */
    public static String next(List<String> existingVersions) {
        int maxMajor = 0;
        if (existingVersions != null) {
            for (String v : existingVersions) {
                maxMajor = Math.max(maxMajor, parseMajor(v));
            }
        }
        return "V" + (maxMajor + 1) + ".0";
    }

    /**
     * 计算下一个版本号（单值）：当前版本主版本号 + 1
     * 例："V1.0" → V2.0；null/空/脏数据 → V1.0
     */
    public static String next(String currentVersion) {
        return "V" + (parseMajor(currentVersion) + 1) + ".0";
    }

    /**
     * 解析主版本号整数（V1.0 → 1，V10.2 → 10，v3 → 3，无前缀"2.5" → 2）
     * 解析失败返回 0
     */
    public static int parseMajor(String version) {
        if (version == null || version.isEmpty()) {
            return 0;
        }
        try {
            String s = version.trim().toUpperCase();
            if (s.startsWith("V")) {
                s = s.substring(1);
            }
            int dot = s.indexOf('.');
            if (dot >= 0) {
                s = s.substring(0, dot);
            }
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
