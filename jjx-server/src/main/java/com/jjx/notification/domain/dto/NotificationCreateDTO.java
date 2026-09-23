package com.jjx.notification.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationCreateDTO {
    @NotBlank(message = "标题不能为空")
    private String title;
    private String content;
    @NotBlank(message = "通知类型不能为空")
    private String notificationType;
    /**
     * 触发本通知的事件码（2026-09-23 dev-20260921-014 补）：
     * 事件配置页「最近一次实际渲染」按 event_code 反查，此前只写了 bizType → 页面恒空。
     */
    private String eventCode;
    /** 业务类型（历史口径：事件联动通知里放的是事件码，勿改语义） */
    private String bizType;
    private String bizId;
    private Long senderId;
    private String senderName;
    @NotNull(message = "接收者ID不能为空")
    private Long receiverId;
    private String receiverName;
    private String priority;
}
