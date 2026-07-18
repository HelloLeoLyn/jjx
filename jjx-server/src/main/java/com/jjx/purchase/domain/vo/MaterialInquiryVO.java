package com.jjx.purchase.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 材料询价VO（视图对象）
 *
 * @author JJX ERP系统
 * @date 2026-04-02
 */
@Data
public class MaterialInquiryVO {

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
     * 询价日期（格式化字符串）
     */
    private String inquiryDateStr;

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
     * 询价单价（格式化）
     */
    private String inquiryPriceStr;

    /**
     * 询价单价
     */
    private BigDecimal inquiryPrice;

    /**
     * 币种
     */
    private String currency;

    /**
     * 询价数量（格式化）
     */
    private String quantityStr;

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
     * 询价状态标签
     */
    private String inquiryStatusLabel;

    /**
     * 状态标签类型（success/warning/danger/info）
     */
    private String statusTagType;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间（格式化字符串）
     */
    private String createTimeStr;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间（格式化字符串）
     */
    private String updateTimeStr;

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

    // 计算字段
    /**
     * 询价总金额
     */
    private BigDecimal totalAmount;

    /**
     * 询价总金额（格式化）
     */
    private String totalAmountStr;

    /**
     * 是否在有效期内
     */
    private Boolean withinValidityPeriod;

    /**
     * 有效期截止日期
     */
    private Date validityEndDate;

    /**
     * 有效期截止日期（格式化字符串）
     */
    private String validityEndDateStr;

    /**
     * 剩余有效天数
     */
    private Integer remainingValidityDays;

    /**
     * 是否已过期
     */
    private Boolean expired;

    /**
     * 是否有效
     */
    private Boolean active;

    // 扩展信息
    /**
     * 物料分类
     */
    private String materialCategory;

    /**
     * 物料分类名称
     */
    private String materialCategoryName;

    /**
     * 供应商联系人
     */
    private String supplierContact;

    /**
     * 供应商电话
     */
    private String supplierPhone;

    /**
     * 供应商地址
     */
    private String supplierAddress;

    /**
     * 供应商评级
     */
    private Integer supplierRating;

    /**
     * 历史询价次数
     */
    private Integer inquiryCount;

    /**
     * 平均询价价格
     */
    private BigDecimal avgInquiryPrice;

    /**
     * 最低询价价格
     */
    private BigDecimal minInquiryPrice;

    /**
     * 最高询价价格
     */
    private BigDecimal maxInquiryPrice;

    /**
     * 价格趋势（up/down/stable）
     */
    private String priceTrend;

    /**
     * 价格变化百分比
     */
    private BigDecimal priceChangePercent;

    // 操作权限
    /**
     * 是否可以编辑
     */
    private Boolean canEdit = false;

    /**
     * 是否可以删除
     */
    private Boolean canDelete = false;

    /**
     * 是否可以使用（用于采购参考）
     */
    private Boolean canUse = false;

    /**
     * 是否可以复制
     */
    private Boolean canCopy = false;

    /**
     * 获取状态标签类型
     */
    public String getStatusTagType() {
        if (statusTagType != null) {
            return statusTagType;
        }

        if ("active".equals(inquiryStatus)) {
            return withinValidityPeriod != null && withinValidityPeriod ? "success" : "warning";
        } else if ("expired".equals(inquiryStatus)) {
            return "danger";
        } else if ("inactive".equals(inquiryStatus)) {
            return "info";
        }
        return "default";
    }

    /**
     * 获取状态标签文本
     */
    public String getInquiryStatusLabel() {
        if (inquiryStatusLabel != null) {
            return inquiryStatusLabel;
        }

        switch (inquiryStatus) {
            case "active":
                return "有效";
            case "inactive":
                return "无效";
            case "expired":
                return "已过期";
            default:
                return inquiryStatus;
        }
    }

    /**
     * 判断是否可以使用
     */
    public Boolean getCanUse() {
        if (canUse != null) {
            return canUse;
        }
        return "active".equals(inquiryStatus) &&
               (withinValidityPeriod == null || withinValidityPeriod);
    }
}
