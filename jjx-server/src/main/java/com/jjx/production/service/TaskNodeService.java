package com.jjx.production.service;

import com.jjx.production.domain.dto.TaskAssignItemDTO;
import com.jjx.production.domain.entity.ProductionTaskNode;
import com.jjx.production.domain.vo.MyTaskNodeVO;
import com.jjx.production.domain.vo.TaskCandidateVO;
import com.jjx.production.domain.vo.TaskNodeVO;

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

    /** 在父节点下创建子任务节点（一次可多人）；合计不得超过父节点可分配数量 */
    List<ProductionTaskNode> assignChildren(Long parentNodeId, List<TaskAssignItemDTO> items);

    /** 节点剩余数量：effective - childOccupied - selfReported（selfReported 从 WorkReport 动态汇总，P1 恒 0） */
    BigDecimal remaining(Long taskNodeId);

    /** 可继续分配数量（= remaining，下限 0） */
    BigDecimal availableToAssign(Long taskNodeId);

    /** 查询单个任务节点（不存在抛异常） */
    ProductionTaskNode getNode(Long taskNodeId);

    /** 收回直接子节点部分剩余任务：child.recalledQuantity += quantity；父节点可分配数量自动恢复 */
    ProductionTaskNode recall(Long childNodeId, BigDecimal quantity);

    /** 退回部分剩余任务给父节点：node.recalledQuantity += quantity；父节点容量自动恢复 */
    ProductionTaskNode returnNode(Long nodeId, BigDecimal quantity);

    /** 我的任务节点：当前用户持有的 TaskNode + 执行上下文（数量动态汇总） */
    List<MyTaskNodeVO> myTaskNodes();

    /** 分配任务候选人员：当前用户部门子树内成员（最小可靠候选范围） */
    List<TaskCandidateVO> candidates();
}
