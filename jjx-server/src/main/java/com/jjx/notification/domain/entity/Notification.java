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
    private String eventCode;
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
    private Integer status;
    private String failReason;
    private LocalDateTime sendTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
