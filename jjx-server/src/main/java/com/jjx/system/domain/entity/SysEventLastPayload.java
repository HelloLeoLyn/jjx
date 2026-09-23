package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事件最近一次 payload（2026-09-23 dev-20260921-014）：
 * 供事件配置页「试渲染」用真实数据预览标题/正文；由 LocalEventPublisher.fire() 覆盖写。
 */
@Data
@TableName("sys_event_last_payload")
public class SysEventLastPayload {
    /** 事件码（主键，一事件一行） */
    @TableId(value = "event_code", type = IdType.INPUT)
    private String eventCode;
    /** 最近一次 payload（JSON 文本） */
    private String payload;
    /** 业务对象 ID（便于排查） */
    private String bizId;
    /** 最近一次触发时间 */
    private LocalDateTime updateTime;
}
