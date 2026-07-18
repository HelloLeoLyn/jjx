package com.jjx.common.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;

/**
 * 日期时间工具类（统一规范）
 *
 * @author example
 * @version 2.0
 */
public final class DateUtils {

    // ==================== 常量定义 ====================
    public static final String PATTERN_DATE = "yyyy-MM-dd";
    public static final String PATTERN_TIME = "HH:mm:ss";
    public static final String PATTERN_DATE_TIME = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_DATE_TIME_MS = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String PATTERN_COMPACT = "yyyyMMddHHmmss";
    public static final String PATTERN_CHINESE = "yyyy年MM月dd日";

    // ==================== FastDateFormat（线程安全）====================
    private static final FastDateFormat DATE_FORMAT =
            FastDateFormat.getInstance(PATTERN_DATE);

    private static final FastDateFormat DATE_TIME_FORMAT =
            FastDateFormat.getInstance(PATTERN_DATE_TIME);

    private static final FastDateFormat DATE_TIME_MS_FORMAT =
            FastDateFormat.getInstance(PATTERN_DATE_TIME_MS);

    // ==================== DateTimeFormatter（Java 8）====================
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(PATTERN_DATE);

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(PATTERN_DATE_TIME);

    private DateUtils() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    // ==================== Date 转 String ====================

    /**
     * Date 转 String（默认格式：yyyy-MM-dd HH:mm:ss）
     */
    public static String formatDateTime(Date date) {
        return format(date, PATTERN_DATE_TIME);
    }

    /**
     * Date 转 String（yyyy-MM-dd）
     */
    public static String formatDate(Date date) {
        return format(date, PATTERN_DATE);
    }

    /**
     * Date 转 String（自定义格式）
     */
    public static String format(Date date, String pattern) {
        if (date == null || StringUtils.isBlank(pattern)) {
            return "";
        }
        FastDateFormat sdf = FastDateFormat.getInstance(pattern);
        return sdf.format(date);
    }

    /**
     * Date 转 String（使用 Java 8 API）
     */
    public static String formatWithJava8(Date date, String pattern) {
        if (date == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .format(formatter);
    }

    // ==================== 带默认值的转换 ====================

    /**
     * Date 转 String，如果为 null 返回默认值
     */
    public static String formatOrDefault(Date date, String defaultValue, String pattern) {
        if (date == null) {
            return defaultValue;
        }
        return format(date, pattern);
    }

    /**
     * Date 转 String，null 时返回空字符串
     */
    public static String formatOrEmpty(Date date, String pattern) {
        return formatOrDefault(date, "", pattern);
    }

    // ==================== Optional 风格 ====================

    /**
     * 使用 Optional 风格
     */
    public static Optional<String> safeFormat(Date date, String pattern) {
        return Optional.ofNullable(date)
                .map(d -> format(d, pattern));
    }

    // ==================== 常见格式快捷方法 ====================

    /**
     * 转换为紧凑格式：20240101123045
     */
    public static String formatCompact(Date date) {
        return format(date, PATTERN_COMPACT);
    }

    /**
     * 转换为中文格式：2024年01月01日
     */
    public static String formatChinese(Date date) {
        return format(date, PATTERN_CHINESE);
    }

    /**
     * 转换为带毫秒格式：2024-01-01 12:30:45.123
     */
    public static String formatWithMillis(Date date) {
        return format(date, PATTERN_DATE_TIME_MS);
    }
}
