package com.jjx.system.enums;


import lombok.Getter;

@Getter
public enum UserType{
    SYSTEM("00", "系统用户"),
    NORMAL("99", "普通用户");

    private final String code;
    private final String desc;

    UserType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // 校验方法
    public static boolean isValid(String code) {
        if (code == null) return false;
        for (UserType type : values()) {
            if (type.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
