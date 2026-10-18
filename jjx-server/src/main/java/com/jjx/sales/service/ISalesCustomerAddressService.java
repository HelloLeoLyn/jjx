package com.jjx.sales.service;

import com.jjx.sales.domain.dto.SalesCustomerAddressDTO;
import com.jjx.sales.domain.entity.SalesCustomerAddress;
import com.jjx.sales.domain.entity.SalesCustomer;

import java.util.List;

/**
 * 客户收货地址簿服务接口（dev-20261008-029）
 */
public interface ISalesCustomerAddressService {

    /** 将客户自身地址补为公司地址；空地址及已有相同地址不重复创建。 */
    void ensureCompanyAddress(SalesCustomer customer);

    /** 查询某客户的全部收货地址（默认地址置顶） */
    List<SalesCustomerAddress> listByCustomer(Long customerId);

    /** 新增收货地址；无其它地址或显式指定时设为默认 */
    Long add(Long customerId, SalesCustomerAddressDTO dto);

    /** 修改收货地址 */
    boolean update(Long customerId, SalesCustomerAddressDTO dto);

    /** 删除收货地址（逻辑删除） */
    boolean delete(Long customerId, Long addressId);

    /** 设为默认地址 */
    boolean setDefault(Long customerId, Long addressId);
}
