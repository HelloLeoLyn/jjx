package com.jjx.production.service;

import com.jjx.production.domain.dto.TaskAssignItemDTO;
import com.jjx.production.domain.entity.ProductionTaskNode;
import com.jjx.production.domain.vo.MyTaskNodeVO;
import com.jjx.production.domain.vo.TaskCandidateVO;
import com.jjx.production.domain.vo.TaskNodeVO;
import com.jjx.production.domain.vo.TaskTreeEventVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 生产任务树服务（P1 Task Tree Core）
 * 统一 TaskNode 模型：每道工序一棵任务树，根节点代表全部任务数量，节点语义一致。
 */
public interface TaskNodeService {

    /** 幂等建立根节点：已存在则直接返回；root.taskQuantity = execution 计划数量 */
    ProductionTaskNode ensureRoot(Long executionId);

    /** 获取工序任务树（第一次需要时自动 ensureRoot；不含 CANCELLED 节点） */
    TaskNodeVO getTaskTree(Long executionId);

    /**
     * 任务树懒加载：当前视角第一层任务节点（parentNodeId 为空）或指定节点的直接子节点。
     * 纯浏览不建根（不 ensureRoot）；普通用户只能访问本人持有节点及其下级子树。
     */
    List<TaskNodeVO> listChildren(Long executionId, Long parentNodeId);

    /** 任务节点详情（单节点数量投影；视图范围校验；不加载子树） */
    TaskNodeVO getNodeDetail(Long taskNodeId);

    /** 在父节点下创建子任务节点（一次可多人）；合计不得超过父节点可分配数量 */
    List<ProductionTaskNode> assignChildren(Long parentNodeId, List<TaskAssignItemDTO> items);

    /** 节点剩余数量：effective - childOccupied - selfReported（selfReported 从 WorkReport 动态汇总，P1 恒 0） */
    BigDecimal remaining(Long taskNodeId);

    /** 可继续分配数量（= remaining，下限 0） */
    BigDecimal availableToAssign(Long taskNodeId);

    /** 查询单个任务节点（不存在抛异常） */
    ProductionTaskNode getNode(Long taskNodeId);

    /** 行锁查询任务节点（SELECT ... FOR UPDATE）：报工 submit 等数量校验前使用，与 assign/recall/return 锁顺序一致（TT-FINAL-04） */
    ProductionTaskNode lockNode(Long taskNodeId);

    /** 收回直接子节点部分剩余任务：child.recalledQuantity += quantity；父节点可分配数量自动恢复 */
    ProductionTaskNode recall(Long childNodeId, BigDecimal quantity);

    /** 退回部分剩余任务给父节点：node.recalledQuantity += quantity；父节点容量自动恢复 */
    ProductionTaskNode returnNode(Long nodeId, BigDecimal quantity);

    /** 我的任务节点：当前用户持有的 TaskNode + 执行上下文（数量动态汇总） */
    List<MyTaskNodeVO> myTaskNodes();

    /** 该 Execution 的有效 Task Tree 是否已闭环：所有节点（含系统 Root）selfRemaining=0（TT-FINAL-03） */
    boolean isExecutionTreeClosed(Long executionId);

    /** 该 Execution 的完整任务操作流水（按时间升序）：分配/收回/退回（sys_oper_log）+ 报工/撤销报工（work_report）（TT-FINAL-06） */
    List<TaskTreeEventVO> executionEvents(Long executionId);

    /** 分配任务候选人员：当前用户部门子树内成员（最小可靠候选范围） */
    List<TaskCandidateVO> candidates();
}
