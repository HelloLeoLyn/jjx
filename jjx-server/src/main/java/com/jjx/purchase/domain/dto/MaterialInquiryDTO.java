package com.jjx.purchase.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 材料询价DTO
 *
 * @author JJX ERP系统
 * @date 2026-04-02
 */
@Data
public class MaterialInquiryDTO {

    /**
     * 询价ID
     */
    private Long inquiryId;

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
     * 规格型号
     */
    private String materialSpec;

    /**
     * 单位
     */
    private String unit;

    /**
     * 询价日期
     */
    private Date inquiryDate;

    /**
     * 供应商ID
     */
    private Long supplierId;

    /**
     * 供应商名称
     */
    private String supplierName;

    /**
     * 供应商编码
     */
    private String supplierCode;

    /**
     * 询价单价
     */
    private BigDecimal inquiryPrice;

    /**
     * 币种
     */
    private String currency;

    /**
     * 询价数量
     */
    private BigDecimal quantity;

    /**
     * 交货天数
     */
    private Integer deliveryDays;

    /**
     * 付款条件
     */
    private String paymentTerms;

    /**
     * 报价有效期（天）
     */
    private Integer validityDays;

    /**
     * 询价人
     */
    private String inquiryPerson;

    /**
     * 询价状态（active/inactive/expired）
     */
    private String inquiryStatus;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新人
     */
    private String updateBy;

    // 扩展字段
    /**
     * 询价总金额（计算字段）
     */
    private BigDecimal totalAmount;

    /**
     * 是否在有效期内（计算字段）
     */
    private Boolean withinValidityPeriod;

    /**
     * 物料分类（扩展字段）
     */
    private String materialCategory;

    /**
     * 供应商联系人（扩展字段）
     */
    private String supplierContact;

    /**
     * 供应商电话（扩展字段）
     */
    private String supplierPhone;

    /**
     * 计算总金额
     */
    public BigDecimal calculateTotalAmount() {
        if (inquiryPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return inquiryPrice.multiply(quantity);
    }

    /**
     * 判断是否在有效期内
     */
    public boolean isWithinValidityPeriod() {
        if (inquiryDate == null || validityDays == null) {
            return false;
        }

        Date expiryDate = new Date(inquiryDate.getTime() + (long) validityDays * 24 * 60 * 60 * 1000);
        return new Date().before(expiryDate);
    }
}
