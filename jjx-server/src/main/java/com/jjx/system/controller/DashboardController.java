package com.jjx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.sales.mapper.SalesWorkbenchMapper;
import com.jjx.system.annotation.Log;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.vo.SalesWorkbenchVO;
import com.jjx.system.mapper.SysUserMapper;
import com.jjx.system.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
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
    private final SalesWorkbenchMapper salesWorkbenchMapper;

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

        // 销售数据（v-hasPermi="sales:dashboard" 控制）
        Map<String, Object> sales = new HashMap<>();
        sales.put("monthlySales", 128500);
        sales.put("orderCount", 12);
        sales.put("completionRate", 78);
        sales.put("paymentRate", 65);
        data.put("sales", sales);

        // 生产数据（v-hasPermi="production:dashboard" 控制）
        Map<String, Object> production = new HashMap<>();
        production.put("activeOrders", 8);
        production.put("todayCompleted", 3);
        production.put("progress", 65);
        production.put("alerts", 1);
        data.put("production", production);

        // 管理数据（v-hasPermi="admin:dashboard" 控制）
        Map<String, Object> admin = new HashMap<>();
        admin.put("totalSales", 385200);
        admin.put("totalCost", 256800);
        admin.put("profitRate", 33);
        admin.put("employeeCount", 28);
        data.put("admin", admin);

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
}
