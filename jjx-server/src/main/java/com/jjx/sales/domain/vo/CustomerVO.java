package com.jjx.sales.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户信息VO
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "客户信息VO")
public class CustomerVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "客户类型 (1: 终端客户, 2: 代理商, 3: 经销商)", example = "1")
    private Integer customerType;

    @Schema(description = "客户等级 (1: A级, 2: B级, 3: C级)", example = "3")
    private Integer customerLevel;

    @Schema(description = "客户来源 (1: 展会, 2: 网络, 3: 转介绍, 4: 主动开发)", example = "1")
    private Integer customerSource;

    @Schema(description = "行业分类", example = "电子制造")
    private String industryCategory;

    @Schema(description = "联系邮箱", example = "zhangsan@example.com")
    private String contactEmail;

    @Schema(description = "客户ID", example = "1001")
    private Long customerId;

    @Schema(description = "客户编码", example = "CUST001")
    private String customerCode;

    @Schema(description = "客户名称", example = "XX科技有限公司")
    private String customerName;

    @Schema(description = "客户简称", example = "XX科技")
    private String customerShortName;

    @Schema(description = "联系人", example = "张三")
    private String contactPerson;

    @Schema(description = "联系电话", example = "13800138000")
    private String contactPhone;

    @Schema(description = "地址", example = "上海市浦东新区XX路XX号")
    private String address;

    @Schema(description = "国家")
    private String country;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "邮政编码")
    private String postalCode;

    @Schema(description = "信用额度", example = "100000.00")
    private BigDecimal creditLimit;

    /** 客户状态 (1: 潜在客户, 2: 正式客户, 3: 暂停合作, 4: 终止合作) */
    @Schema(description = "客户状态", example = "2")
    private Integer customerStatus;

    @Schema(description = "付款方式 (1: 预付, 2: 货到付款, 3: 月结30天, 4: 月结60天)", example = "1")
    private Integer paymentMethod;

    @Schema(description = "备注", example = "VIP客户")
    private String remark;

    @Schema(description = "是否VIP客户", example = "false")
    private Boolean vip;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
