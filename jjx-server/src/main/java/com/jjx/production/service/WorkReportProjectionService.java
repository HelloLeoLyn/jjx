package com.jjx.production.service;

import com.jjx.production.domain.entity.ProductionOperationExecution;

import java.math.BigDecimal;

/**
 * WorkReport Projection 服务（P3）
 * Execution 数量/工时字段 = WorkReport Projection（SUM APPROVED），用户不再直接维护。
 * 只有 APPROVED 报工才是有效完成事实；PENDING/REJECTED/CANCELLED 均不计入。
 */
public interface WorkReportProjectionService {

    /**
     * 事务内重算 execution projection：
     * qualified = SUM(qualified) / defective = SUM(defective)
     * output = SUM(qualified+defective) / labor = SUM(labor) / machine = SUM(machine)
     * 仅统计 report_status = 'APPROVED'。
     */
    void recalculate(Long executionId);

    /**
     * Projection 一致性诊断（P2 Final Gate 工具，非业务 API）：
     * MATCH / MISMATCH / NO_REPORT_LEGACY / EMPTY
     */
    String compareProjection(Long executionId);

    /** APPROVED 报工数量聚合（供诊断/展示） */
    BigDecimal[] sumApproved(Long executionId);

    /** 是否存在 APPROVED 报工（供完成 gate 判断） */
    boolean hasAnyApproved(Long executionId);
}
