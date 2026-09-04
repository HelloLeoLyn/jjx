package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.dto.SalesDeliveryQueryDTO;
import com.jjx.sales.domain.entity.SalesDelivery;
import com.jjx.sales.domain.vo.SalesDeliveryVO;
import com.jjx.sales.mapper.SalesDeliveryMapper;
import com.jjx.sales.service.ISalesDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jjx.system.utils.SecurityUtils;

import java.util.List;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * 销售发货单服务实现
 */
@Service
@RequiredArgsConstructor
public class SalesDeliveryServiceImpl implements ISalesDeliveryService {

    private static final String[] DELIVERY_STATUS_DESC = {"未知", "待发货", "已发货", "运输中", "已签收", "已拒收"};

    private final SalesDeliveryMapper salesDeliveryMapper;

    @Override
    public Page<SalesDeliveryVO> pageQuery(SalesDeliveryQueryDTO dto) {
        LambdaQueryWrapper<SalesDelivery> wrapper = new LambdaQueryWrapper<>();
        if (dto.getOrderId() != null) {
            wrapper.eq(SalesDelivery::getOrderId, dto.getOrderId());
        }
        if (dto.getDeliveryNo() != null && !dto.getDeliveryNo().isEmpty()) {
            wrapper.like(SalesDelivery::getDeliveryNo, dto.getDeliveryNo());
        }
        if (dto.getCustomerName() != null && !dto.getCustomerName().isEmpty()) {
            wrapper.like(SalesDelivery::getCustomerName, dto.getCustomerName());
        }
        if (dto.getDeliveryStatus() != null) {
            wrapper.eq(SalesDelivery::getDeliveryStatus, dto.getDeliveryStatus());
        }
        if (dto.getDeliveryDateStart() != null) {
            wrapper.ge(SalesDelivery::getDeliveryDate, dto.getDeliveryDateStart());
        }
        if (dto.getDeliveryDateEnd() != null) {
            wrapper.le(SalesDelivery::getDeliveryDate, dto.getDeliveryDateEnd());
        }
        wrapper.orderByDesc(SalesDelivery::getCreateTime).orderByDesc(SalesDelivery::getDeliveryId);

        Page<SalesDelivery> page = salesDeliveryMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        Page<SalesDeliveryVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public SalesDeliveryVO getById(Long deliveryId) {
        SalesDelivery entity = salesDeliveryMapper.selectById(deliveryId);
        return entity != null ? toVO(entity) : null;
    }

    @Override
    public List<SalesDeliveryVO> listByOrderId(Long orderId) {
        LambdaQueryWrapper<SalesDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SalesDelivery::getOrderId, orderId)
               .orderByDesc(SalesDelivery::getCreateTime);
        return salesDeliveryMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long deliveryId, SalesDelivery receiveInfo) {
        SalesDelivery current = salesDeliveryMapper.selectById(deliveryId);
        if (current == null) {
            throw new BusinessException("发货单不存在");
        }
        if (Integer.valueOf(4).equals(current.getDeliveryStatus())) {
            throw new BusinessException("发货单已签收，请勿重复操作");
        }
        SalesDelivery update = new SalesDelivery();
        update.setDeliveryId(deliveryId);
        update.setReceiverName(receiveInfo == null ? null : receiveInfo.getReceiverName());
        update.setReceiverPhone(receiveInfo == null ? null : receiveInfo.getReceiverPhone());
        update.setReceiveRemark(receiveInfo == null ? null : receiveInfo.getReceiveRemark());
        update.setReceiveTime(new Date());
        update.setReceiveBy(SecurityUtils.getUserId());
        String receiveName = SecurityUtils.getRealName();
        update.setReceiveName(receiveName == null || receiveName.isBlank()
                ? SecurityUtils.getUsername() : receiveName);
        update.setDeliveryStatus(4);
        if (salesDeliveryMapper.updateById(update) <= 0) {
            throw new BusinessException("签收失败，请刷新后重试");
        }
    }

    private SalesDeliveryVO toVO(SalesDelivery entity) {
        SalesDeliveryVO vo = new SalesDeliveryVO();
        BeanUtils.copyProperties(entity, vo);
        int idx = entity.getDeliveryStatus() != null ? entity.getDeliveryStatus() : 0;
        if (idx >= 0 && idx < DELIVERY_STATUS_DESC.length) {
            vo.setDeliveryStatusDesc(DELIVERY_STATUS_DESC[idx]);
        }
        return vo;
    }

}
