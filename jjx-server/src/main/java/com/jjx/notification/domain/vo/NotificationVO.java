package com.jjx.notification.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationVO {
    private Long notificationId;
    private String title;
    private String content;
    private String notificationType;
    private String bizType;
    private String bizId;
    /** 事件配置为模板，通知为生成时的路径快照。 */
    private String jumpPath;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String receiverName;
    private Integer isRead;
    private LocalDateTime readTime;
    private String priority;
    private String status;
    private LocalDateTime sendTime;
    private LocalDateTime createTime;
}
