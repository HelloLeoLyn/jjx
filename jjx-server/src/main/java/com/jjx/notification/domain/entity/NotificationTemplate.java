package com.jjx.notification.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("sys_notification_template")
public class NotificationTemplate {
    @TableId(type = IdType.AUTO)
    private Long templateId;
    private String templateCode;
    private String templateName;
    private String notificationType;
    private String titleTemplate;
    private String contentTemplate;
    private String variables;
    private String bizType;
    private Integer isEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
