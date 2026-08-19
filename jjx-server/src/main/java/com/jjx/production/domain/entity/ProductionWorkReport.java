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
 * 生产报工（一次不可覆盖的生产数量/工时事实）
 * 对应表：production_work_report
 * 关系：ProductionOperationExecution 1:N ProductionWorkReport；ProductionDispatchNode 1:N ProductionWorkReport
 * <p>
 * 领域规则（P2-C 起强制）：
 * - 已提交报工的生产事实字段（executionId/dispatchNodeId/reporter/数量/工时/设备/时间区间）禁止修改
 * - 更正 = 原报工 CANCELLED + 新增正确报工；CANCELLED 原事实字段保留，禁止物理删除
 * - 未来正式写动作仅 SUBMIT / CANCEL（本实体技术上可 update，但领域层不暴露通用修改 API）
 */
@Data
@TableName("production_work_report")
public class ProductionWorkReport {

    @TableId(type = IdType.AUTO)
    private Long reportId;

    /** 生产订单ID（冗余引用，便于追溯查询） */
    private Long orderId;

    /** 工单编号（冗余） */
    private String orderNo;

    /** 工序执行记录ID（生产事实主体） */
    private Long executionId;

    /** 派工单ID（冗余；ActionService 须校验 = node.dispatchId） */
    private Long dispatchId;

    /** 报工时责任节点ID（责任锚点） */
    private Long dispatchNodeId;

    /** 报工提交人ID（P2-C 默认须=ACTIVE assignee，库不强制） */
    private Long reporterId;

    /** 报工提交人姓名快照 */
    private String reporterName;

    /** 本次实际使用设备ID（可空=人工工序无设备） */
    private Long equipmentId;

    /** 本次实际使用设备名称（快照） */
    private String equipmentName;

    /** 本次合格数量 */
    private BigDecimal qualifiedQuantity;

    /** 本次不良数量 */
    private BigDecimal defectiveQuantity;

    /** 本次人工工时 */
    private BigDecimal laborHours;

    /** 本次机器工时 */
    private BigDecimal machineHours;

    /** 本次生产开始时间（可空） */
    private LocalDateTime workStartTime;

    /** 本次生产结束时间（可空；P2-C 校验 end>=start） */
    private LocalDateTime workEndTime;

    /** 报工正式提交时间 */
    private LocalDateTime reportTime;

    /** 不良原因（P2 V1 单字段，P3 再做缺陷明细） */
    private String defectReason;

    /** 备注（提交后不可变） */
    private String remark;

    /** 状态：SUBMITTED/CANCELLED */
    private String reportStatus;

    /** 撤销人ID */
    private Long cancelledBy;

    /** 撤销人姓名 */
    private String cancelledByName;

    /** 撤销时间 */
    private LocalDateTime cancelledAt;

    /** 撤销原因（P2-C 必填） */
    private String cancelReason;

    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
