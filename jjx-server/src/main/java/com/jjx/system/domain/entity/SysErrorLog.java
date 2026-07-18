package com.jjx.system.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_error_log")
public class SysErrorLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private Long userId;
    private String username;
    private String exceptionName;
    private String exceptionMsg;
    private String requestUrl;
    private String requestMethod;
    private String requestParams;
    private String clientIp;
    private LocalDateTime triggerTime;
    private Integer handleStatus;
    private String handleRemark;
    private LocalDateTime handleTime;
    private String handleBy;
}
