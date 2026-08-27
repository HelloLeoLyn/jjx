package com.jjx.production.service;

import com.jjx.production.domain.dto.WorkReportCancelDTO;
import com.jjx.production.domain.dto.WorkReportReviewDTO;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.domain.vo.WorkReportVO;

/**
 * 生产报工动作服务（P3 WorkReport + Approval）
 * 正式写动作：SUBMIT / APPROVE / REJECT / CANCEL；禁止普通 UPDATE/DELETE。
 */
public interface WorkReportActionService {

    /**
     * SUBMIT：创建一次不可覆盖的报工事实（PENDING）。
     * 前置 gate：Task 当前执行人本人、Task 非 CANCELLED、Execution EXECUTING、
     * reportQuantity <= Task.remainingQuantity（唯一额度边界）、defective>0 时缺陷原因必填。
     * 成功后重算 execution projection（只认 APPROVED）。
     */
    WorkReportVO submit(WorkReportSubmitDTO dto, String operatorName, Long operatorId);

    /**
     * APPROVE：PENDING → APPROVED（条件更新防并发；审批人 = Parent Task assignee 或生产管理）。
     * pending 占用转 completed，remaining 不变；一笔只审批一次。
     */
    WorkReportVO approve(Long reportId, WorkReportReviewDTO dto, String operatorName, Long operatorId);

    /**
     * REJECT：PENDING → REJECTED（条件更新防并发；审批人规则同 APPROVE；驳回原因必填）。
     * 释放 pending 占用，remaining 恢复；已关联 PASS/FAIL 质检的报工禁止驳回。
     */
    WorkReportVO reject(Long reportId, WorkReportReviewDTO dto, String operatorName, Long operatorId);

    /**
     * CANCEL：PENDING → CANCELLED（条件更新防并发；提交人本人或超管）。
     * 释放 pending 占用，remaining 恢复；已关联 PASS/FAIL 质检的报工禁止撤销。
     */
    WorkReportVO cancel(Long reportId, WorkReportCancelDTO dto, String operatorName, Long operatorId);
}
