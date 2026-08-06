package com.jjx.sales.domain.dto;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 客户导入DTO（DEV-662 客户管理导入功能）
 */
@Data
public class CustomerImportDTO {

    /**
     * 客户名称（必填）
     */
    @ExcelColumn(value = "客户名称(*)", order = 1, required = true, comment = "客户全称")
    private String customerName;

    /**
     * 客户简称
     */
    @ExcelColumn(value = "客户简称", order = 2, comment = "客户简称")
    private String customerShortName;

    /**
     * 客户类型（1=终端客户 2=代理商 3=经销商）
     */
    @ExcelColumn(value = "客户类型", order = 3, comment = "1=终端客户, 2=代理商, 3=经销商")
    private Integer customerType;

    /**
     * 客户等级（1=A 2=B 3=C）
     */
    @ExcelColumn(value = "客户等级", order = 4, comment = "1=A级, 2=B级, 3=C级")
    private Integer customerLevel;

    /**
     * 行业类别
     */
    @ExcelColumn(value = "行业类别", order = 5, comment = "客户所属行业")
    private String industryCategory;

    /**
     * 客户来源（1=展会 2=网络 3=转介绍 4=其他）
     */
    @ExcelColumn(value = "客户来源", order = 6, comment = "1=展会, 2=网络, 3=转介绍, 4=其他")
    private Integer customerSource;

    /**
     * 国家
     */
    @ExcelColumn(value = "国家", order = 7, comment = "国家")
    private String country;

    /**
     * 省份
     */
    @ExcelColumn(value = "省份", order = 8, comment = "省份")
    private String province;

    /**
     * 城市
     */
    @ExcelColumn(value = "城市", order = 9, comment = "城市")
    private String city;

    /**
     * 地址
     */
    @ExcelColumn(value = "详细地址", order = 10, comment = "详细地址")
    private String address;

    /**
     * 联系人
     */
    @ExcelColumn(value = "联系人", order = 11, comment = "客户联系人姓名")
    private String contactPerson;

    /**
     * 联系电话
     */
    @ExcelColumn(value = "联系电话", order = 12, comment = "客户联系电话")
    private String contactPhone;

    /**
     * 邮箱
     */
    @ExcelColumn(value = "邮箱", order = 13, comment = "客户邮箱")
    private String contactEmail;

    /**
     * 统一社会信用代码
     */
    @ExcelColumn(value = "统一社会信用代码", order = 14, comment = "18位统一社会信用代码")
    private String unifiedSocialCreditCode;

    /**
     * 纳税人识别号
     */
    @ExcelColumn(value = "纳税人识别号", order = 15, comment = "纳税人识别号")
    private String taxpayerId;

    /**
     * 开户银行
     */
    @ExcelColumn(value = "开户银行", order = 16, comment = "开户银行")
    private String bankName;

    /**
     * 银行账号
     */
    @ExcelColumn(value = "银行账号", order = 17, comment = "银行账号")
    private String bankAccount;

    /**
     * 付款方式（1=现结 2=月结 3=预付款）
     */
    @ExcelColumn(value = "付款方式", order = 18, comment = "1=现结, 2=月结, 3=预付款")
    private Integer paymentMethod;

    /**
     * 信用额度
     */
    @ExcelColumn(value = "信用额度", order = 19, comment = "信用额度（元）")
    private Double creditLimit;

    /**
     * 备注
     */
    @ExcelColumn(value = "备注", order = 20, comment = "备注信息")
    private String remark;
}
