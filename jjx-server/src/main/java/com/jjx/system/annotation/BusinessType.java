package com.jjx.system.annotation;

import lombok.Getter;

@Getter
public enum BusinessType {
    INSERT("新增",1),
    UPDATE("修改",2),
    DELETE("删除",3),
    EXPORT("导出",4),
    IMPORT("导入",5),
    APPROVE("审批",6),
    LOGIN("登录",7),
    LOGOUT("登出",8),
    OTHER("其他",9),
    RESET("重置密码",10);

    private final String desc;
    private final Integer code;

    BusinessType(String desc, Integer code) {
        this.desc = desc;
        this.code = code;
    }
}
