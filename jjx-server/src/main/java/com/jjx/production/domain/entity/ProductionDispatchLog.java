package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派工操作流水
 * 对应表：production_dispatch_log
 * ASSIGN指派/REASSIGN改派/REJECT退回/START开始/COMPLETE完成
 */
@Data
@TableName("production_dispatch_log")
public class ProductionDispatchLog {

    @TableId(type = IdType.AUTO)
    private Long logId;

    private Long dispatchId;
    private Long orderId;
    private String action;
    private String content;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createTime;
}
