package com.jjx.production.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.WorkReportQueryDTO;
import com.jjx.production.domain.vo.WorkReportVO;

import java.util.List;

/**
 * 生产报工读取服务（P3：只读能力）
 * 不实现 submit/approve/reject/cancel（属 WorkReportActionService）。
 */
public interface WorkReportReadService {

    /** 按报工ID查询 */
    WorkReportVO getById(Long reportId);

    /** 按 executionId 查询全部报工（默认 reportTime DESC / createTime DESC / reportId DESC，用于 Execution 报工历史 Drawer） */
    List<WorkReportVO> listByExecutionId(Long executionId);

    /** 按 executionId 查询 APPROVED 报工（执行投影汇总用） */
    List<WorkReportVO> listApprovedByExecutionId(Long executionId);

    /** 我的报工（reporterId = 当前用户；支持 status/taskId/executionId 过滤） */
    PageResult<WorkReportVO> pageMine(WorkReportQueryDTO queryDTO, Long currentUserId);

    /**
     * 待我审批（report_status=PENDING）：
     * 生产管理 → 全部；普通用户 → 其负责 Task 的直接子任务报工（parent assignee = 当前用户）。
     */
    PageResult<WorkReportVO> pagePendingApproval(WorkReportQueryDTO queryDTO, Long currentUserId,
                                                 boolean globalScope);
}
