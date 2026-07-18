package com.jjx.sales.domain.dto;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerQueryDTO extends PageQuery {

    /**
     * 客户编码（模糊查询）
     */
    private String customerCode;

    /**
     * 客户名称（模糊查询）
     */
    private String customerName;

    /**
     * 客户类型 (1: 终端客户, 2: 代理商, 3: 经销商)
     */
    private Integer customerType;

    /**
     * 客户等级 (1: A级, 2: B级, 3: C级)
     */
    private Integer customerLevel;

    /**
     * 客户状态 (1: 潜在客户, 2: 正式客户, 3: 暂停合作, 4: 终止合作)
     */
    private Integer customerStatus;

    /**
     * 联系人姓名（模糊查询）
     */
    private String contactPerson;

    /**
     * 联系电话（模糊查询）
     */
    private String contactPhone;

    /**
     * 销售负责人ID
     */
    private Long salesManagerId;
}
