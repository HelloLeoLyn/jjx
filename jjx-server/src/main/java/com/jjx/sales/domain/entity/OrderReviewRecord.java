package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订单审核记录实体类
 * 记录订单审核流程的详细信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_order_review")
public class OrderReviewRecord extends BaseEntity {

    /**
     * 审核记录ID
     */
    @TableId(value = "review_id", type = IdType.AUTO)
    private Long recordId;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 审核阶段
     * 1: 提交审核
     * 2: 开始审核
     * 3: 审核通过
     * 4: 审核驳回
     * 5: 客户确认
     * 6: 状态变更
     */
    private Integer reviewStage;

    /**
     * 审核阶段名称
     */
    private String stageName;

    /**
     * 审核前状态
     */
    private Integer previousStatus;

    /**
     * 审核后状态
     */
    private Integer currentStatus;

    /**
     * 审核人ID
     */
    private Long reviewerId;

    /**
     * 审核人姓名
     */
    private String reviewerName;

    /**
     * 审核人角色
     */
    private String reviewerRole;

    /**
     * 审核意见
     */
    private String reviewComment;

    /**
     * 审核附件（JSON格式存储附件信息）
     */
    private String attachments;

    /**
     * 审核时间
     */
    private LocalDateTime reviewTime;

    /**
     * 审核耗时（分钟）
     */
    private Integer reviewDuration;

    /**
     * 审核结果
     * 1: 通过
     * 2: 驳回
     * 3: 退回修改
     * 4: 转交他人
     */
    private Integer reviewResult;

    /**
     * 审核结果描述
     */
    private String resultDescription;

    /**
     * 下一处理人ID
     */
    private Long nextHandlerId;

    /**
     * 下一处理人姓名
     */
    private String nextHandlerName;

    /**
     * 是否最终审核
     */
    private Boolean isFinalReview;

    /**
     * 审核流程ID（用于关联多级审核）
     */
    private String reviewProcessId;

    /**
     * 审核节点序号
     */
    private Integer nodeSequence;

    /**
     * 审核节点名称
     */
    private String nodeName;

    /**
     * 审核要求
     */
    private String reviewRequirements;

    /**
     * 审核标准
     */
    private String reviewCriteria;

    /**
     * 审核得分（0-100）
     */
    private Integer reviewScore;

    /**
     * 风险等级
     * 1: 低风险
     * 2: 中风险
     * 3: 高风险
     */
    private Integer riskLevel;

    /**
     * 风险描述
     */
    private String riskDescription;

    /**
     * 改进建议
     */
    private String improvementSuggestions;

    /**
     * 是否通知客户
     */
    private Boolean notifyCustomer;

    /**
     * 客户通知方式
     */
    private String notificationMethod;

    /**
     * 客户反馈
     */
    private String customerFeedback;

    /**
     * 是否紧急处理
     */
    private Boolean isUrgent;

    /**
     * 紧急原因
     */
    private String urgentReason;

    /**
     * 审核版本号
     */
    private Integer reviewVersion;

    /**
     * 关联的业务ID（如报价单ID、合同ID等）
     */
    private Long relatedBusinessId;

    /**
     * 关联的业务类型
     */
    private String relatedBusinessType;

    /**
     * 审核流程类型
     * 1: 普通审核
     * 2: 加急审核
     * 3: 会签审核
     * 4: 或签审核
     */
    private Integer reviewProcessType;

    /**
     * 审核流程状态
     * 1: 进行中
     * 2: 已完成
     * 3: 已终止
     * 4: 已超时
     */
    private Integer processStatus;

    /**
     * 流程开始时间
     */
    private LocalDateTime processStartTime;

    /**
     * 流程结束时间
     */
    private LocalDateTime processEndTime;

    /**
     * 流程超时时间
     */
    private LocalDateTime processTimeoutTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志 (0: 正常, 1: 删除)
     */
    @TableLogic
    private Integer deleted;
}
