package com.jjx.sales.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户新增DTO
 */
@Data
@Schema(description = "客户新增DTO")
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerAddDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 100, message = "客户名称长度不能超过100个字符")
    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX科技有限公司")
    private String customerName;

    @Size(max = 50, message = "客户简称长度不能超过50个字符")
    @Schema(description = "客户简称", example = "XX科技")
    private String customerShortName;

    @Schema(description = "客户类型 (1: 终端客户, 2: 代理商, 3: 经销商)", example = "1")
    private Integer customerType;

    @Schema(description = "客户状态 (1: 潜在客户, 2: 正式客户, 3: 暂停合作, 4: 终止合作)", example = "2")
    private Integer customerStatus;

    @Schema(description = "客户等级 (1: A级, 2: B级, 3: C级)", example = "3")
    private Integer customerLevel;

    @Size(max = 100, message = "行业分类长度不能超过100个字符")
    @Schema(description = "行业分类", example = "电子制造")
    private String industryCategory;

    @Schema(description = "客户来源 (1: 展会, 2: 网络, 3: 转介绍, 4: 主动开发)", example = "1")
    private Integer customerSource;

    @Size(max = 50, message = "联系人长度不能超过50个字符")
    @Schema(description = "联系人", example = "张三")
    private String contactPerson;

    @Pattern(regexp = "^(1[3-9]\\d{9}|(0\\d{2,3}-?)?\\d{7,8})$", message = "联系电话格式不正确")
    @Size(max = 20, message = "联系电话长度不能超过20个字符")
    @Schema(description = "联系电话", example = "13800138000")
    private String contactPhone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "联系邮箱长度不能超过100个字符")
    @Schema(description = "联系邮箱", example = "zhangsan@example.com")
    private String contactEmail;

    @Pattern(regexp = "^(0\\d{2,3}-?)?\\d{7,8}$|^$",
            message = "传真号码格式不正确")
    @Size(max = 20, message = "传真号码长度不能超过20个字符")
    @Schema(description = "传真", example = "021-12345678")
    private String fax;

    @Size(max = 50, message = "国家长度不能超过50个字符")
    @Schema(description = "国家")
    private String country;

    @Size(max = 50, message = "省份长度不能超过50个字符")
    @Schema(description = "省份")
    private String province;

    @Size(max = 50, message = "城市长度不能超过50个字符")
    @Schema(description = "城市")
    private String city;

    @Size(max = 200, message = "详细地址长度不能超过200个字符")
    @Schema(description = "详细地址", example = "上海市浦东新区XX路XX号")
    private String address;

    @Size(max = 20, message = "邮编长度不能超过20个字符")
    @Schema(description = "邮政编码")
    private String postalCode;

    @DecimalMin(value = "0", message = "信用额度不能为负数")
    @Schema(description = "信用额度", example = "100000.00")
    private Double creditLimit;

    @Schema(description = "付款方式 (1: 预付, 2: 货到付款, 3: 月结30天, 4: 月结60天)", example = "1")
    private Integer paymentMethod;

    @Schema(description = "是否VIP客户", example = "false")
    private Boolean vip;

    @Min(value = 1, message = "客户评分最小为1")
    @Max(value = 5, message = "客户评分最大为5")
    @Schema(description = "客户评分 (1-5分)", example = "3")
    private Integer customerScore;

    @Size(max = 500, message = "备注长度不能超过500个字符")
    @Schema(description = "备注", example = "VIP客户")
    private String remark;
}
