package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 样品打样轮次快照
 * 每轮打样归档：图纸 + 工艺参数 + 结果
 */
@Data
@TableName("sales_sample_round")
public class SalesSampleRound {

    /** 轮次记录ID */
    @TableId(type = IdType.AUTO)
    private Long roundId;

    /** 样品单ID */
    private Long orderId;

    /** 轮次号 */
    private Integer roundNo;

    /** 该轮工艺参数快照 */
    private String engineeringNote;

    /** 该轮图纸附件ID(JSON数组) */
    private String attachmentIds;

    /** 该轮结果: pending/confirmed/rejected */
    private String result;

    /** 该轮退回原因 */
    private String rejectReason;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
