package com.jjx.purchase.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单明细数据传输对象
 */
@Data
public class PurchaseOrderItemDTO {

    /**
     * 明细ID（更新时使用）
     */
    private Long itemId;

    /**
     * 采购订单ID
     */
    @NotNull(message = "采购订单ID不能为空")
    private Long orderId;

    /**
     * 物料ID
     */
    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    /**
     * 物料编码
     */
    @NotBlank(message = "物料编码不能为空")
    @Size(max = 50, message = "物料编码长度不能超过50个字符")
    private String materialCode;

    /**
     * 物料名称
     */
    @NotBlank(message = "物料名称不能为空")
    @Size(max = 200, message = "物料名称长度不能超过200个字符")
    private String materialName;

    /**
     * 物料规格
     */
    @Size(max = 200, message = "物料规格长度不能超过200个字符")
    private String materialSpec;

    /**
     * 单位
     */
    @NotBlank(message = "单位不能为空")
    @Size(max = 20, message = "单位长度不能超过20个字符")
    private String unit;

    /**
     * 订单数量
     */
    @NotNull(message = "订单数量不能为空")
    private BigDecimal quantity;

    /**
     * 单价
     */
    @NotNull(message = "单价不能为空")
    private BigDecimal unitPrice;

    /**
     * 金额
     */
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    /**
     * 税率（百分比）
     */
    private BigDecimal taxRate;

    /**
     * 税额
     */
    private BigDecimal taxAmount;

    /**
     * 已收货数量
     */
    private BigDecimal receivedQuantity;

    /**
     * 收货状态（pending待收货/partially_received部分收货/completed已收货）
     */
    @Pattern(regexp = "^(pending|partially_received|completed)$", message = "收货状态不正确")
    private String receiptStatus;

    /**
     * 询价信息JSON
     */
    private String inquiryInfo;

    /**
     * 询价状态（pending待询价/inquired已询价/comparing比价中/selected已选中）
     */
    @Pattern(regexp = "^(pending|inquired|comparing|selected)$", message = "询价状态不正确")
    private Integer inquiryStatus;

    /**
     * 批次号
     */
    @Size(max = 50, message = "批次号长度不能超过50个字符")
    private String batchNo;

    /**
     * 生产日期
     */
    private LocalDate productionDate;

    /**
     * 有效期至
     */
    private LocalDate expiryDate;

    /**
     * 检验结果（passed合格/failed不合格）
     */
    @Pattern(regexp = "^(passed|failed)$", message = "检验结果必须是passed或failed")
    private String inspectionResult;

    /**
     * 检验备注
     */
    @Size(max = 500, message = "检验备注长度不能超过500个字符")
    private String inspectionRemark;

    /**
     * 排序
     */
    private Integer itemOrder;

}
