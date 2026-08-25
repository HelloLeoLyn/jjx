package com.jjx.production.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.production.domain.dto.TaskAssignDTO;
import com.jjx.production.domain.dto.TaskCompleteDTO;
import com.jjx.production.domain.dto.TaskRecallDTO;
import com.jjx.production.domain.dto.TaskReturnDTO;
import com.jjx.production.domain.dto.TaskTreeQueryDTO;
import com.jjx.production.domain.vo.TaskCandidateVO;
import com.jjx.production.domain.vo.TaskCompletionDetailVO;
import com.jjx.production.domain.vo.TaskEventVO;
import com.jjx.production.domain.vo.TaskTreeRowVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 生产任务服务（统一任务责任树；P1 Foundation + P2 Task Flow）
 */
public interface ProductionTaskService {

    // ==================== P1 Foundation ====================

    /**
     * 创建 First ProductionTask（工序产生时同步创建；同一事务）
     * execution_id = executionId / parent_task_id = NULL / assignee_id = NULL
     * task_quantity = inputQuantity / status = PENDING / version = 0
     * 唯一约束兜底：已存在时幂等返回既有 First Task
     */
    Long createFirstTask(Long executionId, BigDecimal inputQuantity);

    /**
     * 第一层分页查询（parent_task_id IS NULL）
     * 生产全局角色 → 全部；普通用户 → assignee_id = 当前用户
     */
    Page<TaskTreeRowVO> pageAccessibleTasks(TaskTreeQueryDTO queryDTO);

    /**
     * 任务详情（真实 production_task 单行 + 投影）
     */
    TaskTreeRowVO getDetail(Long taskId);

    /**
     * 直接子任务（真懒加载：只查询 parent_task_id = taskId；活动树排除 CANCELLED）
     */
    List<TaskTreeRowVO> listChildren(Long taskId);

    /**
     * 完成明细（P4）：当前 Task 有效子树内全部 APPROVED WorkReport
     * 约束：SUM(reportQuantity) == TaskTreeRowVO.completedQuantity（对账 invariant）
     */
    List<TaskCompletionDetailVO> listCompletionDetails(Long taskId);

    /**
     * 任务流转流水（P6）：task_id = 当前任务 OR related_task_id = 当前任务，按时间倒序
     */
    List<TaskEventVO> listEvents(Long taskId);

    /**
     * 我的任务（P6 报工入口）：assignee_id = 当前登录人；可空 executionId 收窄；排除 CANCELLED/COMPLETED
     */
    List<TaskTreeRowVO> listMyTasks(Long executionId);

    /**
     * 当前 Task 自身剩余数量（P3 WorkReport 接入后真实投影）：
     * remaining = taskQuantity - assignedQuantity - pendingQuantity - completedQuantity（下限 0）
     * 唯一额度边界：报工/分配/收回/退回共用。
     */
    BigDecimal remainingQuantity(Long taskId);

    // ==================== P2 Task Flow ====================

    /**
     * 可分配候选人员（P2 最小；由 ProductionTaskAssigneeResolver 产出）
     */
    List<TaskCandidateVO> listCandidates(Long taskId);

    /**
     * 任务分配（统一入口）：
     * - 每个层级行为同构：一次事务校验并创建全部 Child（可 1 人或多人，逐人拆量，合计不超过剩余）
     * - 身份门：assignee_id IS NULL → 仅生产管理者可发起；已分配 → 仅当前执行人可发起
     * - 无下属 → 无分配权限；First Task 不写 assignee_id，实际负责人全部由 Child 表达
     * 并发：父行 FOR UPDATE 串行化 + version 条件更新；TaskEvent 与 Task 修改同事务。
     */
    void assign(Long taskId, TaskAssignDTO dto);

    /**
     * 收回：父执行人从直接 Child 拿回未完成/未再下发的量
     * 校验 child.parent_task_id == parentTaskId；锁顺序 parent → child。
     */
    void recall(Long parentTaskId, TaskRecallDTO dto);

    /**
     * 退回：当前执行人把自己未完成/未再下发的剩余退给父任务
     * 第一层（parent_task_id IS NULL）禁止。
     */
    void returnTask(Long taskId, TaskReturnDTO dto);

    /**
     * 人工确认完成（P5 自底向上确认链）：
     * 前置：有效完成量 == taskQuantity && subtreePending == 0 && remaining == 0
     *      && assigned 未完成责任 == 0 && 所有有效直接 Child 均已 COMPLETED
     * 身份：当前执行人（第一层=生产管理者）；完成后禁止 assign/recall/return/report。
     * 写入 TaskEvent(action=COMPLETE)。
     */
    void complete(Long taskId, TaskCompleteDTO dto);
}
