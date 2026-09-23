package com.jjx.production.enums;

import lombok.Getter;

/** 生产设备运行状态。 */
@Getter
public enum EquipmentStatusEnum {
    STANDBY(0, "待机中", true),
    RUNNING(1, "运行中", true),
    MAINTENANCE(2, "维护中", false),
    FAULT(3, "故障中", false);

    private final int value;
    private final String label;
    private final boolean available;

    EquipmentStatusEnum(int value, String label, boolean available) {
        this.value = value;
        this.label = label;
        this.available = available;
    }

    public static EquipmentStatusEnum getByValue(Integer value) {
        if (value == null) return null;
        for (EquipmentStatusEnum status : values()) {
            if (status.value == value) return status;
        }
        return null;
    }
}
