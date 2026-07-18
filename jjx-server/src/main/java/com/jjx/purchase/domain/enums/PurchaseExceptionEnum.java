package com.jjx.purchase.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 采购业务异常枚举
 * 错误码范围：10000-10999
 */
@Getter
@RequiredArgsConstructor
public enum PurchaseExceptionEnum {

    // ==================== 采购订单异常 (10000-10099) ====================

    /**
     * 采购订单不存在
     */
    ORDER_NOT_FOUND(10000, "采购订单不存在"),

    /**
     * 订单号已存在
     */
    ORDER_NO_DUPLICATE(10001, "订单号已存在"),

    /**
     * 订单ID不能为空
     */
    ORDER_ID_REQUIRED(10002, "订单ID不能为空"),

    /**
     * 订单明细不能为空
     */
    ORDER_ITEMS_EMPTY(10003, "订单明细不能为空"),

    /**
     * 保存订单失败
     */
    ORDER_SAVE_FAILED(10004, "保存订单失败"),

    /**
     * 更新订单失败
     */
    ORDER_UPDATE_FAILED(10005, "更新订单失败"),

    /**
     * 复制订单失败
     */
    ORDER_COPY_FAILED(10006, "复制订单失败"),

    /**
     * 源订单不存在
     */
    SOURCE_ORDER_NOT_FOUND(10007, "源订单不存在"),

    /**
     * 没有可导出的数据
     */
    ORDER_EXPORT_NO_DATA(10008, "没有可导出的数据"),

    /**
     * 导出失败
     */
    ORDER_EXPORT_FAILED(10009, "导出失败"),

    // ==================== 采购订单状态异常 (10100-10199) ====================

    /**
     * 订单状态错误
     */
    ORDER_STATUS_ERROR(10100, "订单状态错误"),

    /**
     * 只有草稿或已拒绝状态的订单可以修改
     */
    ORDER_NOT_EDITABLE(10101, "只有草稿或已拒绝状态的订单可以修改"),

    /**
     * 只有草稿或已拒绝状态的订单可以提交审批
     */
    ORDER_NOT_SUBMITTABLE(10102, "只有草稿或已拒绝状态的订单可以提交审批"),

    /**
     * 只有待审批状态的订单可以审批
     */
    ORDER_NOT_APPROVABLE(10103, "只有待审批状态的订单可以审批"),

    /**
     * 只有待审批或已批准状态的订单可以收货
     */
    ORDER_NOT_RECEIVABLE(10104, "只有待审批或已批准状态的订单可以收货"),

    /**
     * 只有草稿状态的订单可以删除
     */
    ORDER_NOT_DELETABLE(10105, "只有草稿或已拒绝状态的订单可以删除"),

    /**
     * 订单已取消
     */
    ORDER_ALREADY_CANCELLED(10106, "订单已取消"),

    /**
     * 订单已完成
     */
    ORDER_ALREADY_COMPLETED(10107, "订单已完成"),

    // ==================== 供应商异常 (10200-10299) ====================

    /**
     * 供应商信息不完整
     */
    SUPPLIER_INFO_INCOMPLETE(10200, "供应商信息不完整"),

    /**
     * 供应商不存在
     */
    SUPPLIER_NOT_FOUND(10201, "供应商不存在"),

    // ==================== 订单明细异常 (10300-10399) ====================

    /**
     * 订单明细不存在
     */
    ORDER_ITEM_NOT_FOUND(10300, "订单明细不存在"),

    /**
     * 订单明细项ID不能为空
     */
    ORDER_ITEM_ID_REQUIRED(10301, "订单明细项ID不能为空"),

    // ==================== 批量操作异常 (10400-10499) ====================

    /**
     * 请选择要操作的订单
     */
    BATCH_NO_ORDERS_SELECTED(10400, "请选择要操作的订单"),

    /**
     * 批量提交失败
     */
    BATCH_SUBMIT_FAILED(10401, "批量提交失败"),

    /**
     * 批量操作部分失败
     */
    BATCH_PARTIAL_FAILURE(10402, "批量操作部分失败"),

    // ==================== 采购收货异常 (10500-10599) ====================

    /**
     * 收货数量不能为空
     */
    RECEIVE_QUANTITY_REQUIRED(10500, "收货数量不能为空"),

    /**
     * 收货数量不能超过订单数量
     */
    RECEIVE_QUANTITY_EXCEEDS(10501, "收货数量不能超过订单数量"),

    /**
     * 收货数量必须大于0
     */
    RECEIVE_QUANTITY_INVALID(10502, "收货数量必须大于0"),

    // ==================== 采购付款异常 (10600-10699) ====================

    /**
     * 付款金额不能为空
     */
    PAYMENT_AMOUNT_REQUIRED(10600, "付款金额不能为空"),

    /**
     * 付款金额不能超过订单总金额
     */
    PAYMENT_AMOUNT_EXCEEDS(10601, "付款金额不能超过订单总金额"),

    /**
     * 付款金额必须大于0
     */
    PAYMENT_AMOUNT_INVALID(10602, "付款金额必须大于0"),

    /**
     * 订单已付清
     */
    ORDER_ALREADY_PAID(10603, "订单已付清"),

    ;
    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 根据code获取枚举
     */
    public static PurchaseExceptionEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PurchaseExceptionEnum exception : values()) {
            if (exception.getCode().equals(code)) {
                return exception;
            }
        }
        return null;
    }

    /**
     * 根据code获取消息
     */
    public static String getMessageByCode(Integer code) {
        PurchaseExceptionEnum exception = getByCode(code);
        return exception != null ? exception.getMessage() : null;
    }
}
