package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.WorkReportProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * WorkReport Projection 服务实现（P3）
 * 使用数据库 SUM（利用 idx_execution_status），只统计 APPROVED（有效完成事实）。
 * PENDING/REJECTED/CANCELLED 均不计入 execution output。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkReportProjectionServiceImpl implements WorkReportProjectionService {

    private static final String SUM_SQL =
            "SELECT COALESCE(SUM(qualified_quantity),0), COALESCE(SUM(defective_quantity),0),"
                    + " COALESCE(SUM(qualified_quantity + defective_quantity),0),"
                    + " COALESCE(SUM(labor_hours),0), COALESCE(SUM(machine_hours),0)"
                    + " FROM production_work_report WHERE execution_id=? AND report_status='APPROVED'";

    private final JdbcTemplate jdbcTemplate;
    private final ProductionOperationExecutionMapper executionMapper;
    private final ProductionWorkReportMapper workReportMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalculate(Long executionId) {
        BigDecimal[] sums = sumApproved(executionId);
        ProductionOperationExecution exec = executionMapper.selectById(executionId);
        if (exec == null) throw new BusinessException("工序执行记录不存在: " + executionId);
        ProductionOperationExecution upd = new ProductionOperationExecution();
        upd.setExecutionId(executionId);
        upd.setOutputQuantity(sums[2]);       // qualified + defective（仅 APPROVED）
        upd.setQualifiedQuantity(sums[0]);
        upd.setDefectiveQuantity(sums[1]);
        upd.setActualLaborHours(sums[3]);
        upd.setActualMachineHours(sums[4]);
        executionMapper.updateById(upd);
        log.info("WorkReport projection 重算 executionId={}: q={} d={} o={} lh={} mh={}",
                executionId, sums[0], sums[1], sums[2], sums[3], sums[4]);
    }

    @Override
    public BigDecimal[] sumApproved(Long executionId) {
        try {
            return jdbcTemplate.queryForObject(SUM_SQL, (rs, i) -> new BigDecimal[]{
                    rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3),
                    rs.getBigDecimal(4), rs.getBigDecimal(5)}, executionId);
        } catch (Exception e) {
            log.warn("SUM projection 查询失败 executionId={}: {}", executionId, e.getMessage());
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO};
        }
    }

    @Override
    public String compareProjection(Long executionId) {
        ProductionOperationExecution exec = executionMapper.selectById(executionId);
        if (exec == null) return "EMPTY";
        Long reportCount = workReportMapper.selectCount(Wrappers.<com.jjx.production.domain.entity.ProductionWorkReport>lambdaQuery()
                .eq(com.jjx.production.domain.entity.ProductionWorkReport::getExecutionId, executionId));
        if (reportCount == null || reportCount == 0) {
            // 无报工：execution 若有非零 legacy 数量 → NO_REPORT_LEGACY；否则 EMPTY
            boolean legacy = isNonZero(exec.getOutputQuantity()) || isNonZero(exec.getQualifiedQuantity())
                    || isNonZero(exec.getDefectiveQuantity());
            return legacy ? "NO_REPORT_LEGACY" : "EMPTY";
        }
        BigDecimal[] sums = sumApproved(executionId);
        boolean match = eq(sums[0], exec.getQualifiedQuantity())
                && eq(sums[1], exec.getDefectiveQuantity())
                && eq(sums[2], exec.getOutputQuantity());
        return match ? "MATCH" : "MISMATCH";
    }

    @Override
    public boolean hasAnyApproved(Long executionId) {
        Long cnt = workReportMapper.selectCount(Wrappers.<com.jjx.production.domain.entity.ProductionWorkReport>lambdaQuery()
                .eq(com.jjx.production.domain.entity.ProductionWorkReport::getExecutionId, executionId)
                .eq(com.jjx.production.domain.entity.ProductionWorkReport::getReportStatus,
                        WorkReportStatusEnum.APPROVED.getCode()));
        return cnt != null && cnt > 0;
    }

    private boolean isNonZero(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) != 0;
    }

    private boolean eq(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.compareTo(b) == 0;
    }
}
