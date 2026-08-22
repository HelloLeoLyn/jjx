package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.WorkReportCancelDTO;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.WorkReportVO;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.WorkReportActionService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.WorkReportReadService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产报工动作服务实现（P2-C）
 * <p>
 * SUBMIT：校验（execution/权限/数量/工时/时间/设备）
 * → insert WorkReport（SUBMITTED）→ 重算 execution projection → commit
 * CANCEL：条件 UPDATE SUBMITTED→CANCELLED（affectedRows 检查，已撤销幂等）
 * → 重算 projection → commit
 * <p>
 * 权限：production:work-report:add（SUBMIT）/ production:work-report:cancel（CANCEL）
 * 报工提交人 = 操作人（operatorId 由上层会话注入）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkReportActionServiceImpl implements WorkReportActionService {

    private final ProductionWorkReportMapper workReportMapper;
    private final ProductionOperationExecutionMapper executionMapper;
    private final WorkReportProjectionService projectionService;
    private final WorkReportReadService readService;
    private final JdbcTemplate jdbcTemplate;

    /** P3-C：WorkReport 撤销质检联动（PASS/FAIL 禁撤；PENDING 联动逻辑删除） */
    private final com.jjx.production.service.QualityInspectionService qualityInspectionService;

    // ==================== SUBMIT ====================

    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkReportVO submit(WorkReportSubmitDTO dto, String operatorName, Long operatorId) {
        // TODO
        return null;
    }

    // ==================== CANCEL ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkReportVO cancel(Long reportId, WorkReportCancelDTO dto, String operatorName, Long operatorId) {
        if (dto == null || dto.getCancelReason() == null || dto.getCancelReason().isBlank()) {
            throw new BusinessException("撤销原因必填");
        }
        ProductionWorkReport r = workReportMapper.selectById(reportId);
        if (r == null) throw new BusinessException("报工记录不存在");

        // 已完成 execution 禁止撤销（P2 V1：完成后的数量可能已影响 order/库存）
        ProductionOperationExecution exec = executionMapper.selectById(r.getExecutionId());
        if (exec != null && ExecutionStatusEnum.COMPLETED.getCode().equals(exec.getExecutionStatus())) {
            throw new BusinessException("工序已完成，不允许撤销报工");
        }

        // P3-C：质检关联 gate——已关联 PASS/FAIL 质检的报工禁止撤销；仅 PENDING 质检时联动处理
        java.util.List<com.jjx.production.domain.vo.QualityInspectionVO> related =
                qualityInspectionService.listByWorkReportId(reportId);
        boolean hasFinalized = related.stream().anyMatch(q ->
                com.jjx.production.enums.QualityInspectionResultEnum.PASS.getCode().equals(q.getResult())
                        || com.jjx.production.enums.QualityInspectionResultEnum.FAIL.getCode().equals(q.getResult()));
        if (hasFinalized) {
            throw new BusinessException("该报工已关联质检判定结果（PASS/FAIL），不允许撤销；如需更正请走质检复检");
        }
        // 仅 PENDING 质检：允许撤销报工，但同步逻辑删除这些 PENDING 质检（历史可追踪，不留指向已撤销事实的有效质检单）
        for (com.jjx.production.domain.vo.QualityInspectionVO q : related) {
            qualityInspectionService.delete(q.getInspectionId());
            log.info("报工撤销联动：逻辑删除 PENDING 质检 {}", q.getInspectionId());
        }

        // 权限：work-report:cancel 权限点 + 业务关系（本人或超管）
        if (!SecurityUtils.hasPermission("*:*:*") && !SecurityUtils.hasPermission("production:work-report:cancel")) {
            throw new BusinessException("无撤销报工权限");
        }
        // 业务关系：reporter 本人 或 超管（权限点 + 业务关系共同判断）
        if (!SecurityUtils.hasPermission("*:*:*") && (operatorId == null || !operatorId.equals(r.getReporterId()))) {
            throw new BusinessException("只有报工提交人本人或管理员可以撤销");
        }

        // 条件更新：SUBMITTED → CANCELLED（并发保护）
        ProductionWorkReport upd = new ProductionWorkReport();
        upd.setReportId(reportId);
        upd.setReportStatus(WorkReportStatusEnum.CANCELLED.getCode());
        upd.setCancelledBy(operatorId);
        upd.setCancelledByName(operatorName);
        upd.setCancelledAt(LocalDateTime.now());
        upd.setCancelReason(dto.getCancelReason());
        upd.setUpdateBy(operatorName);
        int rows = workReportMapper.update(upd, Wrappers.<ProductionWorkReport>lambdaUpdate()
                .eq(ProductionWorkReport::getReportId, reportId)
                .eq(ProductionWorkReport::getReportStatus, WorkReportStatusEnum.SUBMITTED.getCode()));
        if (rows == 0) {
            // 已 CANCELLED → 幂等返回当前状态
            ProductionWorkReport cur = workReportMapper.selectById(reportId);
            if (cur != null && WorkReportStatusEnum.CANCELLED.getCode().equals(cur.getReportStatus())) {
                log.info("撤销幂等：报工 {} 已撤销", reportId);
                return readService.getById(reportId);
            }
            throw new BusinessException("报工状态已变化，请刷新后重试");
        }

        // 重算 projection（同一事务）
        projectionService.recalculate(r.getExecutionId());
        log.info("撤销报工 reportId={}, executionId={}, 原因={}", reportId, r.getExecutionId(), dto.getCancelReason());
        return readService.getById(reportId);
    }

    // ==================== helpers ====================

    private String equipmentNameOf(Long equipmentId) {
        try {
            var names = jdbcTemplate.query(
                    "SELECT equipment_name FROM production_equipment WHERE equipment_id = ?",
                    (rs, i) -> rs.getString("equipment_name"), equipmentId);
            return names.isEmpty() ? null : names.get(0);
        } catch (Exception e) {
            log.warn("查询设备失败 equipmentId={}: {}", equipmentId, e.getMessage());
            return null;
        }
    }

    private String orderNoOf(Long orderId) {
        try {
            var nos = jdbcTemplate.query(
                    "SELECT order_no FROM production_order WHERE order_id = ?",
                    (rs, i) -> rs.getString("order_no"), orderId);
            return nos.isEmpty() ? null : nos.get(0);
        } catch (Exception e) {
            log.warn("查询工单编号失败 orderId={}: {}", orderId, e.getMessage());
            return null;
        }
    }

    private String strip(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
