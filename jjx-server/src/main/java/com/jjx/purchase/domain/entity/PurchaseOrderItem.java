package com.jjx.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单明细表实体类
 * 对应表：purchase_order_item
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("purchase_order_item")
public class PurchaseOrderItem {

    /**
     * 明细ID
     */
    @TableId(value = "item_id", type = IdType.AUTO)
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

    /** 供应商品名：写单时从物料档案取得的快照。 */
    private String supplierMaterialName;

    /**
     * 物料规格
     */
    private String materialSpec;

    /**
     * 单位
     */
    private String unit;

    /** 采购/价格单位独立；unit、quantity 始终为物料基本单位与数量。 */
    private String purchaseUnit;
    private String priceUnit;
    /** 本次采购数量；quantity 始终为库存基本单位数量。旧单为空时按原单位解释。 */
    private BigDecimal purchaseQuantity;
    /** 本单固定片材尺寸快照，单位mm，不随物料档案后续修改变化。 */
    private BigDecimal pieceWidthMm;
    private BigDecimal pieceLengthMm;
    /** 采购/加工/交货要求，独立于检验备注。 */
    private String purchaseRemark;
    private BigDecimal taxRate;

    private Integer rollCount;
    private BigDecimal rollLengthM;
    private BigDecimal rollWidthMm;


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
     * 询价信息JSON
     */
    private String inquiryInfo;

    /**
     * 询价状态（pending待询价/inquired已询价/comparing比价中/selected已选中）
     */
    private Integer inquiryStatus;

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
     * 检验备注
     */
    private String inspectionRemark;

    /**
     * 排序
     */
    private Integer itemOrder;
}
