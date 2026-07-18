package com.jjx.purchase.domain.dto;

import com.jjx.purchase.domain.enums.SupplierTypeEnum;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 供应商数据传输对象
 */
@Data
public class PurchaseSupplierDTO {

    /**
     * 供应商ID（更新时使用）
     */
    private Long supplierId;

    /**
     * 供应商编码
     */
    @NotBlank(message = "供应商编码不能为空")
    @Size(max = 50, message = "供应商编码长度不能超过50个字符")
    private String supplierCode;

    /**
     * 供应商名称
     */
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 200, message = "供应商名称长度不能超过200个字符")
    private String supplierName;

    /**
     * 供应商类型（material原材料/equipment设备/other其他）
     */
    @NotNull(message = "供应商类型不能为空")
    private SupplierTypeEnum supplierType;

    /**
     * 联系人
     */
    @Size(max = 50, message = "联系人长度不能超过50个字符")
    private String contactPerson;

    /**
     * 电话
     */
    @Size(max = 20, message = "电话长度不能超过20个字符")
    @Pattern(regexp = "^[0-9\\-\\+\\s]*$", message = "电话格式不正确")
    private String phone;

    /**
     * 邮箱
     */
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 地址
     */
    @Size(max = 500, message = "地址长度不能超过500个字符")
    private String address;

    /**
     * 付款条件
     */
    @Size(max = 100, message = "付款条件长度不能超过100个字符")
    private String paymentTerms;

    /**
     * 银行账户
     */
    @Size(max = 100, message = "银行账户长度不能超过100个字符")
    private String bankAccount;

    /**
     * 税号
     */
    @Size(max = 50, message = "税号长度不能超过50个字符")
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
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
