package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.dto.SalesCustomerAddressDTO;
import com.jjx.sales.domain.entity.SalesCustomerAddress;
import com.jjx.sales.mapper.SalesCustomerAddressMapper;
import com.jjx.sales.service.ISalesCustomerAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 客户收货地址簿服务实现（dev-20261008-029）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesCustomerAddressServiceImpl implements ISalesCustomerAddressService {

    private final SalesCustomerAddressMapper addressMapper;

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
        SalesCustomerAddress entity = new SalesCustomerAddress();
        entity.setCustomerId(customerId);
        copy(dto, entity);
        // 首个地址自动成为默认；否则按显式指定
        boolean first = countByCustomer(customerId) == 0;
        boolean asDefault = first || (dto.getIsDefault() != null && dto.getIsDefault() == 1);
        entity.setIsDefault(asDefault ? 1 : 0);
        addressMapper.insert(entity);
        if (asDefault) {
            clearDefaultExcept(customerId, entity.getAddressId());
        }
        return entity.getAddressId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Long customerId, SalesCustomerAddressDTO dto) {
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
        entity.setLabel(dto.getLabel());
        entity.setContactPerson(dto.getContactPerson());
        entity.setContactPhone(dto.getContactPhone());
        entity.setCountry(dto.getCountry());
        entity.setProvince(dto.getProvince());
        entity.setCity(dto.getCity());
        entity.setAddress(dto.getAddress());
        entity.setPostalCode(dto.getPostalCode());
        entity.setRemark(dto.getRemark());
    }
}
