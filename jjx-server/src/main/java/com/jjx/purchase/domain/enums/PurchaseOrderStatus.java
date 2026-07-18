package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 采购订单状态枚举
 */
@Getter
public enum PurchaseOrderStatus {

    /**
     * 草稿
     */
    DRAFT("draft", "草稿"),

    /**
     * 询价中
     */
    INQUIRY("inquiry", "询价中"),

    /**
     * 比价中
     */
    COMPARING("comparing", "比价中"),

    /**
     * 已提交
     */
    SUBMITTED("submitted", "已提交"),

    /**
     * 已批准
     */
    APPROVED("approved", "已批准"),

    /**
     * 执行中
     */
    IN_PROGRESS("in_progress", "执行中"),

    /**
     * 已完成
     */
    COMPLETED("completed", "已完成"),

    /**
     * 已关闭
     */
    CLOSED("closed", "已关闭"),

    /**
     * 已取消
     */
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String description;

    PurchaseOrderStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static PurchaseOrderStatus getByCode(String code) {
        for (PurchaseOrderStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为草稿状态
     */
    public boolean isDraft() {
        return this == DRAFT;
    }

    /**
     * 判断是否为可编辑状态
     */
    public boolean isEditable() {
        return this == DRAFT || this == INQUIRY || this == COMPARING;
    }

    /**
     * 判断是否为可提交状态
     */
    public boolean isSubmittable() {
        return this == DRAFT || this == INQUIRY || this == COMPARING;
    }

    /**
     * 判断是否为可审批状态
     */
    public boolean isApprovable() {
        return this == SUBMITTED;
    }

    /**
     * 判断是否为执行中状态
     */
    public boolean isInProgress() {
        return this == IN_PROGRESS;
    }

    /**
     * 判断是否为已完成状态
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }
}
