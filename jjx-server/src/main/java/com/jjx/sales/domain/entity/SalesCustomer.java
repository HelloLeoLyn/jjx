package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 销售客户实体类
 * 薄膜开关ERP系统的客户管理核心实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_customer")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SalesCustomer extends BaseEntity {

    /**
     * 客户ID
     */
    @TableId(type = IdType.AUTO)
    private Long customerId;

    /**
     * 客户编码
     */
    private String customerCode;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 客户简称
     */
    private String customerShortName;

    /**
     * 客户类型 (1: 终端客户, 2: 代理商, 3: 经销商)
     */
    private Integer customerType;

    /**
     * 客户等级 (1: A级, 2: B级, 3: C级)
     */
    private Integer customerLevel;

    /**
     * 行业分类
     */
    private String industryCategory;

    /**
     * 客户来源 (1: 展会, 2: 网络, 3: 转介绍, 4: 主动开发)
     */
    private Integer customerSource;

    /**
     * 国家
     */
    private String country;

    /**
     * 省份
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 详细地址
     */
    private String address;

    /**
     * 邮政编码
     */
    private String postalCode;

    /**
     * 联系人姓名
     */
    private String contactPerson;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 联系邮箱
     */
    private String contactEmail;

    /**
     * 传真
     */
    private String fax;

    /**
     * 网址
     */
    private String website;

    /**
     * 统一社会信用代码
     */
    private String unifiedSocialCreditCode;

    /**
     * 纳税人识别号
     */
    private String taxpayerId;

    /**
     * 开户银行
     */
    private String bankName;

    /**
     * 银行账号
     */
    private String bankAccount;

    /**
     * 付款方式 (1: 预付, 2: 货到付款, 3: 月结30天, 4: 月结60天)
     */
    private Integer paymentMethod;

    /**
     * 付款条件
     */
    private String paymentTerms;

    /**
     * 信用额度
     */
    private Double creditLimit;

    /**
     * 已用信用额度
     */
    private Double usedCreditLimit;

    /**
     * 客户状态 (1: 潜在客户, 2: 正式客户, 3: 暂停合作, 4: 终止合作)
     */
    private Integer customerStatus;

    /**
     * 合作开始日期
     */
    private LocalDateTime cooperationStartDate;

    /**
     * 合作结束日期
     */
    private LocalDateTime cooperationEndDate;

    /**
     * 销售负责人ID
     */
    private Long salesManagerId;

    /**
     * 销售负责人姓名
     */
    private String salesManagerName;

    /**
     * 客户备注
     */
    private String remark;

    /**
     * 客户评分 (1-5分)
     */
    private Integer customerScore;

    /**
     * 年采购额
     */
    private Double annualPurchaseAmount;

    /**
     * 主要产品需求
     */
    private String mainProductDemand;

    /**
     * 特殊要求
     */
    private String specialRequirements;

    /**
     * 是否VIP客户
     */
    @TableField("is_vip")
    private Boolean vip;

    /**
     * 客户标签 (JSON格式存储多个标签)
     */
    private String customerTags;

    /**
     * 附件信息 (JSON格式存储附件信息)
     */
    private String attachments;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
