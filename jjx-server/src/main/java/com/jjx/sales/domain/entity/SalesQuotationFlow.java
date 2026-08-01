package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报价单状态流转记录
 */
@Data
@TableName("sales_quotation_flow")
public class SalesQuotationFlow {

    /** 流转记录ID */
    @TableId(type = IdType.AUTO)
    private Long flowId;

    /** 报价单ID */
    private Long quotationId;

    /** 动作编码: SUBMIT_REVIEW/APPROVE/REJECT/SEND/CUSTOMER_CONFIRM/CUSTOMER_REJECT */
    private String actionCode;

    /** 动作名称 */
    private String actionName;

    /** 流转前状态 */
    private Integer fromStatus;

    /** 流转后状态 */
    private Integer toStatus;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 流转说明/审核意见 */
    private String remark;

    /** 附件ID列表(JSON数组) */
    private String attachmentIds;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
