package com.jjx.production.service.impl;

import com.jjx.production.domain.dto.ConvertPlanToWorkOrdersDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.product.mapper.ProductRoutingItemMapper;
import com.jjx.product.domain.entity.ProductRoutingItem;
import java.util.ArrayList;

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
    private final ProductRoutingItemMapper productRoutingItemMapper;
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
    public boolean completeOrder(Long orderId) {
        log.info("完成生产工单: {}", orderId);

        ProductionOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }

        // 检查工单状态是否可以完成
        if (!canCompleteOrder(order)) {
            throw new BusinessException("工单状态不允许完成");
        }

        // 更新状态为已完成
        order.setOrderStatus(OrderStatusEnum.COMPLETED.getCode());
        order.setActualEndTime(LocalDateTime.now());

        boolean success = updateById(order);
        if (!success) {
            throw new BusinessException("完成生产工单失败");
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
        if (queryDTO.getOrderType() != null) {
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
     * 检查工单是否可以完成
     */
    private static boolean canCompleteOrder(ProductionOrder order) {
        // 只有进行中状态的工单可以完成
        return OrderStatusEnum.IN_PROGRESS.getCode().equals(order.getOrderStatus());
    }

    /**
     * 检查工单是否可以取消
     */
    private static boolean canCancelOrder(ProductionOrder order) {
        // 只有草稿、待审批、已批准、已排程和进行中状态的工单可以取消
        Integer status = order.getOrderStatus();
        return OrderStatusEnum.DRAFT.getCode().equals(status) ||
               OrderStatusEnum.PENDING_APPROVAL.getCode().equals(status) ||
               OrderStatusEnum.APPROVED.getCode().equals(status) ||
               OrderStatusEnum.PLANNED.getCode().equals(status) ||
               OrderStatusEnum.IN_PROGRESS.getCode().equals(status);
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
            workOrder.setOrderStatus(OrderStatusEnum.DRAFT.getCode());
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

        // 更新计划状态为"已转工单"
        plan.setOrderStatus(OrderStatusEnum.PLANNED.getCode());
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
        List<ProductRoutingItem> routingItems = productRoutingItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductRoutingItem>()
                        .eq(ProductRoutingItem::getRoutingId, routingId)
                        .orderByAsc(ProductRoutingItem::getProcessOrder)
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
            ProductRoutingItem item = routingItems.get(i);

            ProductionOperationExecution execution = new ProductionOperationExecution();
            execution.setOrderId(orderId);
            execution.setProcessId(item.getProcessId());
            execution.setProcessOrder(item.getProcessOrder());

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
}
