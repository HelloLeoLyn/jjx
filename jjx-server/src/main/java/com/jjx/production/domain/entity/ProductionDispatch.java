package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工序派工单
 * 对应表：production_dispatch
 * 挂 production_operation_execution（execution_id），与工序执行联动
 */
@Data
@TableName("production_dispatch")
public class ProductionDispatch {

    @TableId(type = IdType.AUTO)
    private Long dispatchId;

    /** 生产订单ID */
    private Long orderId;
    /** 工单编号(冗余) */
    private String orderNo;
    /** 工序执行记录ID */
    private Long executionId;
    /** 工序名称(冗余) */
    private String processName;
    /** 工序顺序(冗余) */
    private Integer processOrder;

    /** 责任班组(部门ID) */
    private Long teamId;
    /** 责任班组名称 */
    private String teamName;
    /** 设备ID(空=不限) */
    private Long equipmentId;
    /** 设备名称 */
    private String equipmentName;
    /** 执行人(JSON数组 [{userId,userName}]) */
    private String operators;

    /** 派工主管(用户ID) */
    private Long assignedBy;
    /** 派工主管姓名 */
    private String assignedByName;
    /** 最近指派时间 */
    private LocalDateTime assignTime;
    /** 状态：0待派工 1已派工 2执行中 3已完成 4已退回 */
    private Integer status;
    /** 退回原因 */
    private String rejectReason;
    /** 改派次数 */
    private Integer reDispatchCount;
    /** 备注 */
    private String remark;

    @TableLogic
    private String delFlag;
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
