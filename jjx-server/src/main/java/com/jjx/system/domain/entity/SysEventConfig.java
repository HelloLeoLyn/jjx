package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_event_config")
public class SysEventConfig {
    @TableId(type = IdType.AUTO)
    private Long eventId;
    private String eventCode;
    private String eventName;
    private String bizModule;
    private Integer isEnabled;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
