package com.jjx.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审核状态枚举
 */
@Getter
public enum ApproveStatusEnum implements BizStatusEnum {
    
    DRAFT(1, "草稿", "info", true),
    PENDING(2, "待审批", "warning", false),
    APPROVED(3, "已批准", "success", false),
    REJECTED(4, "已拒绝", "danger", true),
    CANCELLED(5, "已取消", "info", true);
    

    private final Integer value;
    private final String label;
    private final String tagType;
    private final boolean editable;

    ApproveStatusEnum(Integer value, String label, String tagType, boolean editable) {
        this.value = value;
        this.label = label;
        this.tagType = tagType;
        this.editable = editable;
    }

    private static final Map<Integer, ApproveStatusEnum> VALUE_MAP =
        Arrays.stream(values()).collect(Collectors.toMap(ApproveStatusEnum::getValue, e -> e));

    public static ApproveStatusEnum getByValue(Integer value) {
        ApproveStatusEnum status = VALUE_MAP.get(value);
        if (status == null) {
            throw new IllegalArgumentException("无效的审核状态码: " + value);
        }
        return status;
    }
    public static boolean isEditable(Integer value) {
        return value == DRAFT.getValue() || value == REJECTED.getValue();
    }
    public static boolean isValidValue(Integer value) {
        return VALUE_MAP.containsKey(value);
    }

    /**
     * 判断是否为可取消状态（草稿和已拒绝可取消，DEV-1230 从 purchase 版合并）
     */
    public boolean isCancelable() {
        return this == DRAFT || this == REJECTED;
    }

    /**
     * 判断是否为可审批状态（DEV-1230 从 purchase 版合并）
     */
    public boolean isApprovable() {
        return this == PENDING;
    }
}