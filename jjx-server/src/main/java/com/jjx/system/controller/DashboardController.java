package com.jjx.system.controller;

import com.jjx.common.core.result.Result;
import com.jjx.inventory.service.InventoryMaterialService;
import com.jjx.inventory.service.InventoryStockService;
import com.jjx.product.service.IProductService;
import com.jjx.sales.service.ICustomerService;
import com.jjx.system.annotation.Log;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.service.ISysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private final InventoryMaterialService materialService;
    private final InventoryStockService stockService;
    private final IProductService productService;
    private final ICustomerService customerService;
    private final ISysUserService userService;

    @GetMapping("/my-stats")
    @Operation(summary = "获取仪表盘统计数据")
    @Log(module = "仪表盘", businessType = BusinessType.OTHER)
    public Result<Map<String, Object>> getMyStats() {
        Map<String, Object> data = new HashMap<>();

        // 通用统计（所有人可见）
        data.put("materialCount", materialService.count());
        data.put("stockCount", stockService.count());
        data.put("productCount", productService.count());
        data.put("customerCount", customerService.count());
        data.put("userCount", userService.count());

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
}
