package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Persistent source of truth for business number sequences. */
@Data
@TableName("sys_number_sequence")
public class SysNumberSequence {
    @TableId(type = IdType.AUTO)
    private Long sequenceId;
    private String sequenceKey;
    private String periodKey;
    private Long currentValue;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
