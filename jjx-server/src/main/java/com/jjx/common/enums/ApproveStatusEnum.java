package com.jjx.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审核状态枚举
 */
@Getter
public enum ApproveStatusEnum {
    
    DRAFT(1, "草稿", "info", true),
    PENDING(2, "待审批", "warning", false),
    APPROVED(3, "已批准", "success", false),
    REJECTED(4, "已拒绝", "danger", true);
    

    private final Integer code;
    private final String name;
    private final String tagType;
    private final boolean editable;

    ApproveStatusEnum(Integer code, String name, String tagType, boolean editable) {
        this.code = code;
        this.name = name;
        this.tagType = tagType;
        this.editable = editable;
    }

    private static final Map<Integer, ApproveStatusEnum> CODE_MAP =
        Arrays.stream(values()).collect(Collectors.toMap(ApproveStatusEnum::getCode, e -> e));

    public static ApproveStatusEnum getByCode(Integer code) {
        ApproveStatusEnum status = CODE_MAP.get(code);
        if (status == null) {
            throw new IllegalArgumentException("无效的审核状态码: " + code);
        }
        return status;
    }
    public static boolean isEditable(Integer code) {
        return code == DRAFT.getCode() || code == REJECTED.getCode();
    }
    public static boolean isValidCode(Integer code) {
        return CODE_MAP.containsKey(code);
    }
}