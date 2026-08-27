package com.jjx.production.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionQualityInspection;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionQualityInspectionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Tag(name = "生产报表")
@RestController
@RequiredArgsConstructor
@RequestMapping("/production/report")
public class ProductionReportController {

    private final ProductionOrderMapper productionOrderMapper;
    private final ProductionOperationExecutionMapper executionMapper;
    private final ProductionQualityInspectionMapper qualityMapper;

    @Operation(summary = "产量报表")
    @GetMapping("/output")
    public Result<Map<String, Object>> outputReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderType, "WORK_ORDER")
                .ge(startDate != null, ProductionOrder::getPlanStartDate, startDate)
                .le(endDate != null, ProductionOrder::getPlanEndDate, endDate);

        List<ProductionOrder> orders = productionOrderMapper.selectList(wrapper);

        long totalPlanned = orders.stream()
                .mapToLong(o -> o.getPlannedQuantity() != null ? o.getPlannedQuantity().longValue() : 0)
                .sum();
        long totalCompleted = orders.stream()
                .mapToLong(o -> o.getCompletedQuantity() != null ? o.getCompletedQuantity().longValue() : 0)
                .sum();

        // 按产品统计
        Map<Long, Map<String, Object>> byProduct = new LinkedHashMap<>();
        for (ProductionOrder o : orders) {
            byProduct.computeIfAbsent(o.getProductId(), k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("productName", o.getProductName());
                m.put("productCode", o.getProductCode());
                m.put("planned", 0L);
                m.put("completed", 0L);
                return m;
            });
            Map<String, Object> m = byProduct.get(o.getProductId());
            m.put("planned", (long) m.get("planned") + (o.getPlannedQuantity() != null ? o.getPlannedQuantity().longValue() : 0));
            m.put("completed", (long) m.get("completed") + (o.getCompletedQuantity() != null ? o.getCompletedQuantity().longValue() : 0));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalPlanned", totalPlanned);
        result.put("totalCompleted", totalCompleted);
        result.put("completionRate", totalPlanned > 0
                ? BigDecimal.valueOf(totalCompleted).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalPlanned), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        result.put("byProduct", byProduct.values());

        return Result.success(result);
    }

    @Operation(summary = "效率报表")
    @GetMapping("/efficiency")
    public Result<Map<String, Object>> efficiencyReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        List<ProductionOperationExecution> execs = executionMapper.selectList(
                new LambdaQueryWrapper<ProductionOperationExecution>()
                        .isNotNull(ProductionOperationExecution::getActualStartTime)
                        .isNotNull(ProductionOperationExecution::getActualEndTime)
                        .ge(startDate != null, ProductionOperationExecution::getPlannedStartTime, startDate + " 00:00:00")
                        .le(endDate != null, ProductionOperationExecution::getPlannedEndTime, endDate + " 23:59:59")
                        .last("LIMIT 500"));

        long completed = execs.size();
        long onTime = execs.stream()
                .filter(e -> e.getActualEndTime() != null && e.getPlannedEndTime() != null
                        && !e.getActualEndTime().isAfter(e.getPlannedEndTime()))
                .count();

        Map<String, Object> result = new HashMap<>();
        result.put("totalOperations", completed);
        result.put("onTimeRate", completed > 0
                ? BigDecimal.valueOf(onTime).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(completed), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        result.put("onTimeCount", onTime);
        result.put("delayedCount", completed - onTime);

        return Result.success(result);
    }

    @Operation(summary = "质量报表")
    @GetMapping("/quality")
    public Result<Map<String, Object>> qualityReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        List<ProductionQualityInspection> inspections = qualityMapper.selectList(
                new LambdaQueryWrapper<ProductionQualityInspection>()
                        .ge(startDate != null, ProductionQualityInspection::getCreateTime, startDate + " 00:00:00")
                        .le(endDate != null, ProductionQualityInspection::getCreateTime, endDate + " 23:59:59")
                        .last("LIMIT 500"));

        long total = inspections.size();
        long passed = inspections.stream()
                .filter(i -> "PASS".equals(i.getResult()))
                .count();
        long failed = inspections.stream()
                .filter(i -> "FAIL".equals(i.getResult()))
                .count();

        Map<String, Object> result = new HashMap<>();
        result.put("totalInspections", total);
        result.put("passCount", passed);
        result.put("failCount", failed);
        result.put("passRate", total > 0
                ? BigDecimal.valueOf(passed).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        return Result.success(result);
    }
}
