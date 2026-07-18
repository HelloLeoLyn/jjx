package com.jjx.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购票据表实体类
 * 对应表：purchase_document
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("purchase_document")
public class PurchaseDocument {

    /**
     * 票据ID
     */
    @TableId(value = "document_id", type = IdType.AUTO)
    private Long documentId;

    /**
     * 票据编号
     */
    private String documentNo;

    /**
     * 票据类型（invoice发票/receipt收据/contract合同/quotation报价单/delivery_note送货单/other其他）
     */
    private String documentType;

    /**
     * 关联的采购订单
     */
    private Long orderId;

    /**
     * 供应商ID
     */
    private Long supplierId;

    /**
     * 票据日期
     */
    private LocalDate documentDate;

    /**
     * 票据金额
     */
    private BigDecimal documentAmount;

    /**
     * 币种
     */
    private String currency;

    /**
     * 票据状态（pending待处理/verified已核验/archived已归档）
     */
    private String documentStatus;

    /**
     * 核验日期
     */
    private LocalDate verificationDate;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件URL
     */
    private String fileUrl;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建者
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
