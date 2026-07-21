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
    private String bizType;
    private String bizId;
    private Long senderId;
    private String senderName;
    @NotNull(message = "接收者ID不能为空")
    private Long receiverId;
    private String receiverName;
    private String priority;
}
