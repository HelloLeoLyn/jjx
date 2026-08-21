package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产任务树节点（统一 TaskNode 模型，替代旧 DispatchNode + Assignment）
 * 对应表：production_task_node
 * 关系：ProductionOperationExecution 1:N ProductionTaskNode（每道工序一棵任务树）
 * <p>
 * 核心语义：
 * - 根节点（parentNodeId=null）代表该工序全部任务数量（taskQuantity = execution 计划数量）
 * - 所有节点语义完全一致：持有人可自己执行并报工，也可将部分剩余任务分配给下级（创建子节点）
 * - 数量公式：effective = taskQuantity - recalledQuantity
 *   childOccupied = Σ 直接子节点 effective（已取消节点 effective=0，自然不占用）
 *   availableToAssign = effective - childOccupied - selfReported
 *   （selfReported = 本节点持有人的有效报工量，后续从 WorkReport 动态汇总）
 * - 本表不落完成量：TaskNode 完成量禁止持久化回写
 * - 无持久化 status：状态为动态投影（见 TaskNodeServiceImpl），避免第二事实源
 */
@Data
@TableName("production_task_node")
public class ProductionTaskNode {

    @TableId(type = IdType.AUTO)
    private Long taskNodeId;

    /** 工序执行记录ID（任务树归属） */
    private Long executionId;

    /** 父节点ID（NULL=根节点） */
    private Long parentNodeId;

    /** 节点持有人（执行人）用户ID */
    private Long assigneeId;

    /** 节点持有人姓名快照 */
    private String assigneeName;

    /** 节点任务数量 */
    private BigDecimal taskQuantity;

    /** 已收回数量（已分配子节点但收回，P2 支持） */
    private BigDecimal recalledQuantity;

    /** 备注 */
    private String remark;

    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
