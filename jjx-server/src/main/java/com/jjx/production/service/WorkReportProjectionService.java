package com.jjx.production.service;

import com.jjx.production.domain.entity.ProductionOperationExecution;

import java.math.BigDecimal;

/**
 * WorkReport Projection 服务（P2-C）
 * Execution 数量/工时字段 = WorkReport Projection（SUM SUBMITTED），用户不再直接维护。
 */
public interface WorkReportProjectionService {

    /**
     * 事务内重算 execution projection：
     * qualified = SUM(qualified) / defective = SUM(defective)
     * output = SUM(qualified+defective) / labor = SUM(labor) / machine = SUM(machine)
     */
    void recalculate(Long executionId);

    /**
     * Projection 一致性诊断（P2 Final Gate 工具，非业务 API）：
     * MATCH / MISMATCH / NO_REPORT_LEGACY / EMPTY
     */
    String compareProjection(Long executionId);

    /** 已提交报工数量聚合（供诊断/展示） */
    BigDecimal[] sumSubmitted(Long executionId);

    /** 最近一次已提交报工（供完成 gate 判断） */
    boolean hasAnySubmitted(Long executionId);
}
