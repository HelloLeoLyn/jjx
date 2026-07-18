package com.jjx.system.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_login_log")
public class SysLoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private Long tenantId;
    private String loginType;
    private String loginIp;
    private String loginLocation;
    private String userAgent;
    private LocalDateTime loginTime;
    private Integer status;
    private String failReason;
}