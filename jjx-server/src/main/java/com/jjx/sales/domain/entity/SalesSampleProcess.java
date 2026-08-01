package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 打样工序历史记录
 * 每次工序变更追加一条记录，保留完整打样工序历史（DEV-454）
 */
@Data
@TableName("sales_sample_process")
public class SalesSampleProcess {

    /** 工序记录ID */
    @TableId(type = IdType.AUTO)
    private Long processId;

    /** 样品单ID(sales_order.order_id) */
    private Long orderId;

    /** 打样轮次 */
    private Integer roundNo;

    /** 工序名称 */
    private String processName;

    /** 操作人 */
    private String operator;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 耗时(分钟) */
    private Integer durationMinutes;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
