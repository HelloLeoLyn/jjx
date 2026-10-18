package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.dto.SalesCustomerContactDTO;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.entity.SalesCustomerAddress;
import com.jjx.sales.domain.entity.SalesCustomerContact;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.sales.mapper.SalesCustomerAddressMapper;
import com.jjx.sales.mapper.SalesCustomerContactMapper;
import com.jjx.sales.service.ISalesCustomerContactService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesCustomerContactServiceImpl implements ISalesCustomerContactService {
    private final SalesCustomerContactMapper contactMapper;
    private final CustomerMapper customerMapper;
    private final SalesCustomerAddressMapper addressMapper;

    @Override
    public List<SalesCustomerContact> listByCustomer(Long customerId) {
        if (customerId == null || customerMapper.selectById(customerId) == null) {
            throw new BusinessException("客户不存在");
        }
        return contactMapper.selectList(Wrappers.<SalesCustomerContact>lambdaQuery()
                .eq(SalesCustomerContact::getCustomerId, customerId)
                .orderByAsc(SalesCustomerContact::getContactPerson, SalesCustomerContact::getContactId));
    }

    @Override
    public SalesCustomerContact requireOwned(Long customerId, Long contactId) {
        if (customerId == null || contactId == null) throw new BusinessException("客户ID或联系人ID不能为空");
        SalesCustomerContact contact = contactMapper.selectById(contactId);
        if (contact == null) throw new BusinessException("联系人不存在或已删除");
        if (!customerId.equals(contact.getCustomerId())) throw new BusinessException("联系人不属于该客户");
        return contact;
    }

    /** 与地址写入采用相同客户行锁，避免删除联系人与新增地址交错。 */
    private void lockCustomer(Long customerId) {
        if (customerId == null || customerMapper.selectOne(Wrappers.<SalesCustomer>lambdaQuery()
                .eq(SalesCustomer::getCustomerId, customerId).last("FOR UPDATE")) == null) {
            throw new BusinessException("客户不存在");
        }
    }

    private void copy(SalesCustomerContactDTO dto, SalesCustomerContact target) {
        if (dto == null || StringUtils.isBlank(dto.getContactPerson())) throw new BusinessException("请输入联系人姓名");
        target.setContactPerson(dto.getContactPerson().trim());
        target.setContactPhone(StringUtils.trimToEmpty(dto.getContactPhone()));
        target.setRemark(StringUtils.trimToEmpty(dto.getRemark()));
        Long duplicates = contactMapper.selectCount(Wrappers.<SalesCustomerContact>lambdaQuery()
                .eq(SalesCustomerContact::getCustomerId, target.getCustomerId())
                .eq(SalesCustomerContact::getContactPerson, target.getContactPerson())
                .eq(SalesCustomerContact::getContactPhone, target.getContactPhone())
                .ne(target.getContactId() != null, SalesCustomerContact::getContactId, target.getContactId()));
        if (duplicates > 0) throw new BusinessException("该客户已存在相同姓名和电话的联系人");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(Long customerId, SalesCustomerContactDTO dto) {
        lockCustomer(customerId);
        SalesCustomerContact contact = new SalesCustomerContact();
        contact.setCustomerId(customerId);
        copy(dto, contact);
        contactMapper.insert(contact);
        return contact.getContactId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long customerId, Long contactId, SalesCustomerContactDTO dto) {
        lockCustomer(customerId);
        SalesCustomerContact contact = requireOwned(customerId, contactId);
        copy(dto, contact);
        contactMapper.updateById(contact);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long customerId, Long contactId) {
        lockCustomer(customerId);
        requireOwned(customerId, contactId);
        if (addressMapper.selectCount(Wrappers.<SalesCustomerAddress>lambdaQuery()
                .eq(SalesCustomerAddress::getCustomerId, customerId)
                .eq(SalesCustomerAddress::getContactId, contactId)) > 0) {
            throw new BusinessException("该联系人仍被地址使用，请先在地址中解除关联或更换联系人");
        }
        contactMapper.deleteById(contactId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long ensurePrimaryContact(SalesCustomer customer) {
        if (customer == null || StringUtils.isBlank(customer.getContactPerson())) return null;
        lockCustomer(customer.getCustomerId());
        SalesCustomerContact existing = contactMapper.selectOne(Wrappers.<SalesCustomerContact>lambdaQuery()
                .eq(SalesCustomerContact::getCustomerId, customer.getCustomerId())
                .eq(SalesCustomerContact::getContactPerson, customer.getContactPerson().trim())
                .eq(SalesCustomerContact::getContactPhone, StringUtils.trimToEmpty(customer.getContactPhone())));
        if (existing != null) return existing.getContactId();
        SalesCustomerContactDTO dto = new SalesCustomerContactDTO();
        dto.setContactPerson(customer.getContactPerson());
        dto.setContactPhone(customer.getContactPhone());
        return add(customer.getCustomerId(), dto);
    }
}
