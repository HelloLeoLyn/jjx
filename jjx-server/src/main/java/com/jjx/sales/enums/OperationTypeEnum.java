package com.jjx.sales.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 操作类型枚举
 * 对应数据库 operation_type 字段 (tinyint)
 */
@Getter
public enum OperationTypeEnum {

    CREATE(1, "创建订单"),
    UPDATE(2, "更新订单"),
    PAY(3, "支付"),
    SHIP(4, "发货"),
    CANCEL(5, "取消"),
    APPROVE(6, "审核通过"),
    REJECT(7, "审核驳回"),
    SUBMIT_REVIEW(8, "提交审核"),
    START_REVIEW(9, "开始审核"),
    RESUBMIT(10, "重新提交"),
    SEND_CONFIRM(11, "发送确认"),
    START_PRODUCTION(12, "开始生产"),
    UPDATE_PROGRESS(13, "更新进度"),
    COMPLETE(14, "完成订单");

    private final Integer code;
    private final String label;

    OperationTypeEnum(Integer code, String name) {
        this.code = code;
        this.label = name;
    }

    private static final Map<Integer, OperationTypeEnum> CODE_MAP =
            Arrays.stream(values()).collect(Collectors.toMap(OperationTypeEnum::getCode, e -> e));

    public static OperationTypeEnum getByCode(Integer code) {
        OperationTypeEnum type = CODE_MAP.get(code);
        if (type == null) {
            throw new IllegalArgumentException("无效的操作类型码: " + code);
        }
        return type;
    }

    public static boolean isValidCode(Integer code) {
        return CODE_MAP.containsKey(code);
    }

    /**
     * @deprecated 使用 { #getLabel()}
     */
    @Deprecated
    public String getName() {
        return label;
    }
}
