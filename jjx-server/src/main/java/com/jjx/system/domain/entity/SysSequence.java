package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 序列号备份实体
 * 用于持久化 Redis 中的序列号，防止 Redis 宕机导致序列号丢失
 */
@Data
@TableName("sys_sequence")
public class SysSequence {

    /** 主键ID */
    @TableId
    private Long id;

    /** 业务代码 (SO, PCO, PO, WPO, CUST) */
    private String bizCode;

    /** 日期 (yyMMdd) */
    private String bizDate;

    /** 当前序列号值 */
    private Long currentSeq;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
