package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户通讯录；地址和单据中的姓名、电话仍是选用时的快照。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_customer_contact")
public class SalesCustomerContact extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long contactId;
    private Long customerId;
    private String contactPerson;
    private String contactPhone;
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
