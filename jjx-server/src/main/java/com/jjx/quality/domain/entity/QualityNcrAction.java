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

    /** 允许动作（唯一出处下发，不落库）—— dev-20260923-039：处置记录行的按钮按它渲染 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.util.List<String> allowedActions;

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
    /** 返工报工完成后生成的 FQC 复检批 */
    private Long reinspectionLotId;
    private Integer customerConfirmed;
    private LocalDateTime customerConfirmTime;
    private String approvedBy;
    private LocalDateTime approvedTime;
    private String resultRemark;
    private String operatorName;

    /** dev-20260924-004：本次处置的主缺陷检验项目（件级口径） */
    private String mainCheckItem;

    /** dev-20260924-004：本次处置的主缺陷分级 CR/MA/MI */
    private String mainDefectLevel;

    /** dev-20260924-004：本次处置件数（与 quality_ncr_piece 对账） */
    private Integer pieceCount;

    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
