package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_event_kanban")
public class SysEventKanban {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private String kanbanType;
    private String targetColumn;
    private String cardTitleTemplate;
    private String cardDescTemplate;
    private Long assignRoleId;
    private Integer isEnabled;
    private LocalDateTime createTime;
}
