package com.jjx.production.enums;

import lombok.Getter;

/**
 * 记录类型枚举
 */
@Getter
public enum RecordTypeEnum {

    /**
     * 开始记录 - 工序开始执行
     */
    START("START", "开始记录", "工序开始执行"),

    /**
     * 暂停记录 - 工序暂停执行
     */
    PAUSE("PAUSE", "暂停记录", "工序暂停执行"),

    /**
     * 恢复记录 - 工序恢复执行
     */
    RESUME("RESUME", "恢复记录", "工序恢复执行"),

    /**
     * 完成记录 - 工序完成执行
     */
    COMPLETE("COMPLETE", "完成记录", "工序完成执行"),

    /**
     * 质量记录 - 质量检查记录
     */
    QUALITY("QUALITY", "质量记录", "质量检查记录"),

    /**
     * 问题记录 - 生产问题记录
     */
    ISSUE("ISSUE", "问题记录", "生产问题记录"),

    /**
     * 参数记录 - 工艺参数记录
     */
    PARAMETER("PARAMETER", "参数记录", "工艺参数记录"),

    /**
     * 状态记录 - 状态变更记录
     */
    STATUS("STATUS", "状态记录", "状态变更记录"),

    /**
     * 操作记录 - 操作员操作记录
     */
    OPERATION("OPERATION", "操作记录", "操作员操作记录"),

    /**
     * 数据记录 - 生产数据记录
     */
    DATA("DATA", "数据记录", "生产数据记录"),

    /**
     * 设备记录 - 设备运行记录
     */
    EQUIPMENT("EQUIPMENT", "设备记录", "设备运行记录"),

    /**
     * 物料记录 - 物料使用记录
     */
    MATERIAL("MATERIAL", "物料记录", "物料使用记录"),

    /**
     * 工时记录 - 工时统计记录
     */
    TIME("TIME", "工时记录", "工时统计记录"),

    /**
     * 附件记录 - 附件上传记录
     */
    ATTACHMENT("ATTACHMENT", "附件记录", "附件上传记录"),

    /**
     * 备注记录 - 备注信息记录
     */
    REMARK("REMARK", "备注记录", "备注信息记录"),

    /**
     * 系统记录 - 系统自动记录
     */
    SYSTEM("SYSTEM", "系统记录", "系统自动记录");

    /**
     * 编码
     */
    private final String code;

    /**
     * 名称
     */
    private final String label;

    /**
     * 描述
     */
    private final String description;

    RecordTypeEnum(String code, String name, String description) {
        this.code = code;
        this.label = name;
        this.description = description;
    }

    /**
     * 根据编码获取枚举
     */
    public static RecordTypeEnum getByCode(String code) {
        for (RecordTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据名称获取枚举
     */
    public static RecordTypeEnum getByName(String name) {
        for (RecordTypeEnum type : values()) {
            if (type.getName().equals(name)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 检查编码是否存在
     */
    public static boolean containsCode(String code) {
        return getByCode(code) != null;
    }

    /**
     * 检查名称是否存在
     */
    public static boolean containsName(String name) {
        return getByName(name) != null;
    }

    /**
     * 获取所有编码
     */
    public static String[] getAllCodes() {
        RecordTypeEnum[] values = values();
        String[] codes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].getCode();
        }
        return codes;
    }

    /**
     * 获取所有名称
     */
    public static String[] getAllNames() {
        RecordTypeEnum[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].getName();
        }
        return names;
    }

    /**
     * 是否为操作记录
     */
    public boolean isOperationRecord() {
        return this == START || this == PAUSE || this == RESUME || this == COMPLETE || this == OPERATION;
    }

    /**
     * 是否为数据记录
     */
    public boolean isDataRecord() {
        return this == QUALITY || this == PARAMETER || this == DATA || this == MATERIAL || this == TIME;
    }

    /**
     * 是否为问题记录
     */
    public boolean isIssueRecord() {
        return this == ISSUE;
    }

    /**
     * 是否为设备记录
     */
    public boolean isEquipmentRecord() {
        return this == EQUIPMENT;
    }

    /**
     * 是否为系统记录
     */
    public boolean isSystemRecord() {
        return this == SYSTEM;
    }

    /**
     * 是否为附件记录
     */
    public boolean isAttachmentRecord() {
        return this == ATTACHMENT;
    }

    /**
     * 是否为备注记录
     */
    public boolean isRemarkRecord() {
        return this == REMARK;
    }

    /**
     * 是否需要操作员确认
     */
    public boolean requiresOperatorConfirmation() {
        return isOperationRecord() || isDataRecord() || isIssueRecord();
    }

    /**
     * 是否需要附件
     */
    public boolean requiresAttachment() {
        return this == ATTACHMENT || this == QUALITY || this == ISSUE;
    }

    /**
     * 是否需要参数
     */
    public boolean requiresParameters() {
        return this == PARAMETER || this == DATA || this == MATERIAL;
    }

    /**
     * 获取记录类型分类
     */
    public String getCategory() {
        if (isOperationRecord()) {
            return "operation";
        } else if (isDataRecord()) {
            return "data";
        } else if (isIssueRecord()) {
            return "issue";
        } else if (isEquipmentRecord()) {
            return "equipment";
        } else if (isSystemRecord()) {
            return "system";
        } else if (isAttachmentRecord()) {
            return "attachment";
        } else if (isRemarkRecord()) {
            return "remark";
        } else {
            return "other";
        }
    }

    /**
     * 获取优先级（数值越小优先级越高）
     */
    public int getPriority() {
        switch (this) {
            case ISSUE:
                return 1;
            case START:
                return 2;
            case COMPLETE:
                return 3;
            case PAUSE:
                return 4;
            case RESUME:
                return 5;
            case QUALITY:
                return 6;
            case PARAMETER:
                return 7;
            case DATA:
                return 8;
            case OPERATION:
                return 9;
            case EQUIPMENT:
                return 10;
            case MATERIAL:
                return 11;
            case TIME:
                return 12;
            case ATTACHMENT:
                return 13;
            case REMARK:
                return 14;
            case SYSTEM:
                return 15;
            default:
                return 99;
        }
    }

    /**
     * 获取显示文本（编码 + 名称）
     */
    public String getDisplayText() {
        return code + " - " + label;
    }

    /**
     * 获取详细描述
     */
    public String getDetailedDescription() {
        return label + "：" + description;
    }

    /**
     * 获取记录类型颜色（用于前端显示）
     */
    public String getColor() {
        switch (this) {
            case START:
                return "green";
            case PAUSE:
                return "orange";
            case RESUME:
                return "blue";
            case COMPLETE:
                return "success";
            case QUALITY:
                return "cyan";
            case ISSUE:
                return "red";
            case PARAMETER:
                return "purple";
            case STATUS:
                return "yellow";
            case OPERATION:
                return "geekblue";
            case DATA:
                return "lime";
            case EQUIPMENT:
                return "volcano";
            case MATERIAL:
                return "gold";
            case TIME:
                return "magenta";
            case ATTACHMENT:
                return "default";
            case REMARK:
                return "gray";
            case SYSTEM:
                return "processing";
            default:
                return "default";
        }
    }

    /**
     * 获取记录类型图标（用于前端显示）
     */
    public String getIcon() {
        switch (this) {
            case START:
                return "play";
            case PAUSE:
                return "pause";
            case RESUME:
                return "redo";
            case COMPLETE:
                return "check-circle";
            case QUALITY:
                return "safety-certificate";
            case ISSUE:
                return "exclamation-circle";
            case PARAMETER:
                return "sliders";
            case STATUS:
                return "swap";
            case OPERATION:
                return "user";
            case DATA:
                return "database";
            case EQUIPMENT:
                return "tool";
            case MATERIAL:
                return "box";
            case TIME:
                return "clock";
            case ATTACHMENT:
                return "paper-clip";
            case REMARK:
                return "edit";
            case SYSTEM:
                return "robot";
            default:
                return "file";
        }
    }

    /**
     * @deprecated 使用 { #getLabel()}
     */
    @Deprecated
    public String getName() {
        return label;
    }
}
