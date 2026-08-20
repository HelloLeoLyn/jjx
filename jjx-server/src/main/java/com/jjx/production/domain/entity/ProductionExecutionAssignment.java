package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工序作业分配（WP-B）
 * <p>
 * 回答"具体哪个执行人做多少"；不是第二套责任链。
 * 数量口径：effective = assigned - released；reported = 有效报工 qualified+defective 汇总（Projection 计算）。
 */
@Data
@TableName("production_execution_assignment")
public class ProductionExecutionAssignment {

    @TableId(type = IdType.AUTO)
    private Long assignmentId;

    private Long executionId;
    private Long orderId;
    private Long dispatchId;
    private Long dispatchNodeId;

    private Long assigneeId;
    private String assigneeName;

    private BigDecimal assignedQuantity;
    private BigDecimal releasedQuantity;

    private String assignmentStatus;

    private Long assignedBy;
    private String assignedByName;
    private LocalDateTime assignedAt;

    private Long cancelledBy;
    private LocalDateTime cancelledAt;
    private String cancelReason;

    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
