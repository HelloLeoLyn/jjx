package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派工责任链节点（一次生产任务责任持有实例）
 * 对应表：production_dispatch_node
 * 定位：某个责任主体在某一时间段内正式持有该生产任务责任的一次历史实例；
 * 任何一次责任正式落到某个人，都对应一个新的 Node。
 * <p>
 * 规则（P1-C 起强制）：
 * - 历史 Node 的 assignee / org snapshot / assignedAt 不能被业务动作覆盖
 * - 节点状态流转只改 nodeStatus / closedAt / update 审计字段
 * <p>
 * 唯一 ACTIVE：数据库生成列 active_guard（ACTIVE→1，其他→NULL）+ UNIQUE(dispatch_id, active_guard)，
 * Java 实体不负责写入该字段，故不映射。
 */
@Data
@TableName("production_dispatch_node")
public class ProductionDispatchNode {

    @TableId(type = IdType.AUTO)
    private Long nodeId;

    /** 所属派工单ID（production_dispatch.dispatch_id） */
    private Long dispatchId;

    /** 责任来源节点ID（第1级=NULL，表示源头主管直派） */
    private Long parentNodeId;

    /** 当前责任主体类型（P1第一版仅支持 USER） */
    private String assigneeType;

    /** 责任主体ID（用户ID） */
    private Long assigneeId;

    /** 责任主体姓名快照（改昵称不影响历史） */
    private String assigneeName;

    /** 责任主体当时所属组织ID快照 */
    private Long orgId;

    /** 责任主体当时所属组织名称快照 */
    private String orgName;

    /** 责任主体当时所属组织祖先路径快照（如"1/5/6/7"） */
    private String orgPath;

    /** 本次责任实例当前历史状态（ACTIVE/DELEGATED/REASSIGNED/RETURNED/COMPLETED/CANCELLED） */
    private String nodeStatus;

    /** 本次责任由谁指派（用户ID） */
    private Long assignedBy;

    /** 指派人姓名快照 */
    private String assignedByName;

    /** 本次责任正式生效时间 */
    private LocalDateTime assignedAt;

    /** 本次责任周期结束时间（流转走/完成/取消） */
    private LocalDateTime closedAt;

    /** 备注/退回原因/迁移说明（LEGACY_BACKFILL） */
    private String remark;

    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
