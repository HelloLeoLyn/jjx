package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 事件变量累积注册表（二期，dev-20260923-037）。 */
@Data
@TableName("sys_event_var")
public class SysEventVar {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String eventCode;
    private String varKey;
    private String description;
    private String example;
    private String source;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
