package com.jjx.common.utils;

import java.util.Set;

/** IQC 原因/补充说明的精确清洗规则。 */
public final class ReasonSanitizer {

    /** 来源：现行界面提示串；数据库实测的人工转写变体。 */
    public static final Set<String> BLACKLIST = Set.of(
            "勾选多行可整批合格；录入弹窗内 Tab 移动、Enter 保存、可\"保存并下一行\"；实测记录可留空",
            "选多行可整批合格"
    );

    private ReasonSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        return BLACKLIST.contains(normalized) ? "" : normalized;
    }

    public static boolean isValidSupplement(String value, String derived) {
        String supplement = sanitize(value);
        if (supplement.isEmpty() || supplement.length() > 200) return false;
        if (supplement.equals(derived == null ? "" : derived.trim())) return false;
        return supplement.codePoints().anyMatch(Character::isLetterOrDigit);
    }
}
