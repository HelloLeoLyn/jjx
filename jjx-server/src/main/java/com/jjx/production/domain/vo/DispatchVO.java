package com.jjx.production.domain.vo;

import com.jjx.production.domain.entity.ProductionDispatch;
import com.jjx.production.enums.DispatchStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派工单出参 VO
 */
@Data
public class DispatchVO {

    private Long dispatchId;
    private Long orderId;
    private String orderNo;
    private Long executionId;
    private String processName;
    private Integer processOrder;

    /** 工序大类（ASSEMBLY冲型组装/PRINT印刷，工作台视图） */
    private String majorCategory;

    /** 工序执行状态（production_operation_execution.execution_status，工作台视图） */
    private Integer executionStatus;

    /** 工单计划数量（工作台视图） */
    private java.math.BigDecimal plannedQuantity;

    /** 派工状态（工作台视图：无派工单=0 待派工；有派工单=派工单状态 0-4） */
    private Integer dispatchStatus;

    private Long teamId;
    private String teamName;
    private Long equipmentId;
    private String equipmentName;
    /**
     * 执行人 JSON 原样返回，前端解析（兼容保留）
     * Legacy responsibility-chain representation. P1 Node is the new source of truth.
     * Read fallback only until migration cutover. Do not use in new business rules.
     */
    private String operators;

    private Long assignedBy;
    private String assignedByName;
    private LocalDateTime assignTime;
    private Integer status;
    private String statusLabel;
    private String rejectReason;
    private Integer reDispatchCount;
    private String remark;

    // ============ P1-B Node-first current assignee projection ============
    /** 当前责任节点ID（Node 存在=ACTIVE 节点；legacy fallback=null） */
    private Long currentNodeId;
    /** 当前责任人ID（Node-first；legacy fallback=末位 operator） */
    private Long currentAssigneeId;
    /** 当前责任人姓名 */
    private String currentAssigneeName;
    /** 当前责任人所属组织ID（Node org 快照） */
    private Long currentOrgId;
    /** 当前责任人所属组织名称 */
    private String currentOrgName;
    /** 数据来源：NODE / LEGACY（内部调试；前端可不展示） */
    private String assigneeSource;

    // ============ P1-D：当前用户对该派工单的动作能力 ============
    /** 当前用户可执行动作列表：ASSIGN/DELEGATE/REASSIGN/RETURN（前端按钮显隐用；真正权限由后端 ActionService 校验） */
    private java.util.List<String> allowedActions;

    private String createBy;
    private LocalDateTime createTime;

    public static DispatchVO fromEntity(ProductionDispatch e) {
        if (e == null) return null;
        DispatchVO vo = new DispatchVO();
        vo.setDispatchId(e.getDispatchId());
        vo.setOrderId(e.getOrderId());
        vo.setOrderNo(e.getOrderNo());
        vo.setExecutionId(e.getExecutionId());
        vo.setProcessName(e.getProcessName());
        vo.setProcessOrder(e.getProcessOrder());
        vo.setTeamId(e.getTeamId());
        vo.setTeamName(e.getTeamName());
        vo.setEquipmentId(e.getEquipmentId());
        vo.setEquipmentName(e.getEquipmentName());
        vo.setOperators(e.getOperators());
        vo.setAssignedBy(e.getAssignedBy());
        vo.setAssignedByName(e.getAssignedByName());
        vo.setAssignTime(e.getAssignTime());
        vo.setStatus(e.getStatus());
        vo.setStatusLabel(DispatchStatusEnum.labelOf(e.getStatus()));
        vo.setRejectReason(e.getRejectReason());
        vo.setReDispatchCount(e.getReDispatchCount());
        vo.setRemark(e.getRemark());
        vo.setCreateBy(e.getCreateBy());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
