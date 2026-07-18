package com.jjx.inventory.enums;

public enum ProcessGroup {
    
    M("M", "panel", "面板"),
    U("U", "upper", "上线"),
    D("D", "down", "下线"),
    T("T", "shrapnel", "弹片"),
    L("L", "lightGuide", "导光片"),
    K("K", "diffuser", "扩散片"),
    P("P", "PRINT", "印刷"),
    OTHER("O", "other", "其他类型物料");

    private final String code;
    private final String englishName;
    private final String name;

    ProcessGroup(String code, String englishName, String name) {
        this.code = code;
        this.englishName = englishName;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getEnglishName() {
        return englishName;
    }


    public String getName() {
        return name;
    }

    public String getLabel() {
        return name;
    }

    public static ProcessGroup fromCode(String code) {
        for (ProcessGroup group : values()) {
            if (group.code.equals(code)) {
                return group;
            }
        }
        return null;
    }

    public static ProcessGroup fromEnglishName(String englishName) {
        for (ProcessGroup group : values()) {
            if (group.englishName.equals(englishName)) {
                return group;
            }
        }
        return OTHER;
    }
    public static ProcessGroup fromName(String name) {
        for (ProcessGroup group : values()) {
            if (group.name.equals(name)) {
                return group;
            }
        }
        return OTHER;
    }
    @Override
    public String toString() {
        return name;
    }
}