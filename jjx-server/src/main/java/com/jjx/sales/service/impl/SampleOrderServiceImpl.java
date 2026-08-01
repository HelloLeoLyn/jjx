package com.jjx.sales.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.sales.domain.dto.SalesOrderProductDTO;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.enums.OrderTypeEnum;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.service.ISampleOrderService;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.system.annotation.Event;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 样品单服务实现类
 * 独立于标准订单的样品单生命周期管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleOrderServiceImpl implements ISampleOrderService {

    private final OrderMapper orderMapper;
    private final QuotationMapper quotationMapper;
    private final RedisSequenceService redisSequenceService;
    private final ISalesOrderProductService orderProductService;

    // ============ 状态更新辅助 ============

    /**
     * 安全更新样品单状态（带旧状态校验防并发）
     */
    private void safeTransition(Long orderId, SampleOrderStatusEnum from, SampleOrderStatusEnum to, String actionDesc) {
        int affected = orderMapper.updateSampleStatus(orderId, from.getCode(), to.getCode());
        if (affected == 0) {
            SalesOrder current = orderMapper.selectById(orderId);
            String statusName = current != null && current.getSampleStatus() != null
                    ? SampleOrderStatusEnum.getByCodeSafe(current.getSampleStatus()).map(SampleOrderStatusEnum::getName).orElse("未知")
                    : "未知";
            throw new BusinessException(String.format("样品单状态已变更(当前:%s)，无法%s，请刷新后重试", statusName, actionDesc));
        }
        log.info("样品单[{}] 状态 {} → {}", orderId, from.getName(), to.getName());
    }

    // ============ 核心业务流程 ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder createFromQuotation(Long quotationId, Integer sampleQty, String remark) {
        SalesQuotation quotation = quotationMapper.selectById(quotationId);
        if (quotation == null || quotation.getDeleted() == 1) {
            throw new BusinessException("报价单不存在");
        }

        // 生成订单编号
        String orderNo = redisSequenceService.generateBusinessNumber("SP", "样品单号");

        SalesOrder order = new SalesOrder();
        order.setOrderNo(orderNo);
        order.setQuotationId(quotationId);
        order.setCustomerId(quotation.getCustomerId());
        order.setCustomerName(quotation.getCustomerName());
        order.setContactPerson(quotation.getContactPerson());
        order.setContactPhone(quotation.getContactPhone());
        order.setOrderDate(new Date());
        order.setOrderType(OrderTypeEnum.SAMPLE.getCode());
        order.setSampleStatus(SampleOrderStatusEnum.CREATED.getCode());
        order.setSampleRound(1);
        order.setSampleQty(sampleQty);
        order.setCurrency(quotation.getCurrency());
        order.setExchangeRate(quotation.getExchangeRate());
        order.setSalesManagerId(quotation.getSalesPersonId());
        order.setSalesManagerName(quotation.getSalesPersonName());
        order.setRemark(remark);
        order.setTotalQuantity(sampleQty != null ? sampleQty : 0);
        order.setTotalAmount(quotation.getTotalAmount());
        order.setFinalAmount(quotation.getFinalAmount());
        // 继承报价单链路追踪ID（同一业务链路）
        order.setTraceId(quotation.getTraceId());
        if (quotation.getValidUntil() != null) {
            order.setDeliveryDate(Date.from(quotation.getValidUntil().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }

        orderMapper.insert(order);
        log.info("从报价单[{}]创建样品单[{}] orderId={}", quotation.getQuotationNo(), orderNo, order.getOrderId());
        return order;
    }

    @Override
    @Event(value = "sample.submitted", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder submitReview(Long orderId) {
        safeTransition(orderId,
                SampleOrderStatusEnum.CREATED,
                SampleOrderStatusEnum.PENDING_REVIEW,
                "提交审核");
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.approved", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder approveReview(Long orderId, String remark) {
        safeTransition(orderId,
                SampleOrderStatusEnum.PENDING_REVIEW,
                SampleOrderStatusEnum.ENGINEERING,
                "审核通过");

        // 更新审核信息
        try {
            SalesOrder update = new SalesOrder();
            update.setOrderId(orderId);
            update.setRemark(remark);
            orderMapper.updateById(update);
        } catch (Exception e) {
            log.warn("更新样品单审核备注失败: {}", e.getMessage());
        }

        log.info("样品单[{}] 审核通过，进入工程打样阶段", orderId);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.rejected", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder rejectReview(Long orderId, String remark) {
        safeTransition(orderId,
                SampleOrderStatusEnum.PENDING_REVIEW,
                SampleOrderStatusEnum.REJECTED,
                "审核驳回");
        return orderMapper.selectById(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder startEngineering(Long orderId, String engineeringNote) {
        // 只有 ENGINEERING 状态能设置工程备注
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (!SampleOrderStatusEnum.ENGINEERING.getCode().equals(order.getSampleStatus())) {
            throw new BusinessException("当前状态不可进行工程接单操作");
        }

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setEngineeringNote(engineeringNote);
        orderMapper.updateById(update);

        log.info("样品单[{}] 工程接单，工程备注已记录", orderId);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.ready", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder markSampleReady(Long orderId, Integer sampleQty) {
        safeTransition(orderId,
                SampleOrderStatusEnum.ENGINEERING,
                SampleOrderStatusEnum.SAMPLE_READY,
                "标记样品完成");

        if (sampleQty != null) {
            SalesOrder update = new SalesOrder();
            update.setOrderId(orderId);
            update.setSampleQty(sampleQty);
            orderMapper.updateById(update);
        }

        log.info("样品单[{}] 样品制作完成，待送样", orderId);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.sent", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder sendSample(Long orderId, String trackingNo) {
        safeTransition(orderId,
                SampleOrderStatusEnum.SAMPLE_READY,
                SampleOrderStatusEnum.SAMPLE_SENT,
                "送样");

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setSampleTrackingNo(trackingNo);
        update.setSampleSendDate(new Date());
        orderMapper.updateById(update);

        log.info("样品单[{}] 已送样，快递单号:{}", orderId, trackingNo);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.confirmed", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder confirmSample(Long orderId, String clientName) {
        safeTransition(orderId,
                SampleOrderStatusEnum.SAMPLE_SENT,
                SampleOrderStatusEnum.CONFIRMED,
                "客户确认样品");

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setSampleClientName(clientName);
        update.setSampleConfirmDate(new Date());
        orderMapper.updateById(update);

        log.info("样品单[{}] 客户[{}]已确认样品OK", orderId, clientName);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.rejected_by_customer", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder rejectSample(Long orderId, String rejectReason) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        // 只能从 SAMPLE_SENT 退回（已送样的状态下退回）
        SampleOrderStatusEnum currentStatus = SampleOrderStatusEnum.getByCodeSafe(current.getSampleStatus())
                .orElseThrow(() -> new BusinessException("当前状态不可退回"));

        if (currentStatus != SampleOrderStatusEnum.SAMPLE_SENT) {
            throw new BusinessException("当前状态不可退回，仅已送样待确认的样品可退回");
        }

        orderMapper.updateSampleStatus(orderId, SampleOrderStatusEnum.SAMPLE_SENT.getCode(),
                SampleOrderStatusEnum.REJECTED.getCode());

        // 退回轮次+1，并退回工程阶段
        int nextRound = (current.getSampleRound() != null ? current.getSampleRound() : 1) + 1;
        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setSampleRound(nextRound);
        update.setRemark(rejectReason);
        orderMapper.updateById(update);

        log.info("样品单[{}] 客户退回(Round{})，退回原因:{}", orderId, nextRound, rejectReason);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.converted", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder convertToProduction(Long orderId) {
        SalesOrder sampleOrder = orderMapper.selectById(orderId);
        if (sampleOrder == null || sampleOrder.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        safeTransition(orderId,
                SampleOrderStatusEnum.CONFIRMED,
                SampleOrderStatusEnum.TRANSFERRED,
                "转量产");

        // 生成标准订单
        String standardOrderNo = redisSequenceService.generateBusinessNumber("SO", "标准订单号");

        SalesOrder standardOrder = new SalesOrder();
        standardOrder.setOrderNo(standardOrderNo);
        standardOrder.setQuotationId(sampleOrder.getQuotationId());
        standardOrder.setCustomerId(sampleOrder.getCustomerId());
        standardOrder.setCustomerName(sampleOrder.getCustomerName());
        standardOrder.setContactPerson(sampleOrder.getContactPerson());
        standardOrder.setContactPhone(sampleOrder.getContactPhone());
        standardOrder.setOrderDate(new Date());
        standardOrder.setDeliveryDate(sampleOrder.getDeliveryDate());
        standardOrder.setOrderType(OrderTypeEnum.STANDARD.getCode());
        standardOrder.setOrderStatus(1); // DRAFT
        standardOrder.setCurrency(sampleOrder.getCurrency());
        standardOrder.setExchangeRate(sampleOrder.getExchangeRate());
        standardOrder.setSalesManagerId(sampleOrder.getSalesManagerId());
        standardOrder.setSalesManagerName(sampleOrder.getSalesManagerName());
        standardOrder.setTotalQuantity(sampleOrder.getTotalQuantity());
        standardOrder.setTotalAmount(sampleOrder.getTotalAmount());
        standardOrder.setFinalAmount(sampleOrder.getFinalAmount());
        standardOrder.setRemark("由样品单[" + sampleOrder.getOrderNo() + "]转量产生成");

        orderMapper.insert(standardOrder);

        // 复制产品明细
        copyOrderProducts(sampleOrder.getOrderId(), standardOrder.getOrderId());

        // 更新报价单的 convertedOrderId（如果有报价单关联）
        if (sampleOrder.getQuotationId() != null) {
            try {
                SalesQuotation quotation = quotationMapper.selectById(sampleOrder.getQuotationId());
                if (quotation != null) {
                    quotation.setConvertedOrderId(standardOrder.getOrderId());
                    quotation.setConvertTime(java.time.LocalDateTime.now());
                    quotationMapper.updateById(quotation);
                }
            } catch (Exception e) {
                log.warn("更新报价单转换信息失败: {}", e.getMessage());
            }
        }

        // 回写样品单的 convertedOrderId
        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setConvertedOrderId(standardOrder.getOrderId());
        update.setConvertOrderTime(new Date());
        orderMapper.updateById(update);

        log.info("样品单[{}] 转量产成功，生成标准订单[{}] (orderId={})",
                sampleOrder.getOrderNo(), standardOrderNo, standardOrder.getOrderId());
        return orderMapper.selectById(orderId);
    }

    /**
     * 复制样品单的产品明细到新标准订单
     */
    private void copyOrderProducts(Long sourceOrderId, Long targetOrderId) {
        List<SalesOrderProductDTO> items = new ArrayList<>();
        var productVOs = orderProductService.getListByOrderId(sourceOrderId);

        for (var vo : productVOs) {
            SalesOrderProductDTO dto = new SalesOrderProductDTO();
            dto.setOrderId(targetOrderId);
            dto.setProductId(vo.getProductId());
            dto.setProductCode(vo.getProductCode());
            dto.setProductName(vo.getProductName());
            dto.setQuantity(vo.getQuantity());
            dto.setUnitPrice(vo.getUnitPrice());
            dto.setAmount(vo.getAmount());
            dto.setUnit(vo.getUnit());
            dto.setSpecification(vo.getSpecification());
            dto.setRemark(vo.getRemark());
            items.add(dto);
        }

        if (!items.isEmpty()) {
            orderProductService.batchAdd(items);
            log.info("转量产时复制了 {} 条产品明细到标准订单[{}]", items.size(), targetOrderId);
        }
    }

    @Override
    public SalesOrder selectById(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getDeleted() == 1) {
            throw new BusinessException("样品单不存在或已被删除");
        }
        return order;
    }

    @Override
    public List<SalesOrder> selectSampleList(Long customerId, Integer sampleStatus, Long salesPersonId) {
        return orderMapper.selectSampleOrders();
    }
}
