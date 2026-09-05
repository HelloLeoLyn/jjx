package com.jjx.production.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.enums.ProductionOrderStatusEnum;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.enums.SalesOrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.system.annotation.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 工单开工阶段一事务。
 *
 * <p>独立 Bean 用于确保调用经过 Spring 事务代理；若放回
 * {@link ProductionOrderServiceImpl} 并自调用，{@code @Transactional} 不会生效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionOrderStartTransactionService {

    private final ProductionOrderMapper productionOrderMapper;
    private final OrderMapper salesOrderMapper;

    /**
     * 提交工单及销售订单状态。返回 false 表示工单已经处于进行中，属于幂等成功。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Event(value = "production.started", bizId = "#orderId", bizType = "'production'")
    public boolean startOrder(Long orderId) {
        ProductionOrder order = productionOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }

        if (ProductionOrderStatusEnum.IN_PROGRESS.getValue().equals(order.getOrderStatus())) {
            log.info("生产工单已处于进行中，按幂等成功处理, ID: {}", orderId);
            return false;
        }
        if (!canStartOrder(order)) {
            throw new BusinessException("工单状态不允许启动");
        }

        order.setOrderStatus(ProductionOrderStatusEnum.IN_PROGRESS.getValue());
        order.setActualStartTime(LocalDateTime.now());
        if (productionOrderMapper.updateById(order) <= 0) {
            throw new BusinessException("启动生产工单失败");
        }

        // 状态联动：SO 已审核或已确认（历史订单）→ 生产中；已生产中则幂等跳过。
        updateSalesOrderStatus(order, orderId);
        log.info("生产工单开工状态事务提交就绪, ID: {}", orderId);
        return true;
    }

    private void updateSalesOrderStatus(ProductionOrder order, Long orderId) {
        try {
            if (order.getSalesOrderId() == null) {
                return;
            }
            SalesOrder salesOrder = salesOrderMapper.selectById(order.getSalesOrderId());
            if (salesOrder == null
                    || SalesOrderStatusEnum.IN_PRODUCTION.getValue().equals(salesOrder.getOrderStatus())) {
                return;
            }
            if (SalesOrderStatusEnum.APPROVED.getValue().equals(salesOrder.getOrderStatus())
                    || SalesOrderStatusEnum.CONFIRMED.getValue().equals(salesOrder.getOrderStatus())) {
                int updated = salesOrderMapper.updateStatusWithCheck(order.getSalesOrderId(),
                        SalesOrderStatusEnum.IN_PRODUCTION.getValue(), salesOrder.getOrderStatus());
                if (updated > 0) {
                    log.info("工单{}启动，销售订单{} {}→{}", orderId, order.getSalesOrderNo(),
                            SalesOrderStatusEnum.getByValue(salesOrder.getOrderStatus()).getLabel(),
                            SalesOrderStatusEnum.IN_PRODUCTION.getLabel());
                }
            }
        } catch (Exception e) {
            // 保持原有契约：SO 联动失败不影响工单开工。
            log.warn("工单启动回写销售订单状态失败: {}", e.getMessage());
        }
    }

    private static boolean canStartOrder(ProductionOrder order) {
        Integer status = order.getOrderStatus();
        return ProductionOrderStatusEnum.APPROVED.getValue().equals(status)
                || ProductionOrderStatusEnum.PLANNED.getValue().equals(status);
    }
}
