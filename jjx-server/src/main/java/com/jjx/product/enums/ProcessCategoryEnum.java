package com.jjx.product.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工序类别枚举
 *
 * 同时是标准工序编码（SP-&lt;段位&gt;&lt;序号&gt;）段位的唯一真源：
 * 1 面板 / 2 上线 / 3 下线 / 4 其他（2026-09-16 定为单规则，原 5 段弹片/其他 与 T&lt;类型&gt;C&lt;类别&gt; 码已废弃）。
 */
@Getter
public enum ProcessCategoryEnum {
    
    PANEL("PANEL", "面板", "primary", 1),
    UP_LINE("UP_LINE", "上线", "primary", 2),
    DOWN_LINE("DOWN_LINE", "下线", "primary", 3),
    OTHER("OTHER", "其他", "info", 4);

    private final String code;
    private final String label;
    private final String tagType;
    /** 编码段位（SP-<段位><序号> 的第 1 位数字） */
    private final int segment;

    ProcessCategoryEnum(String code, String name, String tagType, int segment) {
        this.code = code;
        this.label = name;
        this.tagType = tagType;
        this.segment = segment;
    }

    private static final Map<String, ProcessCategoryEnum> CODE_MAP = 
        Arrays.stream(values()).collect(Collectors.toMap(ProcessCategoryEnum::getCode, e -> e));

    private static final Map<Integer, ProcessCategoryEnum> SEGMENT_MAP =
        Arrays.stream(values()).collect(Collectors.toMap(ProcessCategoryEnum::getSegment, e -> e));

    public static ProcessCategoryEnum getByCode(String code) {
        ProcessCategoryEnum category = CODE_MAP.get(code);
        if (category == null) {
            throw new IllegalArgumentException("无效的工序类别码: " + code);
        }
        return category;
    }

    /** 按段位取类别；无对应段位返回 null（供解析历史/异常编码时容错） */
    public static ProcessCategoryEnum getBySegment(Integer segment) {
        return segment == null ? null : SEGMENT_MAP.get(segment);
    }

    /** 段位是否合法（1-4） */
    public static boolean isValidSegment(int segment) {
        return SEGMENT_MAP.containsKey(segment);
    }

    public static boolean isValidCode(String code) {
        return code != null && CODE_MAP.containsKey(code);
    }

    /**
     * @deprecated 使用 { #getLabel()}
     */
    @Deprecated
    public String getName() {
        return label;
    }
}
