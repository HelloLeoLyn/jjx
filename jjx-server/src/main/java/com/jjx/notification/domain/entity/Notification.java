package com.jjx.notification.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("sys_notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long notificationId;
    private String title;
    private String content;
    private String notificationType;
    private String bizType;
    private String bizId;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String receiverName;
    private Integer isRead;
    private LocalDateTime readTime;
    private String priority;
    private String status;
    private String failReason;
    private LocalDateTime sendTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
