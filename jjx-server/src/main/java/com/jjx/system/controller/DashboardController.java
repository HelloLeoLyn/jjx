package com.jjx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jjx.common.core.result.Result;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.kanban.enums.KanbanTaskStatusEnum;
import com.jjx.notification.domain.entity.Notification;
import com.jjx.notification.mapper.NotificationMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.enums.ProductionOrderStatusEnum;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.enums.SalesOrderStatusEnum;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesWorkbenchMapper;
import com.jjx.system.annotation.Log;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.vo.SalesWorkbenchVO;
import com.jjx.system.domain.entity.SysTask;
import com.jjx.system.mapper.SysTaskMapper;
import com.jjx.system.mapper.SysUserMapper;
import com.jjx.system.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 仪表盘控制器
 * 提供各模块统计数据，前端按权限展示对应widget
 */
@Tag(name = "仪表盘")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final InventoryMaterialMapper materialMapper;
    private final InventoryStockMapper stockMapper;
    private final ProductMapper productMapper;
    private final CustomerMapper customerMapper;
    private final SysUserMapper userMapper;
    private final ProductionOrderMapper productionOrderMapper;
    private final OrderMapper orderMapper;
    private final SalesWorkbenchMapper salesWorkbenchMapper;
    private final NotificationMapper notificationMapper;
    private final SysTaskMapper taskMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/my-stats")
    @Operation(summary = "获取仪表盘统计数据")
    public Result<Map<String, Object>> getMyStats() {
        Map<String, Object> data = new HashMap<>();

        // 通用统计（所有人可见）
        data.put("materialCount", materialMapper.selectCount(null));
        data.put("stockCount", stockMapper.selectCount(null));
        data.put("productCount", productMapper.selectCount(null));
        data.put("customerCount", customerMapper.selectCount(null));
        data.put("userCount", userMapper.selectCount(null));

        return Result.success(data);
    }

    @GetMapping("/my-todos")
    @Operation(summary = "获取我的待办任务与未读通知")
    public Result<Map<String, Object>> getMyTodos() {
        Long userId = SecurityUtils.getUserId();

        // sys_notification.status：0=待发送，1=已发送；首页只统计已送达且未读的通知。
        Long unreadNotice = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getIsRead, NotificationReadStatus.UNREAD.getValue())
                .eq(Notification::getStatus, NotificationDeliveryStatus.SENT.getValue()));

        LambdaQueryWrapper<SysTask> todoCondition = new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getAssigneeId, userId)
                .in(SysTask::getStatus, KanbanTaskStatusEnum.PENDING.getValue(),
                        KanbanTaskStatusEnum.IN_PROGRESS.getValue())
                .ne(SysTask::getTaskType, "DEV")
                .ne(SysTask::getKanbanModule, "dev");
        Long todoTotal = taskMapper.selectCount(todoCondition);

        List<Map<String, Object>> todos = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                        .eq(SysTask::getAssigneeId, userId)
                        .in(SysTask::getStatus, KanbanTaskStatusEnum.PENDING.getValue(),
                                KanbanTaskStatusEnum.IN_PROGRESS.getValue())
                        .ne(SysTask::getTaskType, "DEV")
                        .ne(SysTask::getKanbanModule, "dev")
                        .orderByDesc(SysTask::getCreateTime)
                        .last("LIMIT 10"))
                .stream()
                .map(task -> {
                    Map<String, Object> todo = new LinkedHashMap<>();
                    todo.put("taskId", task.getTaskId());
                    todo.put("title", task.getTitle());
                    todo.put("taskType", task.getTaskType());
                    todo.put("bizType", task.getBizType());
                    todo.put("bizId", task.getBizId());
                    todo.put("sourceEvent", task.getSourceEvent());
                    todo.put("deadline", task.getDeadline() == null ? null : task.getDeadline().format(DATE_FORMATTER));
                    todo.put("createTime", task.getCreateTime() == null ? null
                            : task.getCreateTime().format(DATE_TIME_FORMATTER));
                    return todo;
                })
                .toList();

        Map<String, Object> data = new HashMap<>();
        data.put("unreadNotice", unreadNotice);
        data.put("todoTotal", todoTotal);
        data.put("todos", todos);
        return Result.success(data);
    }

    /**
     * 生产概况：按 production_order 工单状态统计；今日完工以完工回写的 actual_end_time 为准。
     */
    @GetMapping("/production-overview")
    @Operation(summary = "获取生产概况")
    @SaCheckPermission("production:dashboard")
    public Result<Map<String, Long>> productionOverview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        Map<String, Long> data = new HashMap<>();
        data.put("activeOrders", productionOrderMapper.selectCount(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderStatus, ProductionOrderStatusEnum.IN_PROGRESS.getValue())));
        data.put("pendingOrders", productionOrderMapper.selectCount(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getApprovalStatus, ApproveStatusEnum.APPROVED.getValue())
                .eq(ProductionOrder::getOrderStatus, ProductionOrderStatusEnum.PENDING_START.getValue())));
        data.put("todayCompleted", productionOrderMapper.selectCount(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderStatus, ProductionOrderStatusEnum.COMPLETED.getValue())
                .ge(ProductionOrder::getActualEndTime, todayStart)
                .lt(ProductionOrder::getActualEndTime, tomorrowStart)));
        data.put("totalOrders", productionOrderMapper.selectCount(null));
        return Result.success(data);
    }

    /**
     * 公司总览：本月销售按 order_date，统计已确认至已完成的有效订单（状态6-9），金额取 final_amount。
     */
    @GetMapping("/admin-overview")
    @Operation(summary = "获取公司总览")
    @SaCheckPermission("admin:dashboard")
    public Result<Map<String, Object>> adminOverview() {
        LocalDate monthStart = YearMonth.now().atDay(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);
        LambdaQueryWrapper<SalesOrder> effectiveOrders = new LambdaQueryWrapper<SalesOrder>()
                .ge(SalesOrder::getOrderDate, monthStart)
                .lt(SalesOrder::getOrderDate, nextMonthStart)
                .between(SalesOrder::getOrderStatus, SalesOrderStatusEnum.CONFIRMED.getValue(),
                        SalesOrderStatusEnum.COMPLETED.getValue());
        Long monthOrderCount = orderMapper.selectCount(effectiveOrders);

        QueryWrapper<SalesOrder> amountQuery = new QueryWrapper<>();
        amountQuery.select("COALESCE(SUM(final_amount), 0)")
                .ge("order_date", monthStart)
                .lt("order_date", nextMonthStart)
                .between("order_status", SalesOrderStatusEnum.CONFIRMED.getValue(),
                        SalesOrderStatusEnum.COMPLETED.getValue());
        Object amountValue = orderMapper.selectObjs(amountQuery).stream().findFirst().orElse(BigDecimal.ZERO);
        BigDecimal monthSalesAmount = new BigDecimal(amountValue.toString()).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> data = new HashMap<>();
        data.put("monthSalesAmount", monthSalesAmount);
        data.put("monthOrderCount", monthOrderCount);
        data.put("materialCount", materialMapper.selectCount(null));
        data.put("userCount", userMapper.selectCount(null));
        return Result.success(data);
    }

    /**
     * 销售成员工作台（1275）：全部按当前销售过滤，真实 SQL 聚合
     */
    @GetMapping("/sales-workbench")
    @Operation(summary = "销售成员工作台（我的待办+本月业绩）", description = "口径：待处理询价0/1；已发送未回复报价1；卡审核报价5；卡审核订单2/3；待转生产订单6；发货未签收2/3；应收未清已确认后(6-9)未结清（无到期日字段，逾期后置）。业绩按本月+生效口径。")
    @SaCheckPermission("sales:dashboard")
    public Result<SalesWorkbenchVO> salesWorkbench() {
        Long userId = SecurityUtils.getUserId();
        String username = SecurityUtils.getUsername();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = YearMonth.from(today).atDay(1);
        SalesWorkbenchVO vo = new SalesWorkbenchVO();
        // 待办
        vo.setInquiryPending(nvl(salesWorkbenchMapper.countInquiryPending(userId)));
        vo.setQuotationSent(nvl(salesWorkbenchMapper.countQuotationSent(userId)));
        vo.setQuotationReviewing(nvl(salesWorkbenchMapper.countQuotationReviewing(userId)));
        vo.setOrderReviewing(nvl(salesWorkbenchMapper.countOrderReviewing(userId)));
        vo.setOrderReadyProduction(nvl(salesWorkbenchMapper.countOrderReadyProduction(userId)));
        vo.setDeliveryUnreceived(nvl(salesWorkbenchMapper.countDeliveryUnreceived(userId)));
        vo.setReceivableUnpaid(nvl(salesWorkbenchMapper.countReceivableUnpaid(userId)));
        // 本月业绩
        vo.setMonthQuotationAmount(nvl(salesWorkbenchMapper.sumMonthQuotation(userId, monthStart, today)));
        vo.setMonthOrderAmount(nvl(salesWorkbenchMapper.sumMonthOrder(userId, monthStart, today)));
        vo.setMonthReceiptAmount(nvl(salesWorkbenchMapper.sumMonthReceipt(userId, monthStart, today)));
        vo.setMonthNewCustomerCount(nvl(salesWorkbenchMapper.countMonthNewCustomer(
                username, monthStart.atStartOfDay(), today.plusDays(1).atStartOfDay())));
        vo.setMonthSampleCount(nvl(salesWorkbenchMapper.countMonthSample(userId, monthStart, today)));
        return Result.success(vo);
    }

    private Long nvl(Long v) {
        return v == null ? 0L : v;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private enum NotificationReadStatus {
        UNREAD(0);

        private final int value;

        NotificationReadStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private enum NotificationDeliveryStatus {
        SENT(1);

        private final int value;

        NotificationDeliveryStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
