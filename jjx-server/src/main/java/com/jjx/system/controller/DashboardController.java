package com.jjx.system.controller;

import com.jjx.common.core.result.Result;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.system.annotation.Log;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.mapper.SysUserMapper;
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

    private final InventoryMaterialMapper materialMapper;
    private final InventoryStockMapper stockMapper;
    private final ProductMapper productMapper;
    private final CustomerMapper customerMapper;
    private final SysUserMapper userMapper;

    @GetMapping("/my-stats")
    @Operation(summary = "获取仪表盘统计数据")
    @Log(module = "仪表盘", businessType = BusinessType.OTHER)
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
}
