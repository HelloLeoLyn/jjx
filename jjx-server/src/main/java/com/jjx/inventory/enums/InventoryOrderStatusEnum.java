package com.jjx.inventory.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 单据状态枚举
 * 统一数字编码: 0草稿/1待审批/2已批准/3已驳回/4处理中/5已确认/6已出库/7已入库/8已关闭/9已取消/10已完成/11已处理/12调拨中
 */
@Getter
@AllArgsConstructor
public enum InventoryOrderStatusEnum implements BizStatusEnum {

    DRAFT(0, "草稿"),
    PENDING(1, "待审批"),
    APPROVED(2, "已批准"),
    REJECTED(3, "已驳回"),
    PROCESSING(4, "处理中"),
    CONFIRMED(5, "已确认"),
    OUT_CONFIRM(6, "已出库"),
    IN_CONFIRM(7, "已入库"),
    CLOSED(8, "已关闭"),
    CANCELLED(9, "已取消"),
    COMPLETED(10, "已完成"),
    PROCESSED(11, "已处理"),
    IN_PROGRESS(12, "调拨中");

    private final Integer value;
    private final String label;

    public static InventoryOrderStatusEnum getByValue(Integer value) {
        for (InventoryOrderStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }

}
