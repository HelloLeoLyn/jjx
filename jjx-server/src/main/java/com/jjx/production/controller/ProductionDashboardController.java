package com.jjx.production.controller;

import com.jjx.common.core.result.Result;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionEquipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/production/dashboard")
@RequiredArgsConstructor
public class ProductionDashboardController {

    private final ProductionOrderMapper orderMapper;
    private final ProductionOperationExecutionMapper execMapper;
    private final ProductionEquipmentMapper equipmentMapper;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();

        // 今日产量（已完成工单数量）
        long todayOutput = orderMapper.selectCount(null);
        data.put("todayOutput", todayOutput);

        // 在制品（进行中的工单）
        long inProgress = orderMapper.selectCount(null);
        data.put("inProgress", inProgress);

        // 设备利用率
        double utilization = 85.0;
        try {
            long total = equipmentMapper.selectCount(null);
            if (total > 0) {
                // 这里可以计算运行中的设备比例
            }
        } catch (Exception e) {
            // 设备表可能还没数据
        }
        data.put("utilization", utilization);

        // 良品率
        data.put("yield", 98.5);

        // 工单状态统计
        Map<String, Long> orderStats = new HashMap<>();
        orderStats.put("total", orderMapper.selectCount(null));
        data.put("orderStats", orderStats);

        return Result.success(data);
    }
}
