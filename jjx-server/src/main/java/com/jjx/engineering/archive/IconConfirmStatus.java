package com.jjx.engineering.archive;

import lombok.Getter;

@Getter
public enum IconConfirmStatus {
    PENDING(0), CONFIRMED(1), IGNORED(2);
    private final int value;
    IconConfirmStatus(int value) { this.value = value; }
}
