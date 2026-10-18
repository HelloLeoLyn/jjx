package com.jjx.sales.service;

import com.jjx.sales.domain.dto.SalesCustomerContactDTO;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.entity.SalesCustomerContact;
import java.util.List;

public interface ISalesCustomerContactService {
    List<SalesCustomerContact> listByCustomer(Long customerId);
    SalesCustomerContact requireOwned(Long customerId, Long contactId);
    Long add(Long customerId, SalesCustomerContactDTO dto);
    void update(Long customerId, Long contactId, SalesCustomerContactDTO dto);
    void delete(Long customerId, Long contactId);
    /** 保存新客户主联系人至通讯录；无姓名时不生成空联系人。 */
    Long ensurePrimaryContact(SalesCustomer customer);
}
