package com.jjx.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 检验批事实快照；旧数据不补造。 */
@Data
@TableName("quality_lot_history")
public class QualityLotHistory {
    @TableId(type = IdType.AUTO)
    private Long historyId;
    private Long lotId;
    private String eventType;
    private String snapshotJson;
    private String operatorName;
    private String remark;
    private LocalDateTime createTime;
}
