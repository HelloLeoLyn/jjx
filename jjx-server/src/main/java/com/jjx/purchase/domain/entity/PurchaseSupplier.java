package com.jjx.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商表实体类
 * 对应表：purchase_supplier
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("purchase_supplier")
public class PurchaseSupplier {

    /**
     * 供应商ID
     */
    @TableId(value = "supplier_id", type = IdType.AUTO)
    private Long supplierId;

    /**
     * 供应商编码
     */
    private String supplierCode;

    /**
     * 供应商名称
     */
    private String supplierName;

    /**
     * 供应商类型（material原材料/equipment设备/other其他）
     */
    private String supplierType;

    /**
     * 联系人
     */
    private String contactPerson;

    /**
     * 电话
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 地址
     */
    private String address;

    /**
     * 付款条件
     */
    private String paymentTerms;

    /**
     * 银行账户
     */
    private String bankAccount;

    /**
     * 税号
     */
    private String taxNumber;

    /**
     * 评估总分
     */
    private BigDecimal evaluationScore;

    /**
     * 质量评分
     */
    private BigDecimal qualityScore;

    /**
     * 交期评分
     */
    private BigDecimal deliveryScore;

    /**
     * 价格评分
     */
    private BigDecimal priceScore;

    /**
     * 最后评估日期
     */
    private LocalDate lastEvaluationDate;

    /**
     * 状态（0正常 1停用）
     */
    private Integer status;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

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

    /**
     * 备注
     */
    private String remark;
}
