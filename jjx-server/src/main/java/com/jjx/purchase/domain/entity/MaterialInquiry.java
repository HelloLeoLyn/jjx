package com.jjx.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 材料询价实体类
 *
 * @author JJX ERP系统
 * @date 2026-04-02
 */
@Data
@TableName("material_inquiry")
public class MaterialInquiry {

    /**
     * 询价ID
     */
    @TableId(type = IdType.AUTO)
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
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新人
     */
    private String updateBy;

    // 状态常量
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";
    public static final String STATUS_EXPIRED = "expired";

    /**
     * 判断询价是否有效
     * @return true表示有效，false表示无效
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(inquiryStatus);
    }

    /**
     * 判断询价是否过期
     * @return true表示过期，false表示未过期
     */
    public boolean isExpired() {
        return STATUS_EXPIRED.equals(inquiryStatus);
    }

    /**
     * 计算询价总金额
     * @return 总金额（单价 × 数量）
     */
    public BigDecimal getTotalAmount() {
        if (inquiryPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return inquiryPrice.multiply(quantity);
    }

    /**
     * 判断询价是否在有效期内
     * @return true表示在有效期内，false表示已过期
     */
    public boolean isWithinValidityPeriod() {
        if (inquiryDate == null || validityDays == null) {
            return false;
        }

        Date expiryDate = new Date(inquiryDate.getTime() + (long) validityDays * 24 * 60 * 60 * 1000);
        return new Date().before(expiryDate);
    }
}
