package com.jjx.sales.enums;

public enum SampleRoundResultEnum {
    PENDING("pending"), CONFIRMED("confirmed"), REJECTED("rejected");
    private final String value;
    SampleRoundResultEnum(String value) { this.value=value; }
    public String getValue() { return value; }
}
