package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务树操作流水事件 VO（TT-FINAL-06）
 * 聚合维度：executionId。
 * 数据来源：sys_oper_log（分配/收回/退回，bizType=production_task）+ production_work_report（报工/撤销报工）。
 * 不建立第二套业务事实源；数量/人员/时间均为真实业务数据。
 */
@Data
@Schema(description = "任务树操作流水事件VO")
public class TaskTreeEventVO {

    @Schema(description = "事件时间")
    private LocalDateTime time;

    @Schema(description = "动作编码：ASSIGN/RECALL/RETURN/WORK_REPORT/WORK_REPORT_CANCEL")
    private String action;

    @Schema(description = "动作名称（业务术语）")
    private String actionLabel;

    @Schema(description = "操作人")
    private String operatorName;

    @Schema(description = "涉及人员")
    private String targetName;

    @Schema(description = "涉及数量")
    private BigDecimal quantity;

    @Schema(description = "备注")
    private String remark;
}
