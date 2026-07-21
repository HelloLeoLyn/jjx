package com.jjx.notification.domain.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationQueryDTO {
    private Long receiverId;
    private String notificationType;
    private String bizType;
    private Integer isRead;
    private String priority;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int pageNum = 1;
    private int pageSize = 20;
}
