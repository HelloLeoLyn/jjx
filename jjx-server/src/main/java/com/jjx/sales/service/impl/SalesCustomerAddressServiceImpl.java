package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.dto.SalesCustomerAddressDTO;
import com.jjx.sales.domain.entity.SalesCustomerAddress;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.sales.mapper.SalesCustomerAddressMapper;
import com.jjx.sales.service.ISalesCustomerAddressService;
import com.jjx.sales.service.ISalesCustomerContactService;
import com.jjx.sales.domain.entity.SalesCustomerContact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * 客户收货地址簿服务实现（dev-20261008-029）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesCustomerAddressServiceImpl implements ISalesCustomerAddressService {

    private final SalesCustomerAddressMapper addressMapper;
    private final CustomerMapper customerMapper;
    private final ObjectMapper objectMapper;
    private final ISalesCustomerContactService contactService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureCompanyAddress(SalesCustomer customer) {
        if (customer == null || StringUtils.isBlank(customer.getAddress())) return;
        SalesCustomerAddressDTO dto = new SalesCustomerAddressDTO();
        dto.setLabel("公司地址");
        dto.setContactPerson(customer.getContactPerson());
        dto.setContactPhone(customer.getContactPhone());
        dto.setCountry(customer.getCountry());
        dto.setProvince(customer.getProvince());
        dto.setCity(customer.getCity());
        dto.setPostalCode(customer.getPostalCode());
        String raw = customer.getAddress().trim();
        if (raw.startsWith("{")) {
            try {
                JsonNode json = objectMapper.readTree(raw);
                dto.setAddress(json.path("street").asText(""));
                dto.setCountry(json.path("country").asText(dto.getCountry()));
                dto.setProvince(json.path("province").asText(dto.getProvince()));
                dto.setCity(json.path("city").asText(dto.getCity()));
                dto.setPostalCode(json.path("zipCode").asText(dto.getPostalCode()));
            } catch (JsonProcessingException e) {
                throw new BusinessException("客户公司地址格式无效，请检查地址信息");
            }
        } else {
            dto.setAddress(raw);
        }
        if (StringUtils.isBlank(dto.getAddress())) return;
        requireCustomer(customer.getCustomerId());
        boolean exists = listByCustomer(customer.getCustomerId()).stream().anyMatch(a ->
                "公司地址".equals(a.getLabel()) || (same(a.getAddress(), dto.getAddress())
                        && same(a.getCountry(), dto.getCountry()) && same(a.getProvince(), dto.getProvince())
                        && same(a.getCity(), dto.getCity()) && same(a.getPostalCode(), dto.getPostalCode())));
        if (!exists) {
            dto.setContactId(contactService.ensurePrimaryContact(customer));
            add(customer.getCustomerId(), dto);
        }
    }

    private boolean same(String left, String right) {
        return Objects.equals(StringUtils.trimToEmpty(left), StringUtils.trimToEmpty(right));
    }

    /** 锁住客户行，串行处理同客户地址写入，避免首条地址并发产生多个默认值。 */
    private SalesCustomer requireCustomer(Long customerId) {
        if (customerId == null) throw new BusinessException("客户ID不能为空");
        SalesCustomer customer = customerMapper.selectOne(Wrappers.<SalesCustomer>lambdaQuery()
                .eq(SalesCustomer::getCustomerId, customerId).last("FOR UPDATE"));
        if (customer == null) throw new BusinessException("客户不存在");
        return customer;
    }

    @Override
    public List<SalesCustomerAddress> listByCustomer(Long customerId) {
        if (customerId == null) {
            throw new BusinessException("客户ID不能为空");
        }
        LambdaQueryWrapper<SalesCustomerAddress> qw = Wrappers.lambdaQuery(SalesCustomerAddress.class);
        qw.eq(SalesCustomerAddress::getCustomerId, customerId)
                .orderByDesc(SalesCustomerAddress::getIsDefault)
                .orderByDesc(SalesCustomerAddress::getUpdateTime)
                .orderByDesc(SalesCustomerAddress::getAddressId);
        return addressMapper.selectList(qw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(Long customerId, SalesCustomerAddressDTO dto) {
        if (customerId == null) {
            throw new BusinessException("客户ID不能为空");
        }
        if (dto == null) {
            throw new BusinessException("地址信息不能为空");
        }
        requireCustomer(customerId);
        SalesCustomerAddress entity = new SalesCustomerAddress();
        entity.setCustomerId(customerId);
        copy(dto, entity);
        // 首个地址自动成为默认；否则按显式指定
        boolean first = countByCustomer(customerId) == 0;
        boolean asDefault = first || Objects.equals(dto.getIsDefault(), YesNoEnum.YES.getCode());
        entity.setIsDefault(asDefault ? YesNoEnum.YES.getCode() : YesNoEnum.NO.getCode());
        addressMapper.insert(entity);
        if (asDefault) {
            clearDefaultExcept(customerId, entity.getAddressId());
        }
        return entity.getAddressId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Long customerId, SalesCustomerAddressDTO dto) {
        requireCustomer(customerId);
        if (dto == null || dto.getAddressId() == null) {
            throw new BusinessException("地址ID不能为空");
        }
        SalesCustomerAddress existing = requireOwned(customerId, dto.getAddressId());
        boolean wasDefault = existing.getIsDefault() != null && existing.getIsDefault() == 1;
        copy(dto, existing);
        if (dto.getIsDefault() != null) {
            existing.setIsDefault(dto.getIsDefault() == 1 ? 1 : 0);
        }
        addressMapper.updateById(existing);
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            clearDefaultExcept(customerId, existing.getAddressId());
        } else if (wasDefault) {
            // 原默认被取消：保证客户至少仍有一条默认地址
            SalesCustomerAddress next = latestOther(customerId, existing.getAddressId());
            if (next != null) {
                next.setIsDefault(1);
                addressMapper.updateById(next);
            } else {
                existing.setIsDefault(1);
                addressMapper.updateById(existing);
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long customerId, Long addressId) {
        requireCustomer(customerId);
        SalesCustomerAddress existing = requireOwned(customerId, addressId);
        boolean wasDefault = existing.getIsDefault() != null && existing.getIsDefault() == 1;
        addressMapper.deleteById(addressId);
        if (wasDefault) {
            SalesCustomerAddress next = latestOther(customerId, addressId);
            if (next != null) {
                next.setIsDefault(1);
                addressMapper.updateById(next);
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefault(Long customerId, Long addressId) {
        requireCustomer(customerId);
        SalesCustomerAddress existing = requireOwned(customerId, addressId);
        existing.setIsDefault(1);
        addressMapper.updateById(existing);
        clearDefaultExcept(customerId, addressId);
        return true;
    }

    private long countByCustomer(Long customerId) {
        return addressMapper.selectCount(
                Wrappers.<SalesCustomerAddress>lambdaQuery()
                        .eq(SalesCustomerAddress::getCustomerId, customerId));
    }

    private SalesCustomerAddress requireOwned(Long customerId, Long addressId) {
        if (customerId == null || addressId == null) {
            throw new BusinessException("客户ID或地址ID不能为空");
        }
        SalesCustomerAddress address = addressMapper.selectById(addressId);
        if (address == null) {
            throw new BusinessException("收货地址不存在");
        }
        if (!customerId.equals(address.getCustomerId())) {
            throw new BusinessException("收货地址不属于该客户");
        }
        return address;
    }

    private SalesCustomerAddress latestOther(Long customerId, Long excludeId) {
        LambdaQueryWrapper<SalesCustomerAddress> qw = Wrappers.lambdaQuery(SalesCustomerAddress.class);
        qw.eq(SalesCustomerAddress::getCustomerId, customerId)
                .ne(SalesCustomerAddress::getAddressId, excludeId)
                .orderByDesc(SalesCustomerAddress::getAddressId)
                .last("LIMIT 1");
        return addressMapper.selectOne(qw);
    }

    private void clearDefaultExcept(Long customerId, Long keepId) {
        LambdaQueryWrapper<SalesCustomerAddress> qw = Wrappers.lambdaQuery(SalesCustomerAddress.class);
        qw.eq(SalesCustomerAddress::getCustomerId, customerId)
                .ne(SalesCustomerAddress::getAddressId, keepId)
                .eq(SalesCustomerAddress::getIsDefault, 1);
        SalesCustomerAddress patch = new SalesCustomerAddress();
        patch.setIsDefault(0);
        addressMapper.update(patch, qw);
    }

    private void copy(SalesCustomerAddressDTO dto, SalesCustomerAddress entity) {
        Long previousContactId = entity.getContactId();
        String previousPerson = entity.getContactPerson();
        String previousPhone = entity.getContactPhone();
        entity.setContactId(dto.getContactId());
        entity.setLabel(dto.getLabel());
        entity.setContactPerson(dto.getContactPerson());
        entity.setContactPhone(dto.getContactPhone());
        if (dto.getContactId() != null) {
            SalesCustomerContact contact = contactService.requireOwned(entity.getCustomerId(), dto.getContactId());
            if (entity.getAddressId() == null || !Objects.equals(previousContactId, dto.getContactId())
                    || Boolean.TRUE.equals(dto.getRefreshContact())) {
                // 姓名和电话由后端读取，不能伪造其他联系人的快照。
                entity.setContactPerson(contact.getContactPerson());
                entity.setContactPhone(contact.getContactPhone());
            } else {
                // 只改地址时保留原快照；需显式选择更新联系人信息。
                entity.setContactPerson(previousPerson);
                entity.setContactPhone(previousPhone);
            }
        }
        entity.setCountry(dto.getCountry());
        entity.setProvince(dto.getProvince());
        entity.setCity(dto.getCity());
        entity.setAddress(dto.getAddress());
        entity.setPostalCode(dto.getPostalCode());
        entity.setRemark(dto.getRemark());
    }
}
