package com.jjx.production.service;

import com.jjx.production.domain.dto.AssignmentCreateDTO;
import com.jjx.production.domain.dto.AssignmentReleaseDTO;
import com.jjx.production.domain.vo.AssignmentViewVO;

/**
 * 工序作业分配服务（WP-B）
 * <p>
 * ExecutionAssignment = "哪个执行人做多少"，与责任链（DispatchNode）分离。
 * 数量口径：effective = assigned - released；reported = 有效报工汇总（Projection）。
 */
public interface ExecutionAssignmentService {

    /**
     * 创建作业分配（一次多人，整批事务原子）
     *
     * @return 分配视图（含 planned/assigned/unassigned 与明细）
     */
    AssignmentViewVO create(AssignmentCreateDTO dto, String operatorName, Long operatorId);

    /**
     * 释放剩余数量（部分报工后剩余回到 unassigned pool）
     * 历史 assigned/reported 保留；released += 剩余；不整份取消。
     */
    AssignmentViewVO release(Long assignmentId, AssignmentReleaseDTO dto, String operatorName, Long operatorId);

    /**
     * 按 execution 查询分配视图（含每条 assigned/reported/released/remaining/derivedStatus）
     */
    AssignmentViewVO getByExecutionId(Long executionId);

    /**
     * 校验某 execution 是否存在有效（ACTIVE）Assignment
     */
    boolean hasActiveAssignment(Long executionId);
}
