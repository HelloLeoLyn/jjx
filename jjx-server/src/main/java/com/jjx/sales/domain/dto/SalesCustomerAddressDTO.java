package com.jjx.sales.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户收货地址 DTO（新增/修改共用）（dev-20261008-029）
 */
@Data
@Schema(description = "客户收货地址 DTO")
public class SalesCustomerAddressDTO {

    @Schema(description = "地址ID（修改时必填）")
    private Long addressId;

    @Schema(description = "客户ID（由路径提供，可忽略）")
    private Long customerId;

    @Size(max = 50, message = "地址标签长度不能超过50个字符")
    @Schema(description = "地址标签", example = "上海总部")
    private String label;

    @Size(max = 50, message = "联系人长度不能超过50个字符")
    @Schema(description = "收货联系人", example = "张三")
    private String contactPerson;

    @Size(max = 50, message = "联系电话长度不能超过50个字符")
    @Schema(description = "收货联系电话", example = "13800138000")
    private String contactPhone;

    @Size(max = 50, message = "国家长度不能超过50个字符")
    @Schema(description = "国家/地区", example = "中国")
    private String country;

    @Size(max = 50, message = "省份长度不能超过50个字符")
    @Schema(description = "省份/州", example = "广东省")
    private String province;

    @Size(max = 50, message = "城市长度不能超过50个字符")
    @Schema(description = "城市", example = "深圳市")
    private String city;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址长度不能超过255个字符")
    @Schema(description = "详细地址", example = "宝安区沙井街道XX工业园F栋4楼")
    private String address;

    @Size(max = 20, message = "邮政编码长度不能超过20个字符")
    @Schema(description = "邮政编码", example = "518104")
    private String postalCode;

    @Schema(description = "是否设为默认 0否 1是")
    private Integer isDefault;

    @Size(max = 255, message = "备注长度不能超过255个字符")
    @Schema(description = "备注")
    private String remark;
}
