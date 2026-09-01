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
    private final com.jjx.common.utils.pdf.PdfConfigLoader pdfConfigLoader;

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

    @Override
    public byte[] exportPdf(Long deliveryId) {
        SalesDeliveryVO vo = getById(deliveryId);
        if (vo == null) {
            throw new BusinessException("送货单不存在: " + deliveryId);
        }
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");

        java.util.Map<String, String> info = new java.util.LinkedHashMap<>();
        info.put("送货单号", vo.getDeliveryNo());
        info.put("送货日期", vo.getDeliveryDate() == null ? "" : sdf.format(vo.getDeliveryDate()));
        info.put("客户名称", vo.getCustomerName());
        info.put("收货地址", vo.getDeliveryAddress() == null ? "-" : vo.getDeliveryAddress());
        info.put("联系人", vo.getContactPerson() == null ? "-" : vo.getContactPerson());
        info.put("联系电话", vo.getContactPhone() == null ? "-" : vo.getContactPhone());
        info.put("送货方式", vo.getDeliveryMethod() == null ? "-" : vo.getDeliveryMethod());
        info.put("承运商", vo.getCarrier() == null ? "-" : vo.getCarrier());
        info.put("物流单号", vo.getTrackingNo() == null ? "-" : vo.getTrackingNo());
        info.put("状态", vo.getDeliveryStatusDesc() == null ? "-" : vo.getDeliveryStatusDesc());

        return com.jjx.common.utils.pdf.PdfDocBuilder.create()
                .withConfig(pdfConfigLoader.load())
                .withConfig(pdfConfigLoader.load())
                .title("送  货  单")
                .info(info)
                .items(new String[]{"序号", "项目", "数值"}, new java.util.ArrayList<>())
                .amounts(new String[][]{
                        {"总数量", vo.getTotalQuantity() == null ? "" : String.valueOf(vo.getTotalQuantity())},
                        {"总重量", vo.getTotalWeight() == null ? "" : df.format(vo.getTotalWeight())},
                        {"运费", vo.getFreightAmount() == null ? "" : df.format(vo.getFreightAmount())},
                        {"合计金额", vo.getTotalAmount() == null ? "" : df.format(vo.getTotalAmount())},
                })
                .remark(vo.getRemark())
                .signatures("送货人：" + (vo.getDeliveryPersonName() == null ? "" : vo.getDeliveryPersonName()),
                        "收货人：" + (vo.getReceiverName() == null ? "" : vo.getReceiverName()),
                        "日期：")
                .toBytes();
    }
}
