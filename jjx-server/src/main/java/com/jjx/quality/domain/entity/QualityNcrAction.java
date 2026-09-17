package com.jjx.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不良处置单 —— dev-20260917-003
 * 口径：处置方式只有 返工(REWORK) / 让步接收(CONCESSION) / 报废(SCRAP)；
 *       让步接收需客户确认；审批人走权限点（009/011）。
 */
@Data
@TableName("quality_ncr_action")
public class QualityNcrAction {

    @TableId(type = IdType.AUTO)
    private Long actionId;

    private Long ncrId;
    /** REWORK/CONCESSION/SCRAP */
    private String actionType;
    private BigDecimal quantity;
    /** PENDING/PROCESSING/DONE */
    private String status;
    /** 返工生成的工序执行 */
    private Long reworkExecutionId;
    private Integer customerConfirmed;
    private LocalDateTime customerConfirmTime;
    private String approvedBy;
    private LocalDateTime approvedTime;
    private String resultRemark;
    private String operatorName;

    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
