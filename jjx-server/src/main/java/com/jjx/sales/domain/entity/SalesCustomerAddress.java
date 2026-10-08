package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户收货地址簿实体（dev-20261008-029）
 * 一个客户可维护多个收货地址，其中一个可标记为默认。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_customer_address")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SalesCustomerAddress extends BaseEntity {

    /** 地址ID */
    @TableId(value = "address_id", type = IdType.AUTO)
    private Long addressId;

    /** 客户ID（sales_customer.customer_id） */
    private Long customerId;

    /** 地址标签，如 上海总部/东莞仓 */
    private String label;

    /** 收货联系人 */
    private String contactPerson;

    /** 收货联系电话 */
    private String contactPhone;

    /** 国家/地区 */
    private String country;

    /** 省份/州 */
    private String province;

    /** 城市 */
    private String city;

    /** 详细地址 */
    private String address;

    /** 邮政编码 */
    private String postalCode;

    /** 是否客户默认收货地址 0否 1是 */
    private Integer isDefault;

    /** 逻辑删除 0否 1是 */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
