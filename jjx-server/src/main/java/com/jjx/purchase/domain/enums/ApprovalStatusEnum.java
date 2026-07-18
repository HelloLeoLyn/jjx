package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 采购订单审批状态枚举
 * 合并了原 order_status 和 approval_status
 * 1=草稿(draft), 2=已取消(cancelled), 3=待审批(pending), 4=已批准(approved), 5=已拒绝(rejected)
 */
@Getter
public enum ApprovalStatusEnum {

    /**
     * 草稿
     */
    DRAFT(1, "草稿"),

    /**
     * 已取消
     */
    CANCELLED(2, "已取消"),

    /**
     * 待审批
     */
    PENDING(3, "待审批"),

    /**
     * 已批准
     */
    APPROVED(4, "已批准"),

    /**
     * 已拒绝
     */
    REJECTED(5, "已拒绝");

    private final Integer code;
    private final String description;

    ApprovalStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static ApprovalStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ApprovalStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据code获取描述
     */
    public static String getDescriptionByCode(Integer code) {
        ApprovalStatusEnum status = getByCode(code);
        return status != null ? status.getDescription() : null;
    }

    /**
     * 判断是否为草稿状态
     */
    public boolean isDraft() {
        return this == DRAFT;
    }

    /**
     * 判断是否为可编辑状态（草稿和已拒绝可编辑）
     */
    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }

    /**
     * 判断是否为可提交状态（草稿和已拒绝可提交）
     */
    public boolean isSubmittable() {
        return this == DRAFT || this == REJECTED;
    }

    /**
     * 判断是否为可提交状态（草稿和已拒绝可提交）
     */
    public boolean isCancelable() {
        return this == DRAFT || this == REJECTED;
    }

    /**
     * 判断是否为可审批状态
     */
    public boolean isApprovable() {
        return this == PENDING;
    }

    /**
     * 判断是否为已批准
     */
    public boolean isApproved() {
        return this == APPROVED;
    }

    /**
     * 判断是否为已拒绝
     */
    public boolean isRejected() {
        return this == REJECTED;
    }

    /**
     * 判断是否为已取消
     */
    public boolean isCancelled() {
        return this == CANCELLED;
    }
}
