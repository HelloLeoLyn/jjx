package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_event_notification")
public class SysEventNotification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private Long roleId;
    private Long templateId;
    private Integer priority;
    private Integer isEnabled;
    private LocalDateTime createTime;
}
