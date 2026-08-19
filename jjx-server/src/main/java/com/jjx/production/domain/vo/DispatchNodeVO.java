package com.jjx.production.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派工责任链节点 VO（P1-B Node-first Read Model）
 * 仅用于读取/展示；不暴露 active_guard（DB 生成列）。
 * <p>
 * legacy fallback 兼容 DTO 时：nodeId=null，parentNodeId 按数组构造虚拟关系，
 * nodeStatus 末位=ACTIVE、前面=DELEGATED——仅用于展示兼容，不是数据库 Node。
 */
@Data
public class DispatchNodeVO {

    /** 节点ID（legacy fallback 时=null） */
    private Long nodeId;

    private Long dispatchId;

    /** 责任来源节点（第1级=null） */
    private Long parentNodeId;

    /** 责任主体类型（P1=USER） */
    private String assigneeType;

    /** 责任主体ID（用户ID） */
    private Long assigneeId;

    /** 责任主体姓名快照 */
    private String assigneeName;

    /** 当时所属组织ID快照 */
    private Long orgId;

    /** 当时所属组织名称快照 */
    private String orgName;

    /** 节点状态（ACTIVE/DELEGATED/REASSIGNED/RETURNED/COMPLETED/CANCELLED） */
    private String nodeStatus;

    /** 本次责任由谁指派 */
    private Long assignedBy;

    /** 指派人姓名快照 */
    private String assignedByName;

    /** 本次责任正式生效时间 */
    private LocalDateTime assignedAt;

    /** 本次责任周期结束时间 */
    private LocalDateTime closedAt;

    /** 备注 */
    private String remark;

    /** 数据来源：NODE / LEGACY（内部调试用；如不需要可不在前端暴露） */
    private String source;
}
