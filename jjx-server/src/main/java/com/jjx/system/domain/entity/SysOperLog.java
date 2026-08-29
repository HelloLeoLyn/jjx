// ==================== SysOperLog.java ====================
package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_oper_log")
public class SysOperLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String realName;
    private Long tenantId;
    private String module;
    private Integer businessType;
    private String operUrl;
    private String operIp;
    private String operParam;
    private String bizType;
    private String bizId;
    private String traceId;
    private String bizStatus;
    /** 详情（JSON，如附件列表 {"attachments":[{id,fileName}]}） */
    private String detail;

    private Long costTime;
    private Integer status;
    private String errorMsg;
    private String userAgent;
    private LocalDateTime createTime;
}
