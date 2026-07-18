package com.jjx.purchase.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单明细视图对象
 */
@Data
public class PurchaseOrderItemVO {

    /**
     * 明细ID
     */
    private Long itemId;

    /**
     * 采购订单ID
     */
    private Long orderId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 物料规格
     */
    private String materialSpec;

    /**
     * 单位
     */
    private String unit;

    /**
     * 订单数量
     */
    private BigDecimal quantity;

    /**
     * 单价
     */
    private BigDecimal unitPrice;

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 已收货数量
     */
    private BigDecimal receivedQuantity;

    /**
     * 收货状态（pending待收货/partially_received部分收货/completed已收货）
     */
    private Integer receiptStatus;

    /**
     * 收货状态名称
     */
    private String receiptStatusName;

    /**
     * 询价信息JSON
     */
    private String inquiryInfo;

    /**
     * 询价状态（pending待询价/inquired已询价/comparing比价中/selected已选中）
     */
    private String inquiryStatus;

    /**
     * 询价状态名称
     */
    private String inquiryStatusName;

    /**
     * 批次号
     */
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
    private String inspectionResult;

    /**
     * 检验结果名称
     */
    private String inspectionResultName;

    /**
     * 检验备注
     */
    private String inspectionRemark;

    /**
     * 排序
     */
    private Integer itemOrder;
}
