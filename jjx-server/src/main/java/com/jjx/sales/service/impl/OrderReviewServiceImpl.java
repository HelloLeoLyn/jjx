package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.OrderReviewRecord;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.enums.OrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.OrderReviewRecordMapper;
import com.jjx.sales.service.IOrderReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 订单审核服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReviewServiceImpl implements IOrderReviewService {

    private final OrderMapper orderMapper;
    private final OrderReviewRecordMapper reviewRecordMapper;

    /**
     * 提交订单审核
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitOrderForReview(Long orderId, Long submitterId, String submitterName, String submitComment) {
        // 获取订单信息
        SalesOrder order = getOrderById(orderId);

        // 检查订单是否可提交审核
        if (!canSubmitForReview(orderId)) {
            throw new BusinessException("订单当前状态不可提交审核");
        }

        // 创建审核记录
        OrderReviewRecord record = new OrderReviewRecord();
        record.setOrderId(orderId);
        record.setOrderNo(order.getOrderNo());
        record.setReviewStage(1); // 提交审核阶段
        record.setStageName("提交审核");
        record.setPreviousStatus(order.getOrderStatus());
        record.setCurrentStatus(OrderStatusEnum.PENDING_REVIEW.getValue());
        record.setReviewerId(submitterId);
        record.setReviewerName(submitterName);
        record.setReviewerRole("提交人");
        record.setReviewComment(submitComment);
        record.setReviewTime(LocalDateTime.now());
        record.setReviewProcessId(generateProcessId());
        record.setNodeSequence(1);
        record.setNodeName("提交审核节点");
        record.setProcessStatus(1); // 进行中
        record.setProcessStartTime(LocalDateTime.now());
        record.setProcessTimeoutTime(LocalDateTime.now().plusHours(24)); // 24小时超时

        // 保存审核记录
        reviewRecordMapper.insert(record);

        // 更新订单状态
        updateOrderStatus(orderId, OrderStatusEnum.PENDING_REVIEW.getValue());

        log.info("订单 {} 已提交审核，提交人：{}", orderId, submitterName);
        return record.getRecordId();
    }

    /**
     * 开始审核订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long startOrderReview(Long orderId, Long reviewerId, String reviewerName, String reviewerRole) {
        // 获取订单信息
        SalesOrder order = getOrderById(orderId);

        // 检查订单是否可审核
        if (!canReviewOrder(orderId, reviewerId)) {
            throw new BusinessException("订单当前状态不可审核或您无审核权限");
        }

        // 创建审核记录
        OrderReviewRecord record = new OrderReviewRecord();
        record.setOrderId(orderId);
        record.setOrderNo(order.getOrderNo());
        record.setReviewStage(2); // 开始审核阶段
        record.setStageName("开始审核");
        record.setPreviousStatus(order.getOrderStatus());
        record.setCurrentStatus(OrderStatusEnum.REVIEWING.getValue());
        record.setReviewerId(reviewerId);
        record.setReviewerName(reviewerName);
        record.setReviewerRole(reviewerRole);
        record.setReviewTime(LocalDateTime.now());

        // 保存审核记录
        reviewRecordMapper.insert(record);

        // 更新订单状态
        updateOrderStatus(orderId, OrderStatusEnum.REVIEWING.getValue());

        log.info("订单 {} 开始审核，审核人：{}", orderId, reviewerName);
        return record.getRecordId();
    }

    /**
     * 审核通过订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long approveOrder(Long orderId, Long reviewerId, String reviewerName, String reviewComment, String attachments) {
        // 获取订单信息
        SalesOrder order = getOrderById(orderId);

        // 检查订单是否可审核
        if (!canReviewOrder(orderId, reviewerId)) {
            throw new BusinessException("订单当前状态不可审核或您无审核权限");
        }

        // 获取当前审核记录
        OrderReviewRecord currentRecord = reviewRecordMapper.selectCurrentReview(orderId);
        if (currentRecord == null) {
            throw new BusinessException("未找到当前审核记录");
        }

        // 计算审核耗时
        Integer reviewDuration = calculateReviewDuration(currentRecord.getCreateTime());

        // 更新审核记录
        currentRecord.setReviewResult(1); // 通过
        currentRecord.setResultDescription("审核通过");
        currentRecord.setReviewComment(reviewComment);
        currentRecord.setAttachments(attachments);
        currentRecord.setReviewDuration(reviewDuration);
        currentRecord.setReviewTime(LocalDateTime.now());
        currentRecord.setProcessStatus(2); // 已完成
        currentRecord.setProcessEndTime(LocalDateTime.now());

        reviewRecordMapper.updateById(currentRecord);

        // 创建新的审核记录（审核通过阶段）
        OrderReviewRecord record = new OrderReviewRecord();
        record.setOrderId(orderId);
        record.setOrderNo(order.getOrderNo());
        record.setReviewStage(3); // 审核通过阶段
        record.setStageName("审核通过");
        record.setPreviousStatus(order.getOrderStatus());
        record.setCurrentStatus(OrderStatusEnum.APPROVED.getValue());
        record.setReviewerId(reviewerId);
        record.setReviewerName(reviewerName);
        record.setReviewerRole("审核人");
        record.setReviewComment(reviewComment);
        record.setReviewResult(1);
        record.setResultDescription("审核通过");
        record.setReviewTime(LocalDateTime.now());
        record.setReviewDuration(reviewDuration);

        reviewRecordMapper.insert(record);

        // 更新订单状态
        updateOrderStatus(orderId, OrderStatusEnum.APPROVED.getValue());

        log.info("订单 {} 审核通过，审核人：{}", orderId, reviewerName);
        return record.getRecordId();
    }

    /**
     * 审核驳回订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long rejectOrder(Long orderId, Long reviewerId, String reviewerName, String reviewComment,
                            String rejectReason, String improvementSuggestions) {
        // 获取订单信息
        SalesOrder order = getOrderById(orderId);

        // 检查订单是否可审核
        if (!canReviewOrder(orderId, reviewerId)) {
            throw new BusinessException("订单当前状态不可审核或您无审核权限");
        }

        // 获取当前审核记录
        OrderReviewRecord currentRecord = reviewRecordMapper.selectCurrentReview(orderId);
        if (currentRecord == null) {
            throw new BusinessException("未找到当前审核记录");
        }

        // 计算审核耗时
        Integer reviewDuration = calculateReviewDuration(currentRecord.getCreateTime());

        // 更新审核记录
        currentRecord.setReviewResult(2); // 驳回
        currentRecord.setResultDescription("审核驳回");
        currentRecord.setReviewComment(reviewComment);
        currentRecord.setReviewDuration(reviewDuration);
        currentRecord.setReviewTime(LocalDateTime.now());
        currentRecord.setProcessStatus(2); // 已完成
        currentRecord.setProcessEndTime(LocalDateTime.now());

        reviewRecordMapper.updateById(currentRecord);

        // 创建新的审核记录（审核驳回阶段）
        OrderReviewRecord record = new OrderReviewRecord();
        record.setOrderId(orderId);
        record.setOrderNo(order.getOrderNo());
        record.setReviewStage(4); // 审核驳回阶段
        record.setStageName("审核驳回");
        record.setPreviousStatus(order.getOrderStatus());
        record.setCurrentStatus(OrderStatusEnum.REJECTED.getValue());
        record.setReviewerId(reviewerId);
        record.setReviewerName(reviewerName);
        record.setReviewerRole("审核人");
        record.setReviewComment(reviewComment);
        record.setReviewResult(2);
        record.setResultDescription("审核驳回 - " + rejectReason);
        record.setReviewTime(LocalDateTime.now());
        record.setReviewDuration(reviewDuration);
        record.setImprovementSuggestions(improvementSuggestions);

        reviewRecordMapper.insert(record);

        // 更新订单状态
        updateOrderStatus(orderId, OrderStatusEnum.REJECTED.getValue());

        log.info("订单 {} 审核驳回，审核人：{}，原因：{}", orderId, reviewerName, rejectReason);
        return record.getRecordId();
    }

    /**
     * 退回订单修改
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long returnOrderForModification(Long orderId, Long reviewerId, String reviewerName, String reviewComment,
                                           String returnReason, String modificationRequirements) {
        // 获取订单信息
        SalesOrder order = getOrderById(orderId);

        // 检查订单是否可审核
        if (!canReviewOrder(orderId, reviewerId)) {
            throw new BusinessException("订单当前状态不可审核或您无审核权限");
        }

        // 获取当前审核记录
        OrderReviewRecord currentRecord = reviewRecordMapper.selectCurrentReview(orderId);
        if (currentRecord == null) {
            throw new BusinessException("未找到当前审核记录");
        }

        // 计算审核耗时
        Integer reviewDuration = calculateReviewDuration(currentRecord.getCreateTime());

        // 更新审核记录
        currentRecord.setReviewResult(3); // 退回修改
        currentRecord.setResultDescription("退回修改");
        currentRecord.setReviewComment(reviewComment);
        currentRecord.setReviewDuration(reviewDuration);
        currentRecord.setReviewTime(LocalDateTime.now());

        reviewRecordMapper.updateById(currentRecord);

        // 创建新的审核记录（退回修改阶段）
        OrderReviewRecord record = new OrderReviewRecord();
        record.setOrderId(orderId);
        record.setOrderNo(order.getOrderNo());
        record.setReviewStage(5); // 退回修改阶段
        record.setStageName("退回修改");
        record.setPreviousStatus(order.getOrderStatus());
        record.setCurrentStatus(OrderStatusEnum.DRAFT.getValue());
        record.setReviewerId(reviewerId);
        record.setReviewerName(reviewerName);
        record.setReviewerRole("审核人");
        record.setReviewComment(reviewComment);
        record.setReviewResult(3);
        record.setResultDescription("退回修改 - " + returnReason);
        record.setReviewTime(LocalDateTime.now());
        record.setReviewDuration(reviewDuration);
        record.setReviewRequirements(modificationRequirements);

        reviewRecordMapper.insert(record);

        // 更新订单状态
        updateOrderStatus(orderId, OrderStatusEnum.DRAFT.getValue());

        log.info("订单 {} 退回修改，审核人：{}，原因：{}", orderId, reviewerName, returnReason);
        return record.getRecordId();
    }

    /**
     * 转交审核
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long transferOrderReview(Long orderId, Long currentReviewerId, Long nextReviewerId,
                                    String nextReviewerName, String transferReason) {
        // 获取订单信息
        SalesOrder order = getOrderById(orderId);

        // 检查当前用户是否有权限转交
        OrderReviewRecord currentRecord = reviewRecordMapper.selectCurrentReview(orderId);
        if (currentRecord == null || !currentRecord.getReviewerId().equals(currentReviewerId)) {
            throw new BusinessException("您无权转交此订单的审核");
        }

        // 更新当前审核记录
        currentRecord.setReviewResult(4); // 转交他人
        currentRecord.setResultDescription("转交审核");
        currentRecord.setReviewComment("转交给 " + nextReviewerName + "，原因：" + transferReason);
        currentRecord.setReviewTime(LocalDateTime.now());
        currentRecord.setNextHandlerId(nextReviewerId);
        currentRecord.setNextHandlerName(nextReviewerName);

        reviewRecordMapper.updateById(currentRecord);

        // 创建新的审核记录（转交阶段）
        OrderReviewRecord record = new OrderReviewRecord();
        record.setOrderId(orderId);
        record.setOrderNo(order.getOrderNo());
        record.setReviewStage(6); // 转交审核阶段
        record.setStageName("转交审核");
        record.setPreviousStatus(order.getOrderStatus());
        record.setCurrentStatus(order.getOrderStatus()); // 状态不变
        record.setReviewerId(nextReviewerId);
        record.setReviewerName(nextReviewerName);
        record.setReviewerRole("审核人");
        record.setReviewComment("由 " + currentRecord.getReviewerName() + " 转交，原因：" + transferReason);
        record.setReviewTime(LocalDateTime.now());

        reviewRecordMapper.insert(record);

        log.info("订单 {} 审核转交，从 {} 转交给 {}", orderId, currentRecord.getReviewerName(), nextReviewerName);
        return record.getRecordId();
    }

    /**
     * 客户确认订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long confirmOrderByCustomer(Long orderId, Long customerId, String customerName,
                                       String confirmComment, String customerFeedback) {
        // 获取订单信息
        SalesOrder order = getOrderById(orderId);

        // 检查订单是否可客户确认
        if (!canConfirmByCustomer(orderId, customerId)) {
            throw new BusinessException("订单当前状态不可客户确认或客户不匹配");
        }

        // 创建审核记录
        OrderReviewRecord record = new OrderReviewRecord();
        record.setOrderId(orderId);
        record.setOrderNo(order.getOrderNo());
        record.setReviewStage(7); // 客户确认阶段
        record.setStageName("客户确认");
        record.setPreviousStatus(order.getOrderStatus());
        record.setCurrentStatus(OrderStatusEnum.CONFIRMED.getValue());
        record.setReviewerId(customerId);
        record.setReviewerName(customerName);
        record.setReviewerRole("客户");
        record.setReviewComment(confirmComment);
        record.setCustomerFeedback(customerFeedback);
        record.setReviewResult(1); // 通过
        record.setResultDescription("客户确认");
        record.setReviewTime(LocalDateTime.now());
        record.setNotifyCustomer(true);
        record.setNotificationMethod("系统通知");

        reviewRecordMapper.insert(record);

        // 确认记录落库订单字段（DEV-343/314）
        SalesOrder confirmUpdate = new SalesOrder();
        confirmUpdate.setOrderId(orderId);
        confirmUpdate.setConfirmBy(customerName);
        confirmUpdate.setConfirmMethod("客户确认");
        confirmUpdate.setConfirmTime(LocalDateTime.now());
        orderMapper.updateById(confirmUpdate);

        // 更新订单状态
        updateOrderStatus(orderId, OrderStatusEnum.CONFIRMED.getValue());

        log.info("订单 {} 客户确认，客户：{}", orderId, customerName);
        return record.getRecordId();
    }

    /**
     * 取消订单审核
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long cancelOrderReview(Long orderId, Long cancellerId, String cancellerName, String cancelReason) {
        // 获取订单信息
        SalesOrder order = getOrderById(orderId);

        // 检查订单是否可取消
        OrderStatusEnum currentStatus = OrderStatusEnum.getByValue(order.getOrderStatus());
        if (!currentStatus.isCancellable()) {
            throw new BusinessException("订单当前状态不可取消");
        }

        // 获取当前审核记录
        OrderReviewRecord currentRecord = reviewRecordMapper.selectCurrentReview(orderId);
        if (currentRecord != null) {
            // 更新当前审核记录
            currentRecord.setReviewResult(5); // 取消
            currentRecord.setResultDescription("审核取消");
            currentRecord.setReviewComment("取消原因：" + cancelReason);
            currentRecord.setReviewTime(LocalDateTime.now());
            currentRecord.setProcessStatus(3); // 已终止
            currentRecord.setProcessEndTime(LocalDateTime.now());

            reviewRecordMapper.updateById(currentRecord);
        }

        // 创建新的审核记录（取消阶段）
        OrderReviewRecord record = new OrderReviewRecord();
        record.setOrderId(orderId);
        record.setOrderNo(order.getOrderNo());
        record.setReviewStage(8); // 取消审核阶段
        record.setStageName("取消审核");
        record.setPreviousStatus(order.getOrderStatus());
        record.setCurrentStatus(OrderStatusEnum.CANCELLED.getValue());
        record.setReviewerId(cancellerId);
        record.setReviewerName(cancellerName);
        record.setReviewerRole("取消人");
        record.setReviewComment(cancelReason);
        record.setReviewResult(5);
        record.setResultDescription("审核取消");
        record.setReviewTime(LocalDateTime.now());

        reviewRecordMapper.insert(record);

        // 更新订单状态
        updateOrderStatus(orderId, OrderStatusEnum.CANCELLED.getValue());

        log.info("订单 {} 审核取消，取消人：{}，原因：{}", orderId, cancellerName, cancelReason);
        return record.getRecordId();
    }

    /**
     * 获取订单审核记录列表
     */
    @Override
    public List<OrderReviewRecord> getOrderReviewRecords(Long orderId) {
        return reviewRecordMapper.selectByOrderId(orderId);
    }

    /**
     * 获取订单审核历史
     */
    @Override
    public List<OrderReviewRecord> getOrderReviewHistory(Long orderId) {
        return reviewRecordMapper.selectReviewHistory(orderId);
    }

    /**
     * 获取当前审核信息
     */
    @Override
    public OrderReviewRecord getCurrentReviewInfo(Long orderId) {
        return reviewRecordMapper.selectCurrentReview(orderId);
    }

    /**
     * 获取待审核订单列表
     */
    @Override
    public List<SalesOrder> getPendingReviewOrders(Long reviewerId) {
        // 获取当前用户待审核的记录
        List<OrderReviewRecord> pendingRecords = reviewRecordMapper.selectPendingByReviewerId(reviewerId);

        // 提取订单ID列表
        List<Long> orderIds = pendingRecords.stream()
                .map(OrderReviewRecord::getOrderId)
                .distinct()
                .toList();

        if (orderIds.isEmpty()) {
            return List.of();
        }

        // 查询订单信息
        LambdaQueryWrapper<SalesOrder> wrapper = Wrappers.lambdaQuery();
        wrapper.in(SalesOrder::getOrderId, orderIds)
               .eq(SalesOrder::getDeleted, 0)
               .orderByDesc(SalesOrder::getCreateTime);

        return orderMapper.selectList(wrapper);
    }

    /**
     * 获取已提交审核订单列表
     */
    @Override
    public List<SalesOrder> getSubmittedReviewOrders(Long submitterId) {
        // 查询提交人提交的审核记录
        LambdaQueryWrapper<OrderReviewRecord> recordWrapper = Wrappers.lambdaQuery();
        recordWrapper.eq(OrderReviewRecord::getReviewerId, submitterId)
                    .eq(OrderReviewRecord::getReviewStage, 1) // 提交审核阶段
                    .eq(OrderReviewRecord::getDeleted, 0);

        List<OrderReviewRecord> submittedRecords = reviewRecordMapper.selectList(recordWrapper);

        if (submittedRecords.isEmpty()) {
            return List.of();
        }

        // 提取订单ID列表
        List<Long> orderIds = submittedRecords.stream()
                .map(OrderReviewRecord::getOrderId)
                .distinct()
                .toList();

        // 查询订单信息
        LambdaQueryWrapper<SalesOrder> wrapper = Wrappers.lambdaQuery();
        wrapper.in(SalesOrder::getOrderId, orderIds)
               .eq(SalesOrder::getDeleted, 0)
               .orderByDesc(SalesOrder::getCreateTime);

        return orderMapper.selectList(wrapper);
    }

    /**
     * 获取审核统计信息
     */
    @Override
    public Object getReviewStatistics(Long reviewerId, String startDate, String endDate) {
        return reviewRecordMapper.getReviewStatistics(reviewerId, startDate, endDate);
    }

    /**
     * 检查订单是否可提交审核
     */
    @Override
    public boolean canSubmitForReview(Long orderId) {
        try {
            SalesOrder order = getOrderById(orderId);
            OrderStatusEnum status = OrderStatusEnum.getByValue(order.getOrderStatus());
            return status.isSubmittable();
        } catch (Exception e) {
            log.error("检查订单是否可提交审核失败", e);
            return false;
        }
    }

    /**
     * 检查订单是否可审核
     */
    @Override
    public boolean canReviewOrder(Long orderId, Long reviewerId) {
        try {
            SalesOrder order = getOrderById(orderId);
            OrderStatusEnum status = OrderStatusEnum.getByValue(order.getOrderStatus());

            if (!status.isReviewable()) {
                return false;
            }

            // 检查当前审核记录是否属于该审核人
            OrderReviewRecord currentRecord = reviewRecordMapper.selectCurrentReview(orderId);
            return currentRecord != null && currentRecord.getReviewerId().equals(reviewerId);
        } catch (Exception e) {
            log.error("检查订单是否可审核失败", e);
            return false;
        }
    }

    /**
     * 检查订单是否可客户确认
     */
    @Override
    public boolean canConfirmByCustomer(Long orderId, Long customerId) {
        try {
            SalesOrder order = getOrderById(orderId);
            OrderStatusEnum status = OrderStatusEnum.getByValue(order.getOrderStatus());

            if (!status.isConfirmable()) {
                return false;
            }

            // 检查客户是否匹配
            return order.getCustomerId().equals(customerId);
        } catch (Exception e) {
            log.error("检查订单是否可客户确认失败", e);
            return false;
        }
    }

    /**
     * 获取订单审核进度
     */
    @Override
    public Object getReviewProgress(Long orderId) {
        OrderReviewRecord currentRecord = reviewRecordMapper.selectCurrentReview(orderId);
        if (currentRecord == null) {
            return Map.of("status", "未开始", "progress", 0);
        }

        String processId = currentRecord.getReviewProcessId();
        if (processId == null || processId.isEmpty()) {
            return Map.of("status", "单级审核", "currentStage", currentRecord.getStageName());
        }

        // 获取流程进度
        return reviewRecordMapper.getProcessProgress(processId);
    }

    /**
     * 获取审核超时订单列表
     */
    @Override
    public List<SalesOrder> getTimeoutReviewOrders(Integer timeoutHours) {
        List<OrderReviewRecord> timeoutRecords = reviewRecordMapper.selectTimeoutReviews(timeoutHours);

        if (timeoutRecords.isEmpty()) {
            return List.of();
        }

        // 提取订单ID列表
        List<Long> orderIds = timeoutRecords.stream()
                .map(OrderReviewRecord::getOrderId)
                .distinct()
                .toList();

        // 查询订单信息
        LambdaQueryWrapper<SalesOrder> wrapper = Wrappers.lambdaQuery();
        wrapper.in(SalesOrder::getOrderId, orderIds)
               .eq(SalesOrder::getDeleted, 0)
               .orderByDesc(SalesOrder::getCreateTime);

        return orderMapper.selectList(wrapper);
    }

    /**
     * 发送审核提醒
     */
    @Override
    public boolean sendReviewReminder(Long orderId, Long reviewerId, String reminderType) {
        try {
            // 这里应该实现发送提醒的逻辑，比如发送邮件、站内信等
            // 暂时只记录日志
            log.info("发送审核提醒：订单 {}，审核人 {}，提醒类型 {}", orderId, reviewerId, reminderType);
            return true;
        } catch (Exception e) {
            log.error("发送审核提醒失败", e);
            return false;
        }
    }

    /**
     * 导出审核记录
     */
    @Override
    public String exportReviewRecords(Long orderId) {
        // 这里应该实现导出逻辑
        // 暂时返回占位符
        return "/exports/review-records/" + orderId + ".xlsx";
    }

    /**
     * 获取多级审核配置
     */
    @Override
    public Object getMultiLevelReviewConfig(String orderType) {
        // 这里应该从数据库或配置文件中读取多级审核配置
        // 暂时返回默认配置
        return Map.of(
            "orderType", orderType,
            "levels", 2,
            "level1", Map.of("role", "部门经理", "timeout", 24),
            "level2", Map.of("role", "总经理", "timeout", 48)
        );
    }

    /**
     * 设置多级审核配置
     */
    @Override
    public boolean setMultiLevelReviewConfig(String orderType, Object config) {
        // 这里应该保存多级审核配置到数据库或配置文件
        // 暂时只记录日志
        log.info("设置多级审核配置：订单类型 {}，配置 {}", orderType, config);
        return true;
    }

    /**
     * 获取审核流程图
     */
    @Override
    public Object getReviewFlowChart(Long orderId) {
        OrderReviewRecord currentRecord = reviewRecordMapper.selectCurrentReview(orderId);
        if (currentRecord == null) {
            return Map.of("nodes", List.of(), "edges", List.of());
        }

        String processId = currentRecord.getReviewProcessId();
        if (processId == null || processId.isEmpty()) {
            // 单级审核流程图
            return Map.of(
                "nodes", List.of(
                    Map.of("id", "start", "label", "开始", "type", "start"),
                    Map.of("id", "submit", "label", "提交审核", "type", "process"),
                    Map.of("id", "review", "label", "审核", "type", "process"),
                    Map.of("id", "end", "label", "结束", "type", "end")
                ),
                "edges", List.of(
                    Map.of("source", "start", "target", "submit"),
                    Map.of("source", "submit", "target", "review"),
                    Map.of("source", "review", "target", "end")
                )
            );
        }

        // 多级审核流程图
        List<OrderReviewRecord> processRecords = reviewRecordMapper.selectByProcessId(processId);

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // 添加开始节点
        nodes.add(Map.of("id", "start", "label", "开始", "type", "start"));

        // 添加审核节点
        for (int i = 0; i < processRecords.size(); i++) {
            OrderReviewRecord record = processRecords.get(i);
            String nodeId = "node_" + i;
            nodes.add(Map.of(
                "id", nodeId,
                "label", record.getNodeName(),
                "type", "process",
                "status", record.getReviewResult() == null ? "pending" : "completed"
            ));

            // 添加边
            if (i == 0) {
                edges.add(Map.of("source", "start", "target", nodeId));
            } else {
                edges.add(Map.of("source", "node_" + (i - 1), "target", nodeId));
            }
        }

        // 添加结束节点
        nodes.add(Map.of("id", "end", "label", "结束", "type", "end"));
        if (!processRecords.isEmpty()) {
            edges.add(Map.of("source", "node_" + (processRecords.size() - 1), "target", "end"));
        }

        return Map.of("nodes", nodes, "edges", edges);
    }

    /**
     * 获取审核权限检查
     */
    @Override
    public Object getReviewPermissions(Long orderId, Long userId) {
        SalesOrder order = getOrderById(orderId);
        OrderStatusEnum status = OrderStatusEnum.getByValue(order.getOrderStatus());

        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("canSubmit", status.isSubmittable());
        permissions.put("canReview", status.isReviewable());
        permissions.put("canConfirm", status.isConfirmable());
        permissions.put("canCancel", status.isCancellable());

        // 检查具体权限
        OrderReviewRecord currentRecord = reviewRecordMapper.selectCurrentReview(orderId);
        if (currentRecord != null) {
            permissions.put("isCurrentReviewer", currentRecord.getReviewerId().equals(userId));
        } else {
            permissions.put("isCurrentReviewer", false);
        }

        return permissions;
    }

    /**
     * 批量提交审核
     */
    @Override
    public Object batchSubmitForReview(List<Long> orderIds, Long submitterId, String submitterName) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (Long orderId : orderIds) {
            try {
                Long recordId = submitOrderForReview(orderId, submitterId, submitterName, "批量提交审核");
                results.add(Map.of(
                    "orderId", orderId,
                    "success", true,
                    "recordId", recordId,
                    "message", "提交成功"
                ));
            } catch (Exception e) {
                results.add(Map.of(
                    "orderId", orderId,
                    "success", false,
                    "message", e.getMessage()
                ));
            }
        }

        return Map.of("total", orderIds.size(), "results", results);
    }

    /**
     * 批量审核通过
     */
    @Override
    public Object batchApproveOrders(List<Long> orderIds, Long reviewerId, String reviewerName) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (Long orderId : orderIds) {
            try {
                Long recordId = approveOrder(orderId, reviewerId, reviewerName, "批量审核通过", null);
                results.add(Map.of(
                    "orderId", orderId,
                    "success", true,
                    "recordId", recordId,
                    "message", "审核通过成功"
                ));
            } catch (Exception e) {
                results.add(Map.of(
                    "orderId", orderId,
                    "success", false,
                    "message", e.getMessage()
                ));
            }
        }

        return Map.of("total", orderIds.size(), "results", results);
    }

    /**
     * 批量审核驳回
     */
    @Override
    public Object batchRejectOrders(List<Long> orderIds, Long reviewerId, String reviewerName, String rejectReason) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (Long orderId : orderIds) {
            try {
                Long recordId = rejectOrder(orderId, reviewerId, reviewerName, "批量审核驳回", rejectReason, null);
                results.add(Map.of(
                    "orderId", orderId,
                    "success", true,
                    "recordId", recordId,
                    "message", "审核驳回成功"
                ));
            } catch (Exception e) {
                results.add(Map.of(
                    "orderId", orderId,
                    "success", false,
                    "message", e.getMessage()
                ));
            }
        }

        return Map.of("total", orderIds.size(), "results", results);
    }

    // ========== 私有方法 ==========

    /**
     * 获取订单信息
     */
    private SalesOrder getOrderById(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getDeleted() == 1) {
            throw new BusinessException("订单不存在或已被删除");
        }
        return order;
    }

    /**
     * 更新订单状态
     */
    private void updateOrderStatus(Long orderId, Integer status) {
        SalesOrder order = new SalesOrder();
        order.setOrderId(orderId);
        order.setOrderStatus(status);
        orderMapper.updateById(order);
    }

    /**
     * 计算审核耗时（分钟）
     */
    private static Integer calculateReviewDuration(LocalDateTime startTime) {
        if (startTime == null) {
            return 0;
        }
        return (int) ChronoUnit.MINUTES.between(startTime, LocalDateTime.now());
    }

    /**
     * 生成审核流程ID
     */
    private static String generateProcessId() {
        return "PROCESS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
