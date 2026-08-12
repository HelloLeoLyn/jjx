package com.jjx.production.service.impl;

import com.jjx.event.EventPublisher;
import com.jjx.production.domain.dto.ConvertPlanToWorkOrdersDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import java.util.Map;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.product.mapper.EngineeringRoutingItemMapper;
import com.jjx.engineering.domain.entity.EngineeringRoutingItem;
import java.util.ArrayList;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.converter.ProductionOrderConverter;
import com.jjx.production.domain.dto.ProductionOrderCreateDTO;
import com.jjx.production.domain.dto.ProductionOrderQueryDTO;
import com.jjx.production.domain.dto.ProductionOrderUpdateDTO;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.vo.OrderStatisticsVO;
import com.jjx.production.domain.vo.ProductionOrderVO;
import com.jjx.production.enums.OrderStatusEnum;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.ProductionOrderService;
import com.jjx.system.annotation.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/**
 * 生产工单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionOrderServiceImpl extends ServiceImpl<ProductionOrderMapper, ProductionOrder>
        implements ProductionOrderService {

    private final ProductionOrderMapper productionOrderMapper;
    private final ProductionOrderConverter productionOrderConverter;

    private final ProductionOperationExecutionMapper productionOperationExecutionMapper;
    private final EngineeringRoutingItemMapper productRoutingItemMapper;
    private final EventPublisher eventPublisher;
    private final com.jjx.production.service.QualityInspectionService qualityInspectionService;
    private final com.jjx.production.mapper.ProductionQualityInspectionMapper qualityInspectionMapper;
    private final com.jjx.inventory.service.InventoryInboundService inventoryInboundService;
    private final com.jjx.inventory.service.InventoryOutboundService inventoryOutboundService;
    private final com.jjx.inventory.service.OrderStockReserveService orderStockReserveService;
    private final com.jjx.inventory.service.OrderMaterialReserveService orderMaterialReserveService;
    private final com.jjx.sales.mapper.OrderMapper salesOrderMapper;
    private final com.jjx.common.utils.pdf.PdfConfigLoader pdfConfigLoader;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(ProductionOrderCreateDTO createDTO) {
        log.info("创建生产工单: {}", createDTO);

        // 验证工单编码是否已存在
        if (checkOrderCodeExists(createDTO.getOrderNo())) {
            throw new BusinessException("工单编码已存在: " + createDTO.getOrderNo());
        }

        // 验证数据
        validateOrderData(createDTO);

        // 转换为实体
        ProductionOrder order = productionOrderConverter.toEntity(createDTO);
        // 2026-08-11 规范化：orderType 统一大写（PLAN/WORK_ORDER），避免与前端小写混存
        if (order.getOrderType() != null) {
            order.setOrderType(order.getOrderType().toUpperCase());
        }
        // 链路追踪（DEV-568）：无上游 traceId 则生成 UUID
        if (order.getTraceId() == null || order.getTraceId().isEmpty()) {
            order.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));
        }
        order.setOrderStatus(OrderStatusEnum.DRAFT.getCode());
        // 保存到数据库
        boolean success = save(order);
        if (!success) {
            throw new BusinessException("创建生产工单失败");
        }

        log.info("生产工单创建成功, ID: {}", order.getOrderId());
        return order.getOrderId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOrder(ProductionOrderUpdateDTO updateDTO) {
        log.info("更新生产工单: {}", updateDTO);

        // 检查工单是否存在
        ProductionOrder order = getById(updateDTO.getOrderId());
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + updateDTO.getOrderId());
        }

        // 更新实体
        updateEntityFromUpdateDTO(order, updateDTO);

        // 更新到数据库
        boolean success = updateById(order);
        if (!success) {
            throw new BusinessException("更新生产工单失败");
        }

        log.info("生产工单更新成功, ID: {}", order.getOrderId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteOrder(Long orderId) {
        log.info("删除生产工单: {}", orderId);

        // 检查工单是否存在
        ProductionOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }

        // 检查工单状态，只有特定状态可以删除
        if (!canDeleteOrder(order)) {
            throw new BusinessException("工单状态不允许删除");
        }

        // 删除工单
        boolean success = removeById(orderId);
        if (!success) {
            throw new BusinessException("删除生产工单失败");
        }

        log.info("生产工单删除成功, ID: {}", orderId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeleteOrder(List<Long> orderIds) {
        log.info("批量删除生产工单: {}", orderIds);

        if (orderIds == null || orderIds.isEmpty()) {
            throw new BusinessException("工单ID列表不能为空");
        }

        // 检查所有工单是否存在且状态允许删除
        for (Long orderId : orderIds) {
            ProductionOrder order = getById(orderId);
            if (order == null) {
                throw new BusinessException("生产工单不存在: " + orderId);
            }
            if (!canDeleteOrder(order)) {
                throw new BusinessException("工单状态不允许删除: " + orderId);
            }
        }

        // 批量删除
        boolean success = removeByIds(orderIds);
        if (!success) {
            throw new BusinessException("批量删除生产工单失败");
        }

        log.info("批量删除生产工单成功, 数量: {}", orderIds.size());
        return true;
    }

    @Override
    public ProductionOrderVO getOrderById(Long orderId) {
        log.debug("根据ID获取生产工单详情: {}", orderId);

        ProductionOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }

        return productionOrderConverter.toVO(order);
    }

    @Override
    public ProductionOrderVO getOrderByCode(String orderCode) {
        log.debug("根据编码获取生产工单详情: {}", orderCode);

        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductionOrder::getOrderNo, orderCode);
        ProductionOrder order = getOne(wrapper);

        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderCode);
        }

        return productionOrderConverter.toVO(order);
    }

    @Override
    public List<ProductionOrderVO> queryOrderList(ProductionOrderQueryDTO queryDTO) {
        log.debug("查询生产工单列表: {}", queryDTO);

        LambdaQueryWrapper<ProductionOrder> wrapper = buildQueryWrapper(queryDTO);
        wrapper.orderByDesc(ProductionOrder::getCreateTime);

        List<ProductionOrder> orders = list(wrapper);
        return productionOrderConverter.toVOList(orders);
    }

    @Override
    public Page<ProductionOrderVO> queryOrderPage(ProductionOrderQueryDTO queryDTO) {
        log.debug("分页查询生产工单: {}", queryDTO);

        // 构建查询条件
        LambdaQueryWrapper<ProductionOrder> wrapper = buildQueryWrapper(queryDTO);

        // 设置排序
        if (StringUtils.isNotBlank(queryDTO.getOrderBy())) {
            if ("desc".equalsIgnoreCase(queryDTO.getOrderDirection())) {
                wrapper.orderByDesc(getOrderColumn(queryDTO.getOrderBy()));
            } else {
                wrapper.orderByAsc(getOrderColumn(queryDTO.getOrderBy()));
            }
        } else {
            wrapper.orderByDesc(ProductionOrder::getCreateTime);
        }

        // 分页查询
        Page<ProductionOrder> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<ProductionOrder> orderPage = page(page, wrapper);

        // 转换为VO分页
        Page<ProductionOrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        List<ProductionOrderVO> voList = productionOrderConverter.toVOList(orderPage.getRecords());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "production.started", bizId = "#orderId", bizType = "'production'")
    public boolean startOrder(Long orderId) {
        log.info("启动生产工单: {}", orderId);

        ProductionOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }

        // 检查工单状态是否可以启动
        if (!canStartOrder(order)) {
            throw new BusinessException("工单状态不允许启动");
        }

        // 更新状态为进行中
        order.setOrderStatus(OrderStatusEnum.IN_PROGRESS.getCode());
        order.setActualStartTime(LocalDateTime.now());

        boolean success = updateById(order);
        if (!success) {
            throw new BusinessException("启动生产工单失败");
        }

        // 生产领料自动出库（DEV-625）：开工时按 BOM 自动生成领料出库单，幂等（已存在跳过）
        try {
            inventoryOutboundService.createFromProduction(orderId);
        } catch (Exception e) {
            log.error("生产领料自动出库失败（不影响开工主流程）: {}", e.getMessage());
        }

        // 状态联动（2026-08-11）：工单启动 → 销售订单 已确认(6)→生产中(7)
        try {
            if (order.getSalesOrderId() != null) {
                com.jjx.sales.domain.entity.SalesOrder so = salesOrderMapper.selectById(order.getSalesOrderId());
                if (so != null && com.jjx.sales.enums.OrderStatusEnum.CONFIRMED.getCode().equals(so.getOrderStatus())) {
                    int up = salesOrderMapper.updateStatusWithCheck(order.getSalesOrderId(),
                            com.jjx.sales.enums.OrderStatusEnum.IN_PRODUCTION.getCode(),
                            com.jjx.sales.enums.OrderStatusEnum.CONFIRMED.getCode());
                    if (up > 0) {
                        log.info("工单{}启动，销售订单{} 已确认(6)→生产中(7)", orderId, order.getSalesOrderNo());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("工单启动回写销售订单状态失败: {}", e.getMessage());
        }

        log.info("生产工单启动成功, ID: {}", orderId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean pauseOrder(Long orderId) {
        log.info("暂停生产工单: {}", orderId);

        ProductionOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }

        // 检查工单状态是否可以暂停
        if (!canPauseOrder(order)) {
            throw new BusinessException("工单状态不允许暂停");
        }

        // 更新状态为暂停
        order.setOrderStatus(OrderStatusEnum.PAUSED.getCode());

        boolean success = updateById(order);
        if (!success) {
            throw new BusinessException("暂停生产工单失败");
        }

        log.info("生产工单暂停成功, ID: {}", orderId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long retryInbound(Long orderId) {
        log.info("重试完工入库: {}", orderId);
        ProductionOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }
        // 056定稿：重试→成功→产品库存+→produced_quantity回写→标记清除
        Long inboundId = inventoryInboundService.createFromProduction(orderId);
        if (inboundId == null) {
            // 入库单已存在（幂等返回null）→ 视为已处理，清除标记
            order.setInboundPendingFlag(0);
            order.setInboundPendingReason(null);
            updateById(order);
            log.info("重试入库：入库单已存在（幂等），清除待处理标记 orderId={}", orderId);
            return null;
        }
        // 成功：清除标记
        order.setInboundPendingFlag(0);
        order.setInboundPendingReason(null);
        updateById(order);
        log.info("重试完工入库成功: orderId={}, inboundId={}", orderId, inboundId);
        return inboundId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeOrder(Long orderId) {
        log.info("完成生产工单: {}", orderId);

        ProductionOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }

        // 检查工单状态是否可以完成（053完工质检门：状态/工序/质检/数量四条件）
        if (!canCompleteOrder(order)) {
            throw new BusinessException("完工校验不通过：工单需为进行中、全部工序已完成、FQC质检通过且成品完工数量>0（最后一道工序合格数）");
        }

        // 更新状态为已完成
        order.setOrderStatus(OrderStatusEnum.COMPLETED.getCode());
        order.setActualEndTime(LocalDateTime.now());
        order.setCompletedBy(com.jjx.system.utils.SecurityUtils.getUsername()); // 053完工留痕：谁

        // 059定稿：完工自动核算人工成本 = Σ(工序实际工时 × 标准工价)
        try {
            java.math.BigDecimal laborTotal = java.math.BigDecimal.ZERO;
            java.util.List<ProductionOperationExecution> executions = productionOperationExecutionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionOperationExecution>()
                            .eq(ProductionOperationExecution::getOrderId, orderId));
            for (ProductionOperationExecution exec : executions) {
                if (exec.getActualLaborHours() == null || exec.getActualLaborHours().compareTo(java.math.BigDecimal.ZERO) <= 0) continue;
                // 取该工序标准工价（工艺路线工序）
                java.math.BigDecimal wage = java.math.BigDecimal.ZERO;
                if (exec.getProcessId() != null) {
                    try {
                        com.jjx.engineering.domain.entity.EngineeringRoutingItem routeItem =
                                productRoutingItemMapper.selectOne(
                                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringRoutingItem>()
                                                .eq(com.jjx.engineering.domain.entity.EngineeringRoutingItem::getRoutingId, order.getRoutingId())
                                                .eq(com.jjx.engineering.domain.entity.EngineeringRoutingItem::getProcessId, exec.getProcessId())
                                                .last("LIMIT 1"));
                        if (routeItem != null && routeItem.getStandardWage() != null) {
                            wage = routeItem.getStandardWage();
                        }
                    } catch (Exception e) {
                        log.warn("查询工序工价失败: processId={}", exec.getProcessId());
                    }
                }
                laborTotal = laborTotal.add(exec.getActualLaborHours().multiply(wage));
            }
            if (laborTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                order.setLaborCost(laborTotal);
                log.info("工单{}完工自动核算人工成本: {}", orderId, laborTotal);
            }
        } catch (Exception e) {
            log.warn("工单人工成本自动核算失败（可手工调整）: {}", e.getMessage());
        }

        boolean success = updateById(order);
        if (!success) {
            throw new BusinessException("完成生产工单失败");
        }

        // 完工自动创建质检单（DEV-473：TC-56 联动）
        try {
            com.jjx.production.domain.dto.QualityInspectionCreateDTO qcDto =
                    new com.jjx.production.domain.dto.QualityInspectionCreateDTO();
            qcDto.setInspectionType("FQC"); // 完工质检
            qcDto.setOrderId(orderId);
            qcDto.setProductId(order.getProductId());
            qcDto.setInspector(com.jjx.system.utils.SecurityUtils.getUsername());
            qcDto.setRemark("工单完工自动创建质检单");
            Long qcId = qualityInspectionService.create(qcDto);
            log.info("工单[{}] 完工自动创建质检单[{}]", order.getOrderNo(), qcId);
            // 053完工留痕：关联质检单号
            if (qcId != null) {
                ProductionOrder updateOrder = new ProductionOrder();
                updateOrder.setOrderId(orderId);
                updateOrder.setQualityInspectionId(qcId);
                productionOrderMapper.updateById(updateOrder);
            }
        } catch (Exception e) {
            log.warn("完工自动创建质检单失败（不影响主流程）: {}", e.getMessage());
        }

        // 完工自动生成成品入库单（DEV-579：拍板4 生产完成→自动生成成品入库单，走入库流程）
        try {
            Long inboundId = inventoryInboundService.createFromProduction(orderId);
            if (inboundId != null) {
                log.info("工单[{}] 完工自动生成成品入库单[{}]", order.getOrderNo(), inboundId);
                // 056：入库成功 → 清除待处理标记
                if (order.getInboundPendingFlag() != null && order.getInboundPendingFlag() == 1) {
                    order.setInboundPendingFlag(0);
                    order.setInboundPendingReason(null);
                    updateById(order);
                }
            }
        } catch (Exception e) {
            // 056定稿：入库失败不静默——打【入库待处理】标记（标红），带失败原因，供工单页重试
            log.warn("完工自动生成成品入库单失败（已打入库待处理标记，可重试）: {}", e.getMessage());
            try {
                ProductionOrder mark = new ProductionOrder();
                mark.setOrderId(orderId);
                mark.setInboundPendingFlag(1);
                mark.setInboundPendingReason(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "未知原因");
                productionOrderMapper.updateById(mark);
            } catch (Exception e2) {
                log.warn("打入库待处理标记失败: {}", e2.getMessage());
            }
        }

        // 触发联动事件
        try {
            eventPublisher.fire("production.completed", Map.of(
                    "orderNo", order.getOrderNo(),
                    "productId", String.valueOf(order.getProductId()),
                    "productName", order.getProductName(),
                    "quantity", order.getCompletedQuantity() != null ? order.getCompletedQuantity().toString() : "0",
                    "orderId", String.valueOf(orderId)
            ));
        } catch (Exception e) {
            log.warn("事件联动失败（不影响主流程）: {}", e.getMessage());
        }

        log.info("生产工单完成成功, ID: {}", orderId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId) {
        log.info("取消生产工单: {}", orderId);

        ProductionOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }

        // 检查工单状态是否可以取消
        if (!canCancelOrder(order)) {
            throw new BusinessException("工单状态不允许取消");
        }

        // 095：取消工单部分完工入库（产品维度，入 product_stock 表）
        // 已完工合格品（最后一道工序合格数 finishedQuantity>0）→ 自动部分完工入库，产品库存+
        try {
            BigDecimal finishedQty = order.getFinishedQuantity() != null ? order.getFinishedQuantity() : BigDecimal.ZERO;
            if (finishedQty.compareTo(BigDecimal.ZERO) > 0) {
                Long inboundId = inventoryInboundService.createFromProduction(orderId);
                if (inboundId != null) {
                    log.info("取消工单{}自动部分完工入库[{}]（产品入库，数量={}）", orderId, inboundId, finishedQty);
                }
            }
        } catch (Exception e) {
            log.warn("取消工单部分完工入库失败（不影响取消主流程，可手动重试）: {}", e.getMessage());
        }

        // 更新状态为已取消
        order.setOrderStatus(OrderStatusEnum.CANCELLED.getCode());

        boolean success = updateById(order);
        if (!success) {
            throw new BusinessException("取消生产工单失败");
        }

        log.info("生产工单取消成功, ID: {}", orderId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int[] cancelBySalesOrderId(Long salesOrderId) {
        List<ProductionOrder> orders = productionOrderMapper.selectList(
                new LambdaQueryWrapper<ProductionOrder>()
                        .eq(ProductionOrder::getSalesOrderId, salesOrderId));
        if (orders == null || orders.isEmpty()) {
            return new int[]{0, 0};
        }

        int cancelled = 0;
        int skipped = 0;
        for (ProductionOrder order : orders) {
            if (canCancelOrder(order)) {
                order.setOrderStatus(OrderStatusEnum.CANCELLED.getCode());
                updateById(order);
                cancelled++;
                log.info("销售订单{}取消联动：生产工单{}已取消", salesOrderId, order.getOrderId());
            } else {
                skipped++;
                log.info("销售订单{}取消联动：生产工单{}状态[{}]不可取消，跳过",
                        salesOrderId, order.getOrderId(), order.getOrderStatus());
            }
        }
        // 095⑧定稿：全部工单已取消（无跳过）→ 订单 7生产中 自动回退 6已确认，释放成品预留+原料占用/预占
        if (cancelled > 0 && skipped == 0) {
            try {
                com.jjx.sales.domain.entity.SalesOrder salesOrder = salesOrderMapper.selectById(salesOrderId);
                if (salesOrder != null && com.jjx.sales.enums.OrderStatusEnum.IN_PRODUCTION.getCode()
                        .equals(salesOrder.getOrderStatus())) {
                    int updated = salesOrderMapper.updateStatusWithCheck(
                            salesOrderId,
                            com.jjx.sales.enums.OrderStatusEnum.CONFIRMED.getCode(),
                            com.jjx.sales.enums.OrderStatusEnum.IN_PRODUCTION.getCode());
                    if (updated > 0) {
                        log.info("全部工单已取消，订单{}自动回退：生产中(7)→已确认(6)", salesOrderId);
                        // 释放成品预留 + 材料预占
                        try {
                            orderStockReserveService.releaseByOrder(salesOrderId);
                        } catch (Exception e) {
                            log.warn("订单回退释放成品预留失败: {}", e.getMessage());
                        }
                        try {
                            orderMaterialReserveService.releaseByOrder(salesOrderId, "全部工单取消，订单回退释放材料预占");
                        } catch (Exception e) {
                            log.warn("订单回退释放材料预占失败: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("订单回退联动失败（不影响工单取消）: {}", e.getMessage());
            }
        }
        return new int[]{cancelled, skipped};
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeOrder(Long orderId) {
        log.info("关闭生产工单: {}", orderId);

        ProductionOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }

        // 检查工单状态是否可以关闭
        if (!canCloseOrder(order)) {
            throw new BusinessException("工单状态不允许关闭");
        }

        // 更新状态为已关闭
        order.setOrderStatus(OrderStatusEnum.CLOSED.getCode());

        boolean success = updateById(order);
        if (!success) {
            throw new BusinessException("关闭生产工单失败");
        }

        log.info("生产工单关闭成功, ID: {}", orderId);
        return true;
    }

    @Override
    public boolean checkOrderCodeExists(String orderCode) {
        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductionOrder::getOrderNo, orderCode);
        return count(wrapper) > 0;
    }

    @Override
    public List<ProductionOrderVO> getOrdersByProductId(Long productId) {
        log.debug("根据产品ID查询生产工单: {}", productId);

        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductionOrder::getProductId, productId)
                .orderByDesc(ProductionOrder::getCreateTime);

        List<ProductionOrder> orders = list(wrapper);
        return productionOrderConverter.toVOList(orders);
    }

    @Override
    public List<ProductionOrderVO> getOrdersByRoutingId(Long routingId) {
        log.debug("根据工艺路线ID查询生产工单: {}", routingId);

        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductionOrder::getRoutingId, routingId)
                .orderByDesc(ProductionOrder::getCreateTime);

        List<ProductionOrder> orders = list(wrapper);
        return productionOrderConverter.toVOList(orders);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyOrder(Long sourceOrderId, String targetOrderCode, String targetOrderName) {
        log.info("复制生产工单: 源ID={}, 目标编码={}, 目标名称={}", sourceOrderId, targetOrderCode, targetOrderName);

        // 检查源工单是否存在
        ProductionOrder sourceOrder = getById(sourceOrderId);
        if (sourceOrder == null) {
            throw new BusinessException("源生产工单不存在: " + sourceOrderId);
        }

        // 检查目标工单编码是否已存在
        if (checkOrderCodeExists(targetOrderCode)) {
            throw new BusinessException("目标工单编码已存在: " + targetOrderCode);
        }

        // 复制工单
        ProductionOrder targetOrder = copyEntity(sourceOrder);
        targetOrder.setOrderId(null);
        targetOrder.setOrderNo(targetOrderCode);
        targetOrder.setProductName(targetOrderName);
        targetOrder.setOrderStatus(OrderStatusEnum.DRAFT.getCode()); // 新工单状态为草稿
        targetOrder.setCreateTime(null);
        targetOrder.setUpdateTime(null);
        targetOrder.setActualStartTime(null);
        targetOrder.setActualEndTime(null);

        // 保存新工单
        boolean success = save(targetOrder);
        if (!success) {
            throw new BusinessException("复制生产工单失败");
        }

        log.info("生产工单复制成功, 新工单ID: {}", targetOrder.getOrderId());
        return targetOrder.getOrderId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result importOrderData(List<ProductionOrderCreateDTO> importData) {
        log.info("导入生产工单数据, 数量: {}", importData.size());

        if (importData == null || importData.isEmpty()) {
            return Result.error("导入数据不能为空");
        }

        int successCount = 0;
        int failCount = 0;
        List<String> failMessages = new java.util.ArrayList<>();

        for (int i = 0; i < importData.size(); i++) {
            ProductionOrderCreateDTO dto = importData.get(i);
            try {
                // 检查工单编码是否已存在
//                if (checkOrderCodeExists(dto.getOrderNo())) {
//                    throw new ServiceException("工单编码已存在: " + dto.getOrderNo());
//                }

                // 验证数据
                validateOrderData(dto);

                // 转换为实体并保存
                ProductionOrder order = productionOrderConverter.toEntity(dto);
                order.setOrderStatus(OrderStatusEnum.DRAFT.getCode());
                save(order);

                successCount++;
            } catch (Exception e) {
                failCount++;
                String message = String.format("第%d行导入失败: %s", i + 1, e.getMessage());
                failMessages.add(message);
                log.error(message, e);
            }
        }

        String resultMessage = String.format("导入完成: 成功%d条, 失败%d条", successCount, failCount);
        log.info(resultMessage);

        if (failCount > 0) {
            // 创建一个包含失败消息的Map作为data
            java.util.Map<String, Object> resultData = new java.util.HashMap<>();
            resultData.put("successCount", successCount);
            resultData.put("failCount", failCount);
            resultData.put("failMessages", failMessages);
            Result<Object> result = Result.error(resultMessage);
            result.setData(resultData);
            return result;
        } else {
            return Result.success(resultMessage);
        }
    }

    @Override
    public List<ProductionOrderVO> exportOrderData(ProductionOrderQueryDTO queryDTO) {
        log.debug("导出生产工单数据: {}", queryDTO);

        LambdaQueryWrapper<ProductionOrder> wrapper = buildQueryWrapper(queryDTO);
        wrapper.orderByDesc(ProductionOrder::getCreateTime);

        List<ProductionOrder> orders = list(wrapper);
        return productionOrderConverter.toVOList(orders);
    }

    @Override
    public OrderStatisticsVO getOrderStatistics(ProductionOrderQueryDTO queryDTO) {
        log.debug("获取生产工单统计信息: {}", queryDTO);

        // 构建查询条件
        LambdaQueryWrapper<ProductionOrder> wrapper = buildQueryWrapper(queryDTO);

        // 获取统计数据
        long totalCount = count(wrapper);

        // 按状态统计
        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOrder::getOrderStatus, OrderStatusEnum.DRAFT.getCode());
        long draftCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOrder::getOrderStatus, OrderStatusEnum.PENDING_APPROVAL.getCode());
        long pendingApprovalCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOrder::getOrderStatus, OrderStatusEnum.APPROVED.getCode());
        long approvedCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOrder::getOrderStatus, OrderStatusEnum.PLANNED.getCode());
        long scheduledCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOrder::getOrderStatus, OrderStatusEnum.IN_PROGRESS.getCode());
        long inProgressCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOrder::getOrderStatus, OrderStatusEnum.COMPLETED.getCode());
        long completedCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOrder::getOrderStatus, OrderStatusEnum.CANCELLED.getCode());
        long cancelledCount = count(wrapper);

        // 构建统计结果
        OrderStatisticsVO orderStatistics = new OrderStatisticsVO();
        orderStatistics.setTotalCount(totalCount);
        orderStatistics.setDraftCount(draftCount);
        orderStatistics.setPendingApprovalCount(pendingApprovalCount);
        orderStatistics.setApprovedCount(approvedCount);
        orderStatistics.setScheduledCount(scheduledCount);
        orderStatistics.setInProgressCount(inProgressCount);
        orderStatistics.setCompletedCount(completedCount);
        orderStatistics.setCancelledCount(cancelledCount);
        return orderStatistics;
    }

    // ============ 私有方法 ============

    /**
     * 构建查询条件
     */
    private static LambdaQueryWrapper<ProductionOrder> buildQueryWrapper(ProductionOrderQueryDTO queryDTO) {
        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(queryDTO.getOrderNo())) {
            wrapper.like(ProductionOrder::getOrderNo, queryDTO.getOrderNo());
        }
        if (StringUtils.isNotBlank(queryDTO.getProductName())) {
            wrapper.like(ProductionOrder::getProductName, queryDTO.getProductName());
        }
        if (queryDTO.getProductId() != null) {
            wrapper.eq(ProductionOrder::getProductId, queryDTO.getProductId());
        }
        if (queryDTO.getRoutingId() != null) {
            wrapper.eq(ProductionOrder::getRoutingId, queryDTO.getRoutingId());
        }
        if (queryDTO.getOrderStatus() != null) {
            wrapper.eq(ProductionOrder::getOrderStatus, queryDTO.getOrderStatus());
        }
        // 2026-08-11 修复：orderType=all 表示"全部"，不得作为过滤值（否则全部视图永远查不到数据）
        if (StringUtils.isNotBlank(queryDTO.getOrderType()) && !"all".equalsIgnoreCase(queryDTO.getOrderType())) {
            wrapper.eq(ProductionOrder::getOrderType, queryDTO.getOrderType());
        }

        return wrapper;
    }

    /**
     * 获取排序字段
     */
    private static com.baomidou.mybatisplus.core.toolkit.support.SFunction<ProductionOrder, ?> getOrderColumn(String orderBy) {
        switch (orderBy) {
            case "orderNo":
                return ProductionOrder::getOrderNo;
            case "productName":
                return ProductionOrder::getProductName;
            case "productId":
                return ProductionOrder::getProductId;
            case "orderStatus":
                return ProductionOrder::getOrderStatus;
            case "createTime":
                return ProductionOrder::getCreateTime;
            case "updateTime":
                return ProductionOrder::getUpdateTime;
            default:
                return ProductionOrder::getCreateTime;
        }
    }

    /**
     * 验证工单数据
     */
    private static void validateOrderData(ProductionOrderCreateDTO createDTO) {
//        if (StringUtils.isBlank(createDTO.getOrderNo())) {
//            throw new ServiceException("工单编码不能为空");
//        }
        if (StringUtils.isBlank(createDTO.getProductName())) {
            throw new BusinessException("产品名称不能为空");
        }
        if (createDTO.getProductId() == null) {
            throw new BusinessException("产品ID不能为空");
        }
        if (createDTO.getPlannedQuantity() == null || createDTO.getPlannedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("计划数量必须大于0");
        }
    }

    /**
     * 检查工单是否可以删除
     */
    private static boolean canDeleteOrder(ProductionOrder order) {
        // 只有草稿和已取消状态的工单可以删除
        Integer status = order.getOrderStatus();
        return OrderStatusEnum.DRAFT.getCode().equals(status) || OrderStatusEnum.CANCELLED.getCode().equals(status);
    }

    /**
     * 检查工单是否可以开始
     */
    private static boolean canStartOrder(ProductionOrder order) {
        // 只有已批准和已排程状态的工单可以开始
        Integer status = order.getOrderStatus();
        return OrderStatusEnum.APPROVED.getCode().equals(status) || OrderStatusEnum.PLANNED.getCode().equals(status);
    }

    /**
     * 检查工单是否可以暂停
     */
    private static boolean canPauseOrder(ProductionOrder order) {
        // 只有进行中状态的工单可以暂停
        return OrderStatusEnum.IN_PROGRESS.getCode().equals(order.getOrderStatus());
    }

    /**
     * 完工质检门（053定稿）：工单完工必须过质检门
     * ① 工单状态=进行中
     * ② 全部工序已完成（执行状态=COMPLETED/SKIPPED）
     * ③ FQC质检通过（存在 result=pass 的完工质检）
     * ④ 成品完工数量达标（finishedQuantity>0，以最后工序合格数为准，052口径）
     * 任一不满足拒绝完工；调用方拿失败原因提示用户
     */
    private boolean canCompleteOrder(ProductionOrder order) {
        // ① 状态=进行中
        if (!OrderStatusEnum.IN_PROGRESS.getCode().equals(order.getOrderStatus())) {
            log.warn("完工质检门[1/4]失败：工单{}状态不是进行中", order.getOrderId());
            return false;
        }
        // ② 全部工序已完成
        try {
            Long pendingCount = productionOperationExecutionMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.production.domain.entity.ProductionOperationExecution>()
                            .eq(com.jjx.production.domain.entity.ProductionOperationExecution::getOrderId, order.getOrderId())
                            .notIn(com.jjx.production.domain.entity.ProductionOperationExecution::getExecutionStatus,
                                    com.jjx.production.enums.ExecutionStatusEnum.COMPLETED.getCode(),
                                    com.jjx.production.enums.ExecutionStatusEnum.SKIPPED.getCode()));
            if (pendingCount != null && pendingCount > 0) {
                log.warn("完工质检门[2/4]失败：工单{}还有{}道工序未完成", order.getOrderId(), pendingCount);
                return false;
            }
        } catch (Exception e) {
            log.warn("完工质检门[2/4]查询工序失败(不阻断，按通过处理): {}", e.getMessage());
        }
        // ③ FQC质检通过（允许无工序/无质检数据时按通过处理，兼容旧数据）
        try {
            Long fqcPass = qualityInspectionMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.production.domain.entity.ProductionQualityInspection>()
                            .eq(com.jjx.production.domain.entity.ProductionQualityInspection::getOrderId, order.getOrderId())
                            .eq(com.jjx.production.domain.entity.ProductionQualityInspection::getInspectionType, "FQC")
                            .eq(com.jjx.production.domain.entity.ProductionQualityInspection::getResult, "pass"));
            if (fqcPass == null || fqcPass <= 0) {
                log.warn("完工质检门[3/4]失败：工单{}无FQC质检通过记录", order.getOrderId());
                return false;
            }
        } catch (Exception e) {
            log.warn("完工质检门[3/4]查询质检失败(不阻断，按通过处理): {}", e.getMessage());
        }
        // ④ 成品完工数量达标（finishedQuantity>0）
        if (order.getFinishedQuantity() == null || order.getFinishedQuantity().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            log.warn("完工质检门[4/4]失败：工单{}成品完工数量为0", order.getOrderId());
            return false;
        }
        return true;
    }

    /**
     * 检查工单是否可以取消
     */
    private static boolean canCancelOrder(ProductionOrder order) {
        // 草稿、待审批、已批准、已计划、待开始、进行中、已暂停状态的工单可以取消
        Integer status = order.getOrderStatus();
        return OrderStatusEnum.DRAFT.getCode().equals(status) ||
               OrderStatusEnum.PENDING_APPROVAL.getCode().equals(status) ||
               OrderStatusEnum.APPROVED.getCode().equals(status) ||
               OrderStatusEnum.PLANNED.getCode().equals(status) ||
               OrderStatusEnum.PENDING_START.getCode().equals(status) ||
               OrderStatusEnum.IN_PROGRESS.getCode().equals(status) ||
               OrderStatusEnum.PAUSED.getCode().equals(status);
    }

    /**
     * 检查工单是否可以关闭
     */
    private static boolean canCloseOrder(ProductionOrder order) {
        // 只有已完成和已取消状态的工单可以关闭
        Integer status = order.getOrderStatus();
        return OrderStatusEnum.COMPLETED.getCode().equals(status) || OrderStatusEnum.CANCELLED.getCode().equals(status);
    }


    /**
     * 从UpdateDTO更新实体
     */
    private static void updateEntityFromUpdateDTO(ProductionOrder order, ProductionOrderUpdateDTO updateDTO) {

        if (updateDTO.getSalesOrderId() != null) {
            order.setSalesOrderId(updateDTO.getSalesOrderId());
        }
        if (updateDTO.getSalesOrderNo() != null) {
            order.setSalesOrderNo(updateDTO.getSalesOrderNo());
        }
        if (updateDTO.getProductId() != null) {
            order.setProductId(updateDTO.getProductId());
        }
        if (updateDTO.getProductCode() != null) {
            order.setProductCode(updateDTO.getProductCode());
        }
        if (updateDTO.getProductName() != null) {
            order.setProductName(updateDTO.getProductName());
        }
        if (updateDTO.getProductSpec() != null) {
            order.setProductSpec(updateDTO.getProductSpec());
        }
        if (updateDTO.getProductUnit() != null) {
            order.setProductUnit(updateDTO.getProductUnit());
        }
        if (updateDTO.getRoutingId() != null) {
            order.setRoutingId(updateDTO.getRoutingId());
        }
        if (updateDTO.getRoutingCode() != null) {
            order.setRoutingCode(updateDTO.getRoutingCode());
        }
        if (updateDTO.getPlannedQuantity() != null) {
            order.setPlannedQuantity(updateDTO.getPlannedQuantity());
        }
        if (updateDTO.getPlanStartDate() != null) {
            order.setPlanStartDate(updateDTO.getPlanStartDate());
        }
        if (updateDTO.getPlanEndDate() != null) {
            order.setPlanEndDate(updateDTO.getPlanEndDate());
        }
        if (updateDTO.getOrderStatus() != null) {
            order.setOrderStatus(updateDTO.getOrderStatus());
        }
        if (updateDTO.getApprovalStatus() != null) {
            order.setApprovalStatus(updateDTO.getApprovalStatus());
        }
        if (updateDTO.getApproverId() != null) {
            order.setApproverId(updateDTO.getApproverId());
        }
        if (updateDTO.getApproverName() != null) {
            order.setApproverName(updateDTO.getApproverName());
        }

        if (updateDTO.getApprovalRemark() != null) {
            order.setApprovalRemark(updateDTO.getApprovalRemark());
        }
        if (updateDTO.getPriority() != null) {
            order.setPriority(updateDTO.getPriority());
        }
        if (updateDTO.getDepartmentId() != null) {
            order.setDepartmentId(updateDTO.getDepartmentId());
        }
        if (updateDTO.getDepartmentName() != null) {
            order.setDepartmentName(updateDTO.getDepartmentName());
        }
        if (updateDTO.getMaterialCost() != null) {
            order.setMaterialCost(updateDTO.getMaterialCost());
        }
        if (updateDTO.getLaborCost() != null) {
            order.setLaborCost(updateDTO.getLaborCost());
        }
        if (updateDTO.getTotalCost() != null) {
            order.setTotalCost(updateDTO.getTotalCost());
        }
        if (updateDTO.getRemark() != null) {
            order.setRemark(updateDTO.getRemark());
        }
        order.setUpdateTime(LocalDateTime.now());
    }

    /**
     * 复制实体
     */
    private static ProductionOrder copyEntity(ProductionOrder source) {
        ProductionOrder target = new ProductionOrder();

        target.setOrderNo(source.getOrderNo());
        target.setOrderType(source.getOrderType());
        target.setParentOrderId(source.getParentOrderId());
        target.setSalesOrderId(source.getSalesOrderId());
        target.setSalesOrderNo(source.getSalesOrderNo());
        target.setProductId(source.getProductId());
        target.setProductCode(source.getProductCode());
        target.setProductName(source.getProductName());
        target.setProductSpec(source.getProductSpec());
        target.setProductUnit(source.getProductUnit());
        target.setRoutingId(source.getRoutingId());
        target.setRoutingCode(source.getRoutingCode());
        target.setPlannedQuantity(source.getPlannedQuantity());
        target.setPlanStartDate(source.getPlanStartDate());
        target.setPlanEndDate(source.getPlanEndDate());
        target.setOrderStatus(source.getOrderStatus());
        target.setApprovalStatus(source.getApprovalStatus());
        target.setPriority(source.getPriority());
        target.setDepartmentId(source.getDepartmentId());
        target.setDepartmentName(source.getDepartmentName());
        target.setRemark(source.getRemark());

        return target;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> convertPlanToWorkOrders(ConvertPlanToWorkOrdersDTO dto) {
        log.info("计划转工单: planId={}, count={}", dto.getPlanId(), dto.getWorkOrders().size());

        // 校验计划是否存在
        ProductionOrder plan = getById(dto.getPlanId());
        if (plan == null) {
            throw new BusinessException("生产计划不存在: " + dto.getPlanId());
        }
        if (!"PLAN".equals(plan.getOrderType())) {
            throw new BusinessException("订单不是生产计划类型，无法转换: " + plan.getOrderNo());
        }
        // 039⑤：仅已批准计划可转（2026-08-11 统一：审批动作只维护 orderStatus，2=已批准）
        if (plan.getOrderStatus() == null || plan.getOrderStatus() != 2) {
            throw new BusinessException("仅已审批通过的生产计划可转为工单，当前状态: " + (plan.getOrderStatus() == null ? "未知" : plan.getOrderStatus()));
        }
        // 039⑤：Σ子工单数量 ≤ 计划数量（防超量）
        BigDecimal planQty = plan.getPlannedQuantity() != null ? plan.getPlannedQuantity() : BigDecimal.ZERO;
        BigDecimal sumQty = BigDecimal.ZERO;
        for (ConvertPlanToWorkOrdersDTO.WorkOrderItem item : dto.getWorkOrders()) {
            if (item.getPlannedQuantity() != null) {
                sumQty = sumQty.add(item.getPlannedQuantity());
            }
        }
        if (sumQty.compareTo(planQty) > 0) {
            throw new BusinessException("子工单数量合计" + sumQty.stripTrailingZeros().toPlainString()
                    + "超过计划数量" + planQty.stripTrailingZeros().toPlainString() + "，请调整");
        }
        // 039⑤：无BOM/工艺路线产品拦截
        if (plan.getBomId() == null && plan.getRoutingId() == null) {
            throw new BusinessException("计划无BOM/工艺路线，无法转为工单");
        }

        List<Long> createdOrderIds = new ArrayList<>();
        int seq = 0;

        for (ConvertPlanToWorkOrdersDTO.WorkOrderItem item : dto.getWorkOrders()) {
            seq++;

            // 生成工单编号
            String workOrderNo = generateWorkOrderNo(plan, seq);

            // 创建工单
            ProductionOrder workOrder = new ProductionOrder();
            workOrder.setOrderNo(workOrderNo);
            workOrder.setOrderType("WORK_ORDER");
            workOrder.setParentOrderId(plan.getOrderId());
            workOrder.setSalesOrderId(plan.getSalesOrderId());
            workOrder.setSalesOrderNo(plan.getSalesOrderNo());
            workOrder.setProductId(item.getProductId());
            workOrder.setProductCode(item.getProductCode());
            workOrder.setProductName(item.getProductName());
            workOrder.setProductSpec(plan.getProductSpec());
            workOrder.setProductUnit(plan.getProductUnit());
            workOrder.setPlannedQuantity(item.getPlannedQuantity());
            workOrder.setCompletedQuantity(BigDecimal.ZERO);
            workOrder.setRemainingQuantity(item.getPlannedQuantity());
            workOrder.setPlanStartDate(item.getPlanStartDate());
            workOrder.setPlanEndDate(item.getPlanEndDate());
            workOrder.setOrderStatus(OrderStatusEnum.PLANNED.getCode()); // 2026-08-11：计划转工单=排期确定，直接已计划，可启动
            workOrder.setPriority(item.getPriority() != null ? item.getPriority() : "MEDIUM");
            workOrder.setDepartmentId(plan.getDepartmentId());
            workOrder.setDepartmentName(plan.getDepartmentName());
            workOrder.setRemark(item.getRemark());
            // 复制工艺路线
            workOrder.setRoutingId(plan.getRoutingId());
            workOrder.setRoutingCode(plan.getRoutingCode());

            save(workOrder);
            createdOrderIds.add(workOrder.getOrderId());

            // 生成工序执行记录（基于工艺路线）
            generateOperationExecutions(workOrder.getOrderId(), plan.getRoutingId(),
                    item.getPlanStartDate(), item.getPlanEndDate());

            log.info("工单已生成: {} (计划: {})", workOrderNo, plan.getOrderNo());
        }

        // 更新计划状态为"已转工单"（2026-08-11：CLOSED=已关闭，避免与工单的"已计划"混淆）
        plan.setOrderStatus(OrderStatusEnum.CLOSED.getCode());
        updateById(plan);

        return createdOrderIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOrderStatus(Long orderId, Integer newStatus, String remark) {
        log.info("更新订单状态: orderId={}, newStatus={}", orderId, newStatus);

        ProductionOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在: " + orderId);
        }

        // 状态流转校验
        validateStatusTransition(order.getOrderStatus(), newStatus);

        order.setOrderStatus(newStatus);
        if (remark != null) {
            order.setRemark(remark);
        }

        // 如果启动了，记录实际开始时间
        if (OrderStatusEnum.IN_PROGRESS.getCode().equals(newStatus) && order.getActualStartTime() == null) {
            order.setActualStartTime(LocalDateTime.now());
        }
        // 如果完成了，记录实际完成时间
        if (OrderStatusEnum.COMPLETED.getCode().equals(newStatus)) {
            order.setActualEndTime(LocalDateTime.now());
            order.setCompletedQuantity(order.getPlannedQuantity());
            order.setRemainingQuantity(BigDecimal.ZERO);
        }

        return updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchUpdateOrderStatus(List<Long> orderIds, Integer newStatus, String remark) {
        log.info("批量更新订单状态: count={}, newStatus={}", orderIds.size(), newStatus);
        for (Long orderId : orderIds) {
            updateOrderStatus(orderId, newStatus, remark);
        }
        return true;
    }

    /**
     * 生成工单编号: WO-{计划编号}-{序号}
     */
    private String generateWorkOrderNo(ProductionOrder plan, int seq) {
        String planNo = plan.getOrderNo();
        return "WO-" + planNo + "-" + String.format("%02d", seq);
    }

    /**
     * 根据工艺路线生成工序执行记录
     */
    private void generateOperationExecutions(Long orderId, Long routingId,
                                              LocalDate planStartDate, LocalDate planEndDate) {
        if (routingId == null) {
            log.warn("工单 {} 未指定工艺路线，跳过工序生成", orderId);
            return;
        }

        // 查询工艺路线下的所有工序
        List<EngineeringRoutingItem> routingItems = productRoutingItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EngineeringRoutingItem>()
                        .eq(EngineeringRoutingItem::getRoutingId, routingId)
                        .orderByAsc(EngineeringRoutingItem::getProcessOrder)
        );

        if (routingItems.isEmpty()) {
            log.warn("工艺路线 {} 下没有工序，跳过工序生成", routingId);
            return;
        }

        long daySpan = planStartDate != null && planEndDate != null ?
                java.time.temporal.ChronoUnit.DAYS.between(planStartDate, planEndDate) : 0;
        long totalSteps = routingItems.size();
        long daysPerStep = totalSteps > 0 ? Math.max(1, daySpan / totalSteps) : 1;

        for (int i = 0; i < routingItems.size(); i++) {
            EngineeringRoutingItem item = routingItems.get(i);

            ProductionOperationExecution execution = new ProductionOperationExecution();
            execution.setOrderId(orderId);
            execution.setProcessId(item.getProcessId());
            // 2026-08-12：印刷等自定义工序透传 大类/名称/计划参数（生产侧接入）
            execution.setMajorCategory(item.getMajorCategory() != null ? item.getMajorCategory() : "ASSEMBLY");
            execution.setProcessName(item.getProcessName());
            execution.setCustomProcessParams(item.getCustomProcessParams());
            execution.setProcessOrder(item.getProcessOrder());

            // 049定稿：首道工序激活（EXECUTING），其余待执行（PENDING）
            if (i == 0) {
                execution.setExecutionStatus(com.jjx.production.enums.ExecutionStatusEnum.EXECUTING.getCode());
                execution.setActualStartTime(java.time.LocalDateTime.now());
            } else {
                execution.setExecutionStatus(com.jjx.production.enums.ExecutionStatusEnum.PENDING.getCode());
            }

            // 按工序分配时间
            if (planStartDate != null && planEndDate != null) {
                LocalDate stepStart = planStartDate.plusDays(i * daysPerStep);
                LocalDate stepEnd = i == routingItems.size() - 1 ?
                        planEndDate : planStartDate.plusDays((i + 1) * daysPerStep - 1);
                execution.setPlannedStartTime(stepStart.atStartOfDay());
                execution.setPlannedEndTime(stepEnd.atTime(23, 59, 59));
            }

            execution.setActualLaborHours(BigDecimal.ZERO);
            execution.setActualMachineHours(BigDecimal.ZERO);

            productionOperationExecutionMapper.insert(execution);
        }

        log.info("已为工单 {} 生成 {} 个工序执行记录", orderId, routingItems.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOrderPlanDate(Long orderId, String planStartDate, String planEndDate) {
        ProductionOrder order = baseMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在: " + orderId);
        }
        if (planStartDate != null && !planStartDate.isEmpty()) {
            order.setPlanStartDate(LocalDate.parse(planStartDate));
        }
        if (planEndDate != null && !planEndDate.isEmpty()) {
            order.setPlanEndDate(LocalDate.parse(planEndDate));
        }
        return baseMapper.updateById(order) > 0;
    }

    /**
     * 校验状态流转是否合法
     */
    private void validateStatusTransition(Integer currentStatus, Integer newStatus) {
        if (currentStatus.equals(newStatus)) return;

        int cs = currentStatus;
        int ns = newStatus;

        if (cs == 0) { // DRAFT
            if (ns != 1 && ns != 9) throw new BusinessException("草稿状态只能转为待审批或已取消");
        } else if (cs == 1) { // PENDING_APPROVAL
            if (ns != 2 && ns != 3 && ns != 9) throw new BusinessException("待审批状态只能转为已审批、已驳回或已取消");
        } else if (cs == 2) { // APPROVED
            if (ns != 6 && ns != 9) throw new BusinessException("已审批状态只能转为进行中或已取消");
        } else if (cs == 4) { // PLANNED
            if (ns != 6 && ns != 9) throw new BusinessException("已计划状态只能转为进行中或已取消");
        } else if (cs == 5) { // PENDING_START
            if (ns != 6 && ns != 9) throw new BusinessException("待开始状态只能转为进行中或已取消");
        } else if (cs == 6) { // IN_PROGRESS
            if (ns != 8 && ns != 7 && ns != 9) throw new BusinessException("进行中状态只能转为已完成、已暂停或已取消");
        } else if (cs == 7) { // PAUSED
            if (ns != 6 && ns != 9) throw new BusinessException("已暂停状态只能转为进行中或已取消");
        } else if (cs == 8) { // COMPLETED
            if (ns != 10) throw new BusinessException("已完成状态只能转为已关闭");
        } else {
            throw new BusinessException("不支持的状态流转: " + currentStatus + " -> " + newStatus);
        }
    }

    @Override
    public byte[] exportPdf(Long orderId) {
        ProductionOrderVO vo = getOrderById(orderId);
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");

        java.util.Map<String, String> info = new java.util.LinkedHashMap<>();
        info.put("工单号", vo.getOrderNo());
        info.put("产品编码", vo.getProductCode());
        info.put("产品名称", vo.getProductName());
        info.put("产品规格", vo.getProductSpec() == null ? "-" : vo.getProductSpec());
        info.put("来源销售单", vo.getSalesOrderNo() == null ? "-" : vo.getSalesOrderNo());
        info.put("计划开始", vo.getPlanStartDate() == null ? "" : vo.getPlanStartDate().toString());
        info.put("计划结束", vo.getPlanEndDate() == null ? "" : vo.getPlanEndDate().toString());
        info.put("BOM", vo.getRoutingName() == null ? "-" : vo.getRoutingName());
        info.put("工艺路线", vo.getRoutingCode() == null ? "-" : vo.getRoutingCode());
        info.put("状态", vo.getOrderStatusDesc() == null ? "-" : vo.getOrderStatusDesc());

        java.util.List<String[]> rows = new java.util.ArrayList<>();
        rows.add(new String[]{"1", vo.getProductCode(), vo.getProductName(),
                vo.getPlannedQuantity() == null ? "" : df.format(vo.getPlannedQuantity()),
                vo.getProductUnit() == null ? "" : vo.getProductUnit(),
                vo.getCompletedQuantity() == null ? "" : df.format(vo.getCompletedQuantity()),
                vo.getRemainingQuantity() == null ? "" : df.format(vo.getRemainingQuantity())});

        return com.jjx.common.utils.pdf.PdfDocBuilder.create()
                .withConfig(pdfConfigLoader.load())
                .withConfig(pdfConfigLoader.load())
                .title("生  产  工  单")
                .info(info)
                .items(new String[]{"序号", "产品编码", "产品名称", "计划数量", "单位", "已完成", "剩余"}, rows)
                .amounts(new String[][]{
                        {"材料成本", vo.getMaterialCost() == null ? "" : df.format(vo.getMaterialCost())},
                        {"人工成本", vo.getLaborCost() == null ? "" : df.format(vo.getLaborCost())},
                        {"总成本", vo.getTotalCost() == null ? "" : df.format(vo.getTotalCost())},
                })
                .remark(vo.getRemark())
                .signatures("生产负责人：", "车间确认：", "日期：")
                .toBytes();
    }
}
