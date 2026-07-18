package com.jjx.purchase.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 采购订单审批数据传输对象
 */
@Data
public class PurchaseOrderApprovalDTO {

    /**
     * 采购订单ID
     */
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    /**
     * 审批人ID
     */
    @NotNull(message = "审批人ID不能为空")
    private Long approverId;

    /**
     * 审批人姓名
     */
    @NotNull(message = "审批人姓名不能为空")
    @Size(max = 50, message = "审批人姓名长度不能超过50个字符")
    private String approverName;

    /**
     * 审批意见
     */
    @Size(max = 500, message = "审批意见长度不能超过500个字符")
    private String approvalComment;

    /**
     * 审批状态
     */
    @NotNull(message = "审批状态不能为空")
    private Integer approvalStatus;
}
