package com.jjx.purchase.domain.vo;

import lombok.Data;

/**
 * 供应商查询参数视图对象
 */
@Data
public class PurchaseSupplierQueryVO {

    /** 分页页码（DEV-696：供应商列表分页） */
    private Integer pageNum = 1;

    /** 分页大小（DEV-696） */
    private Integer pageSize = 10;

    /**
     * 供应商编码
     */
    private String supplierCode;

    /**
     * 供应商名称
     */
    private String supplierName;

    /**
     * 供应商类型
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
     * 状态
     */
    private Integer status;

    /**
     * 开始创建时间
     */
    private String beginCreateTime;

    /**
     * 结束创建时间
     */
    private String endCreateTime;
}
