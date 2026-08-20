package com.jjx.production.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建工序作业分配入参（WP-B）
 * <p>
 * 支持一次提交多人：张三300/李四300/王五200；允许只分部分。
 * 整批事务原子：任一条不合法整体拒绝，0 写入。
 */
@Data
public class AssignmentCreateDTO {

    /** 工序执行ID */
    private Long executionId;

    /** 本次分配明细（同一 execution 多人） */
    private List<AssignmentItemDTO> assignments;

    /** 备注（可选，写入各条 cancel_reason 之外的一般不落，保留扩展） */
    private String remark;

    @Data
    public static class AssignmentItemDTO {
        /** 执行人ID */
        private Long assigneeId;
        /** 分配作业数量 */
        private BigDecimal quantity;
    }
}
