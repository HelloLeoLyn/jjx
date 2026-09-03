package com.jjx.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业务需求单（通用：变更/新增/改善/问题）
 * 2026-09-02：顶层菜单「业务管理」→「需求管理」
 * ECN 工程变更为 type=CHANGE 的场景（QR-030 联动）
 */
@Data
@TableName("biz_requirement")
public class BizRequirement {

    @TableId(type = IdType.AUTO)
    private Long requirementId;

    /** 需求单号 RQ-xxx */
    private String requirementNo;

    /** 需求类型: CHANGE变更/ADD新增/IMPROVE改善/ISSUE问题 */
    private String requirementType;

    /** 需求标题 */
    private String title;

    /** 需求描述/变更内容 */
    private String description;

    /** 来源: CUSTOMER客户/SALES销售/QUALITY品质/ENGINEERING工程/PRODUCTION生产/MANAGEMENT管理/OTHER其他 */
    private String source;

    /** 紧急度: urgent/high/normal/low */
    private String urgency;

    /** 期望完成日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectDate;

    /** 关联业务类型(多态) */
    private String bizType;

    /** 关联业务ID */
    private Long bizId;

    /** 关联业务单号/编码(冗余展示) */
    private String bizNo;

    /** 状态: 1草稿/2评审中/3已通过/4执行中/5已关闭/6已驳回 */
    private Integer requirementStatus;

    // ===== ECN 扩展（type=CHANGE 工程变更）=====
    /** 变更类型: DESIGN设计改版/PROCESS工艺调整/MATERIAL材料变更/DRAWING图纸更新/OTHER其他 */
    private String changeType;

    /** 切入方式: IMMEDIATE立即切入/BATCH按批切换 */
    private String cutoverMode;

    /** 是否重打样: 0否/1是 */
    private Integer needResample;

    /** 变更前版本(BOM/路线) */
    private String versionBefore;

    /** 变更后版本(BOM/路线) */
    private String versionAfter;

    // ===== 流程 =====
    private Long applicantId;
    private String applicantName;
    private LocalDateTime applyTime;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private String reviewRemark;
    /** 执行人（开始执行时记录） */
    private String executeBy;
    /** 开始执行时间 */
    private LocalDateTime executeTime;
    /** 执行结果（关闭时登记） */
    private String executeResult;
    /** 当前会签轮次（submit 时 +1） */
    private Integer currentRound;
    private LocalDateTime closeTime;
    private String remark;

    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
