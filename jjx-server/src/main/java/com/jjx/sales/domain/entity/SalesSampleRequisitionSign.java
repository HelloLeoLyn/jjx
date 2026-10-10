package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 样品需求单会签记录（dev-20261010-028）
 * 样品单级、三个签字位（业务/核准/部门主管）、可多轮、纯留痕（不驱动单据状态）。
 * 同一 样品单 × 签字位 × 轮次 唯一，重复签署即覆盖。
 */
@Data
@TableName("sales_sample_requisition_sign")
public class SalesSampleRequisitionSign {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样品单ID(sales_order.order_id) */
    private Long sampleOrderId;

    /** 轮次 */
    private Integer roundNo;

    /** 签字位: SALES业务/APPROVE核准/DEPT部门主管 */
    private String signRole;

    /** 结果: 1同意/0不同意 */
    private Integer approveResult;

    /** 签字意见 */
    private String comment;

    /** 签署人ID */
    private Long signerId;

    /** 签署人姓名 */
    private String signerName;

    /** 签署时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signTime;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
