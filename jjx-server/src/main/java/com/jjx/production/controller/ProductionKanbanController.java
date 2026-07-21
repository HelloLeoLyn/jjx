package com.jjx.production.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 看板对接控制器
 * 为 jjx-kanban 提供真实数据接口
 */
@Tag(name = "车间看板")
@RestController
@RequiredArgsConstructor
@RequestMapping("/production/kanban")
public class ProductionKanbanController {

    private final ProductionOrderMapper productionOrderMapper;
    private final ProductionOperationExecutionMapper executionMapper;

    /**
     * 看板视图配置（固定）
     */
    @Operation(summary = "获取可用视图")
    @GetMapping("/views")
    public Result<List<Map<String, Object>>> getViews(@RequestParam String templateType) {
        List<Map<String, Object>> views = new ArrayList<>();

        if ("production".equals(templateType)) {
            views.add(Map.of("id", "process", "name", "工序视图", "groupBy", "currentProcess",
                    "columns", List.of(
                            Map.of("id", "pending", "label", "待开始", "color", "#909399"),
                            Map.of("id", "printing", "label", "印刷", "color", "#409eff"),
                            Map.of("id", "cutting", "label", "冲切", "color", "#67c23a"),
                            Map.of("id", "laminating", "label", "贴合", "color", "#e6a23c"),
                            Map.of("id", "assembly", "label", "装配", "color", "#f56c6c"),
                            Map.of("id", "testing", "label", "测试", "color", "#b37feb"),
                            Map.of("id", "completed", "label", "已完成", "color", "#36cfc9")
                    )));
            views.add(Map.of("id", "priority", "name", "紧急度视图", "groupBy", "priority",
                    "columns", List.of(
                            Map.of("id", "urgent", "label", "紧急", "color", "#f56c6c", "filterValue", "URGENT"),
                            Map.of("id", "high", "label", "高", "color", "#e6a23c", "filterValue", "HIGH"),
                            Map.of("id", "normal", "label", "普通", "color", "#409eff", "filterValue", "MEDIUM"),
                            Map.of("id", "low", "label", "低", "color", "#909399", "filterValue", "LOW")
                    )));
            views.add(Map.of("id", "deadline", "name", "交期视图", "groupBy", "deadline",
                    "columns", List.of(
                            Map.of("id", "overdue", "label", "已逾期", "color", "#f56c6c"),
                            Map.of("id", "today", "label", "今日", "color", "#e6a23c"),
                            Map.of("id", "thisWeek", "label", "本周", "color", "#409eff"),
                            Map.of("id", "later", "label", "更晚", "color", "#909399")
                    )));
        }

        return Result.success(views);
    }

    /**
     * 获取看板数据（从 production_order 表读取）
     */
    @Operation(summary = "获取看板数据")
    @GetMapping("/data")
    public Result<Map<String, Object>> getBoardData(
            @RequestParam String templateType,
            @RequestParam String viewId) {

        if (!"production".equals(templateType)) {
            return Result.success(Map.of("view", Map.of(), "columns", List.of()));
        }

        // 读取进行中的生产工单
        List<ProductionOrder> orders = productionOrderMapper.selectList(
                new LambdaQueryWrapper<ProductionOrder>()
                        .eq(ProductionOrder::getOrderType, "WORK_ORDER")
                        .in(ProductionOrder::getOrderStatus, "APPROVED", "IN_PROGRESS", "PAUSED")
                        .orderByDesc(ProductionOrder::getPriority)
                        .last("LIMIT 50")
        );

        // 转为看板卡片格式
        List<Map<String, Object>> cards = orders.stream().map(order -> {
            Map<String, Object> card = new HashMap<>();
            card.put("id", "WO-" + order.getOrderId());
            card.put("title", order.getProductName());
            card.put("templateType", "production");
            card.put("workOrderNo", order.getOrderNo());
            card.put("productName", order.getProductName());
            card.put("quantity", order.getPlannedQuantity());
            card.put("priority", mapPriority(order.getPriority()));
            card.put("status", "in_progress");
            card.put("assignee", order.getCreateBy());
            card.put("deadline", order.getPlanEndDate() != null ? order.getPlanEndDate().toString() : "");
            card.put("currentProcess", getCurrentProcess(order.getOrderId()));
            return card;
        }).collect(Collectors.toList());

        // 按视图分组
        List<Map<String, Object>> columns = groupCardsByView(cards, viewId);

        return Result.success(Map.of(
                "view", Map.of("id", viewId),
                "columns", columns
        ));
    }

    /**
     * 获取当前工序
     */
    private String getCurrentProcess(Long orderId) {
        List<ProductionOperationExecution> execs = executionMapper.selectList(
                new LambdaQueryWrapper<ProductionOperationExecution>()
                        .eq(ProductionOperationExecution::getOrderId, orderId)
                        .isNull(ProductionOperationExecution::getActualEndTime)
                        .orderByAsc(ProductionOperationExecution::getProcessOrder)
                        .last("LIMIT 1")
        );
        if (!execs.isEmpty()) {
            Long processId = execs.get(0).getProcessId();
            return switch (processId != null ? processId.intValue() : 0) {
                case 1 -> "印刷";
                case 2 -> "冲切";
                case 3 -> "贴合";
                case 4 -> "SMT贴片";
                case 5 -> "装配";
                case 6 -> "测试";
                case 7 -> "包装";
                default -> "待开始";
            };
        }
        return "待开始";
    }

    /**
     * 按视图分组
     */
    private List<Map<String, Object>> groupCardsByView(List<Map<String, Object>> cards, String viewId) {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();

        switch (viewId) {
            case "process" -> {
                String[] cols = {"待开始", "印刷", "冲切", "贴合", "装配", "测试", "已完成"};
                String[] colIds = {"pending", "printing", "cutting", "laminating", "assembly", "testing", "completed"};
                String[] colors = {"#909399", "#409eff", "#67c23a", "#e6a23c", "#f56c6c", "#b37feb", "#36cfc9"};
                for (int i = 0; i < cols.length; i++) {
                    String col = cols[i];
                    String colId = colIds[i];
                    List<Map<String, Object>> colCards = cards.stream()
                            .filter(c -> col.equals("已完成") ? false : col.equals(c.get("currentProcess")) || (col.equals("待开始") && "待开始".equals(c.get("currentProcess"))))
                            .collect(Collectors.toList());
                    groups.put(colId, colCards);
                }
            }
            case "priority" -> {
                String[][] cols = {{"urgent", "紧急", "#f56c6c"}, {"high", "高", "#e6a23c"}, {"normal", "普通", "#409eff"}, {"low", "低", "#909399"}};
                for (String[] col : cols) {
                    String colId = col[0];
                    String label = col[1];
                    List<Map<String, Object>> colCards = cards.stream()
                            .filter(c -> label.equals(c.get("priority")))
                            .collect(Collectors.toList());
                    groups.put(colId, colCards);
                }
            }
            default -> groups.put("all", cards);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            result.add(Map.of("def", Map.of("id", entry.getKey(), "label", entry.getKey()), "cards", entry.getValue()));
        }
        return result;
    }

    private String mapPriority(String priority) {
        if (priority == null) return "普通";
        return switch (priority) {
            case "URGENT" -> "紧急";
            case "HIGH" -> "高";
            case "MEDIUM" -> "普通";
            case "LOW" -> "低";
            default -> "普通";
        };
    }
}
