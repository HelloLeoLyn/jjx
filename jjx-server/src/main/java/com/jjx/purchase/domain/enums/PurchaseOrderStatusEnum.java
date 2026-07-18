package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 采购订单状态枚举
 */
@Getter
public enum PurchaseOrderStatusEnum {

    /**
     * 草稿
     */
    DRAFT(0, "草稿"),

    /**
     * 询价中
     */
    INQUIRY(1, "询价中"),

    /**
     * 比价中
     */
    COMPARING(2, "比价中"),

    /**
     * 已提交
     */
    SUBMITTED(3, "已提交"),

    /**
     * 已批准
     */
    APPROVED(4, "已批准"),

    /**
     * 执行中
     */
    IN_PROGRESS(5, "执行中"),

    /**
     * 已完成
     */
    COMPLETED(6, "已完成"),

    /**
     * 已关闭
     */
    CLOSED(7, "已关闭"),

    /**
     * 已取消
     */
    CANCELLED(8, "已取消");

    private final Integer code;
    private final String description;

    PurchaseOrderStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static PurchaseOrderStatusEnum getByCode(Integer code) {
        if (code == null) return null;
        for (PurchaseOrderStatusEnum status : values()) {
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
        PurchaseOrderStatusEnum status = getByCode(code);
        return status != null ? status.getDescription() : null;
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
