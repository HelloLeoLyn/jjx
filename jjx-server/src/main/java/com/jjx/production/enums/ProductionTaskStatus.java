package com.jjx.production.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductionTaskStatus {

    PENDING("PENDING", "未分配"),

    ACTIVE("ACTIVE", "进行中"),

    COMPLETED("COMPLETED", "已完成"),

    CANCELLED("CANCELLED", "已取消");

    private final String code;

    private final String description;
}