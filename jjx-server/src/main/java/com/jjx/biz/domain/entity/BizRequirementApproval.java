package com.jjx.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务需求会签记录（子表，预留）
 */
@Data
@TableName("biz_requirement_approval")
public class BizRequirementApproval {

    @TableId(type = IdType.AUTO)
    private Long approvalId;

    private Long requirementId;

    /** 会签轮次 */
    private Integer roundNo;

    /** 会签角色/部门: ENGINEERING工程/MAKING制造/PURCHASE采购仓库/QUALITY品管 */
    private String approvalRole;

    private Long approvalUserId;
    private String approvalUserName;

    /** 结果: 1通过/2驳回 */
    private Integer approveResult;

    private String comment;
    private LocalDateTime approveTime;
    private LocalDateTime createTime;
}
