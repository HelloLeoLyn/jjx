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

    /**
     * 关联作业项目(标准工序)ID，NULL=自定义工序
     */
    private Long stdProcessId;

    /**
     * 工序顺序
     */
    private Integer processOrder;

    /**
     * 卡片项目结构（PANEL/UP_LINE/DOWN_LINE/OTHER，卡片级主结构）
     */
    private String processCategory;

    /**
     * 状态：0待做 1进行中 2完成
     */
    private Integer status;

    /** 该工序材料明细(JSON: [{name,spec,qty,unit}]) */
    private String materials;

    /** 工艺说明(怎么做的) */
    private String processNote;

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
