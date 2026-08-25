package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.WorkReportQueryDTO;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.WorkReportVO;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.WorkReportReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 生产报工读取服务实现（P3：只读）
 * 时间排序：reportTime DESC → createTime DESC → reportId DESC（Execution 报工历史 Drawer 默认）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkReportReadServiceImpl implements WorkReportReadService {

    private final ProductionWorkReportMapper workReportMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public WorkReportVO getById(Long reportId) {
        ProductionWorkReport e = workReportMapper.selectById(reportId);
        if (e == null) throw new BusinessException("报工记录不存在");
        return toVO(e);
    }

    @Override
    public List<WorkReportVO> listByExecutionId(Long executionId) {
        return workReportMapper.selectList(Wrappers.<ProductionWorkReport>lambdaQuery()
                        .eq(ProductionWorkReport::getExecutionId, executionId)
                        .orderByDesc(ProductionWorkReport::getReportTime)
                        .orderByDesc(ProductionWorkReport::getCreateTime)
                        .orderByDesc(ProductionWorkReport::getReportId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<WorkReportVO> listApprovedByExecutionId(Long executionId) {
        return workReportMapper.selectList(Wrappers.<ProductionWorkReport>lambdaQuery()
                        .eq(ProductionWorkReport::getExecutionId, executionId)
                        .eq(ProductionWorkReport::getReportStatus, WorkReportStatusEnum.APPROVED.getCode())
                        .orderByDesc(ProductionWorkReport::getReportTime)
                        .orderByDesc(ProductionWorkReport::getReportId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public PageResult<WorkReportVO> pageMine(WorkReportQueryDTO queryDTO, Long currentUserId) {
        WorkReportQueryDTO q = queryDTO == null ? new WorkReportQueryDTO() : queryDTO;
        LambdaQueryWrapper<ProductionWorkReport> wrapper = Wrappers.<ProductionWorkReport>lambdaQuery()
                .eq(ProductionWorkReport::getReporterId, currentUserId)
                .orderByDesc(ProductionWorkReport::getReportTime)
                .orderByDesc(ProductionWorkReport::getReportId);
        applyCommonFilters(wrapper, q);
        Page<ProductionWorkReport> page = workReportMapper.selectPage(
                new Page<>(q.getPageNum(), q.getPageSize()), wrapper);
        return toPage(page);
    }

    @Override
    public PageResult<WorkReportVO> pagePendingApproval(WorkReportQueryDTO queryDTO, Long currentUserId,
                                                        boolean globalScope) {
        WorkReportQueryDTO q = queryDTO == null ? new WorkReportQueryDTO() : queryDTO;
        LambdaQueryWrapper<ProductionWorkReport> wrapper = Wrappers.<ProductionWorkReport>lambdaQuery()
                .eq(ProductionWorkReport::getReportStatus, WorkReportStatusEnum.PENDING.getCode())
                .orderByDesc(ProductionWorkReport::getReportTime)
                .orderByDesc(ProductionWorkReport::getReportId);
        if (queryDTO != null && queryDTO.getTaskId() != null) {
            wrapper.eq(ProductionWorkReport::getTaskId, queryDTO.getTaskId());
        }
        if (queryDTO != null && queryDTO.getExecutionId() != null) {
            wrapper.eq(ProductionWorkReport::getExecutionId, queryDTO.getExecutionId());
        }
        if (!globalScope) {
            // 待我审批 = 我负责 Task 的直接子任务报工（parent assignee = 当前用户）
            List<Long> taskIds = mySubordinateTaskIds(currentUserId);
            if (taskIds.isEmpty()) {
                Page<ProductionWorkReport> empty = new Page<>(q.getPageNum(), q.getPageSize());
                return toPage(empty);
            }
            wrapper.in(ProductionWorkReport::getTaskId, taskIds);
        }
        Page<ProductionWorkReport> page = workReportMapper.selectPage(
                new Page<>(q.getPageNum(), q.getPageSize()), wrapper);
        return toPage(page);
    }

    // ==================== helpers ====================

    private void applyCommonFilters(LambdaQueryWrapper<ProductionWorkReport> wrapper, WorkReportQueryDTO q) {
        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            wrapper.eq(ProductionWorkReport::getReportStatus, q.getStatus());
        }
        if (q.getTaskId() != null) {
            wrapper.eq(ProductionWorkReport::getTaskId, q.getTaskId());
        }
        if (q.getExecutionId() != null) {
            wrapper.eq(ProductionWorkReport::getExecutionId, q.getExecutionId());
        }
    }

    /** 当前用户作为 Parent assignee 的直接子任务 ID（活动树排除 CANCELLED） */
    private List<Long> mySubordinateTaskIds(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        try {
            return jdbcTemplate.query(
                    "SELECT t.task_id FROM production_task t "
                            + "JOIN production_task p ON p.task_id = t.parent_task_id "
                            + "WHERE p.assignee_id = ? AND t.status != 'CANCELLED'",
                    (rs, i) -> rs.getLong("task_id"), userId);
        } catch (Exception e) {
            log.warn("查询待审批子任务失败 userId={}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private PageResult<WorkReportVO> toPage(Page<ProductionWorkReport> page) {
        List<WorkReportVO> voList = page.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return PageResult.of(page, voList);
    }

    private WorkReportVO toVO(ProductionWorkReport e) {
        WorkReportVO vo = new WorkReportVO();
        vo.setReportId(e.getReportId());
        vo.setOrderId(e.getOrderId());
        vo.setOrderNo(e.getOrderNo());
        vo.setExecutionId(e.getExecutionId());
        vo.setTaskId(e.getTaskId());
        vo.setReporterId(e.getReporterId());
        vo.setReporterName(e.getReporterName());
        vo.setEquipmentId(e.getEquipmentId());
        vo.setEquipmentName(e.getEquipmentName());
        vo.setQualifiedQuantity(e.getQualifiedQuantity());
        vo.setDefectiveQuantity(e.getDefectiveQuantity());
        vo.setLaborHours(e.getLaborHours());
        vo.setMachineHours(e.getMachineHours());
        vo.setWorkStartTime(e.getWorkStartTime());
        vo.setWorkEndTime(e.getWorkEndTime());
        vo.setReportTime(e.getReportTime());
        vo.setDefectReason(e.getDefectReason());
        vo.setRemark(e.getRemark());
        vo.setReportStatus(e.getReportStatus());
        vo.setReportStatusLabel(WorkReportStatusEnum.labelOf(e.getReportStatus()));
        vo.setReviewerId(e.getReviewerId());
        vo.setReviewerName(e.getReviewerName());
        vo.setReviewTime(e.getReviewTime());
        vo.setReviewRemark(e.getReviewRemark());
        vo.setCancelledByName(e.getCancelledByName());
        vo.setCancelledAt(e.getCancelledAt());
        vo.setCancelReason(e.getCancelReason());
        return vo;
    }
}
