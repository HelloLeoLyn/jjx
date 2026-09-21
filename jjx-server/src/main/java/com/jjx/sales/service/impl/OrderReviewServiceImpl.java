package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.OrderReviewRecord;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.enums.SalesOrderStatusEnum;
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
    /** 2026-09-21（dev-20260921-010）：评审记录改读 review_flow。 */
    private final com.jjx.system.service.ReviewFlowService reviewFlowService;

    /**
     * 获取订单审核记录列表
     */
    /**
     * 订单评审记录（2026-09-21 dev-20260921-010）：改读 review_flow（biz_type=sales_order）。
     * 原实现读 sales_order_review，而真实审核动作（OrderStatusServiceImpl）只写 review_flow，
     * 该表 0 行 → 记录页/打印页永远空白。
     */
    @Override
    public List<com.jjx.sales.domain.vo.OrderReviewProcessVO> getOrderReviewRecords(Long orderId) {
        List<com.jjx.sales.domain.vo.OrderReviewProcessVO> records = new java.util.ArrayList<>();
        com.jjx.sales.domain.entity.SalesOrder order = orderMapper.selectById(orderId);
        String orderNo = order == null ? null : order.getOrderNo();
        for (com.jjx.system.domain.entity.ReviewFlow flow : reviewFlowService.listByBiz("sales_order", orderId)) {
            com.jjx.sales.domain.vo.OrderReviewProcessVO vo = new com.jjx.sales.domain.vo.OrderReviewProcessVO();
            vo.setRecordId(flow.getFlowId());
            vo.setOrderId(orderId);
            vo.setOrderNo(orderNo);
            vo.setReviewStage(flow.getRoundNo());
            vo.setStageName(flow.getActionName());
            vo.setReviewerName(flow.getOperatorName());
            vo.setReviewComment(flow.getComment());
            vo.setReviewTime(flow.getCreateTime());
            vo.setReviewResult(resultCodeOf(flow.getActionCode()));
            vo.setResultDescription(resultTextOf(flow.getActionCode()));
            records.add(vo);
        }
        return records;
    }

    private static Integer resultCodeOf(String actionCode) {
        if (actionCode == null) return null;
        return switch (actionCode.toUpperCase()) {
            case "APPROVE" -> 1;
            case "REJECT" -> 2;
            case "SUBMIT" -> 0;
            default -> null;
        };
    }

    private static String resultTextOf(String actionCode) {
        if (actionCode == null) return null;
        return switch (actionCode.toUpperCase()) {
            case "APPROVE" -> "通过";
            case "REJECT" -> "驳回";
            case "SUBMIT" -> "已提交";
            default -> null;
        };
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
            SalesOrderStatusEnum status = SalesOrderStatusEnum.getByValue(order.getOrderStatus());
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
            SalesOrderStatusEnum status = SalesOrderStatusEnum.getByValue(order.getOrderStatus());

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
            SalesOrderStatusEnum status = SalesOrderStatusEnum.getByValue(order.getOrderStatus());

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
        SalesOrderStatusEnum status = SalesOrderStatusEnum.getByValue(order.getOrderStatus());

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
