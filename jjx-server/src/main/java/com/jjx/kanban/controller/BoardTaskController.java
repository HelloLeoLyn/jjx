package com.jjx.kanban.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.core.result.Result;
import com.jjx.kanban.domain.dto.BoardTaskInfoDTO;
import com.jjx.kanban.domain.dto.BoardTaskStatusDTO;
import com.jjx.kanban.enums.KanbanTaskStatusEnum;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import com.jjx.system.domain.entity.SysTask;
import com.jjx.system.mapper.SysTaskMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 看板任务接口 — 供 jjx-kanban（已合并进 jjx-web）读取
 * 统一入口：GET /kanban/board/{module}/tasks
 *   office/emergency/dev → sys_task 表
 *   production → production_order 表（进行中的生产工单）
 */
@Tag(name = "看板任务")
@RestController
@RequestMapping("/kanban/board")
@RequiredArgsConstructor
public class BoardTaskController {

    private final SysTaskMapper sysTaskMapper;
    private final ProductionOrderMapper productionOrderMapper;
    private final ProductionOperationExecutionMapper executionMapper;

    @Operation(summary = "按模块获取看板任务（支持状态分页+查询条件）")
    @GetMapping("/{module}/tasks")
    public Result<?> getBoardTasks(
            @PathVariable String module,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        if ("production".equals(module)) {
            return Result.success(fetchProductionTasks());
        }
        LambdaQueryWrapper<SysTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTask::getKanbanModule, module);
        if (status != null) {
            wrapper.eq(SysTask::getStatus, status);
        }
        if (StringUtils.hasText(priority)) {
            wrapper.eq(SysTask::getPriority, priority);
        }
        // 关键字：标题/描述 模糊匹配
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysTask::getTitle, keyword)
                    .or().like(SysTask::getDescription, keyword));
        }
        // 负责人模糊匹配
        if (StringUtils.hasText(assignee)) {
            wrapper.like(SysTask::getAssigneeName, assignee);
        }
        wrapper.orderByDesc(SysTask::getUpdateTime).orderByDesc(SysTask::getCreateTime);

        // 兼容：不传 pageNum/pageSize 时返回全量数组（EventPanel 等调用方）
        if (pageNum == null || pageSize == null) {
            return Result.success(sysTaskMapper.selectList(wrapper));
        }

        // 分页
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysTask> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.core.metadata.IPage<SysTask> pageResult = sysTaskMapper.selectPage(page, wrapper);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("records", pageResult.getRecords());
        data.put("total", pageResult.getTotal());
        return Result.success(data);
    }

    /**
     * 生产工单 → 看板卡片格式
     * 状态: 2已审核/6进行中/7已暂停（进行中工单）
     * 优先级: URGENT/HIGH/MEDIUM/LOW → urgent/high/normal/low
     */
    private List<Map<String, Object>> fetchProductionTasks() {
        List<ProductionOrder> orders = productionOrderMapper.selectList(
                new LambdaQueryWrapper<ProductionOrder>()
                        .eq(ProductionOrder::getOrderType, "WORK_ORDER")
                        .in(ProductionOrder::getOrderStatus, 2, 6, 7)
                        .orderByAsc(ProductionOrder::getPlanEndDate)
                        .last("LIMIT 100")
        );
        return orders.stream().map(order -> {
            Map<String, Object> card = new HashMap<>();
            card.put("taskId", order.getOrderId());
            card.put("title", order.getProductName());
            card.put("workOrderNo", order.getOrderNo());
            card.put("productName", order.getProductName());
            card.put("quantity", order.getPlannedQuantity());
            card.put("priority", mapPriority(order.getPriority()));
            card.put("status", mapOrderStatus(order.getOrderStatus()));
            card.put("assigneeName", order.getCreateBy());
            card.put("deadline", order.getPlanEndDate() != null ? order.getPlanEndDate().toString() : null);
            card.put("taskType", "production");
            card.put("kanbanModule", "production");
            card.put("currentProcess", getCurrentProcess(order.getOrderId()));
            return card;
        }).collect(Collectors.toList());
    }

    /**
     * 获取当前工序（未完成的工序里最靠前的）
     * process_id: 1印刷 2冲切 3贴合 4SMT贴片 5装配 6测试 7包装
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

    private String mapOrderStatus(Integer status) {
        if (status == null) return "pending";
        return switch (status) {
            case 6 -> "in_progress";   // 进行中
            case 2 -> "review";        // 已审核→待审核
            case 8 -> "completed";     // 已完成
            case 7, 11 -> "blocked";   // 已暂停/已超期
            default -> "pending";      // 草稿/待审核/已计划/待开始
        };
    }

    private String mapPriority(String priority) {
        if (priority == null) return "normal";
        return switch (priority) {
            case "URGENT" -> "urgent";
            case "HIGH" -> "high";
            case "MEDIUM" -> "normal";
            case "LOW" -> "low";
            default -> "normal";
        };
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{module}/tasks/{taskId}")
    public Result<SysTask> getTaskDetail(@PathVariable String module, @PathVariable Long taskId) {
        if ("production".equals(module)) {
            return Result.error("生产工单详情请走生产订单接口");
        }
        SysTask task = sysTaskMapper.selectById(taskId);
        if (task == null || !module.equals(task.getKanbanModule())) {
            return Result.error("任务不存在");
        }
        return Result.success(task);
    }

    @Operation(summary = "新建看板任务")
    @PostMapping("/{module}/tasks")
    public Result<Long> createTask(@PathVariable String module, @RequestBody SysTask task) {
        task.setTaskId(null);
        task.setKanbanModule(module);
        if (!StringUtils.hasText(task.getTaskCode())) {
            task.setTaskCode(module + "-" + System.currentTimeMillis());
        }
        if (!StringUtils.hasText(task.getTaskType())) {
            task.setTaskType("general");
        }
        if (task.getPriority() == null) {
            task.setPriority("normal");
        }
        if (task.getStatus() == null) {
            task.setStatus(0);
        }
        task.setCreateTime(LocalDateTime.now());
        sysTaskMapper.insert(task);
        return Result.success(task.getTaskId());
    }

    @Operation(summary = "更新看板任务状态")
    @Log(module = "看板任务", businessType = BusinessType.UPDATE,
            bizType = "'kanban_task'", bizId = "#taskId",
            bizStatus = "T(com.jjx.kanban.enums.KanbanTaskStatusEnum).getByValue(#dto.status)?.label")
    @PatchMapping("/{module}/tasks/{taskId}/status")
    public Result<Boolean> updateTaskStatus(@PathVariable String module, @PathVariable Long taskId,
                                            @RequestBody BoardTaskStatusDTO dto) {
        if ("production".equals(module)) {
            return Result.error("生产工单不允许在此修改");
        }
        SysTask task = findTask(module, taskId);
        if (task == null) {
            return Result.error("任务不存在");
        }
        if (KanbanTaskStatusEnum.getByValue(dto.getStatus()) == null) {
            return Result.error("非法状态");
        }
        task.setStatus(dto.getStatus());
        task.setUpdateTime(LocalDateTime.now());
        sysTaskMapper.updateById(task);
        return Result.success(true);
    }

    @Operation(summary = "更新看板任务内容")
    @Log(module = "看板任务", businessType = BusinessType.UPDATE,
            bizType = "'kanban_task'", bizId = "#taskId",
            bizStatus = "T(com.jjx.kanban.enums.KanbanTaskStatusEnum).getByValue(#result.data)?.label")
    @PatchMapping("/{module}/tasks/{taskId}/info")
    public Result<Integer> updateTaskInfo(@PathVariable String module, @PathVariable Long taskId,
                                          @RequestBody BoardTaskInfoDTO dto) {
        if ("production".equals(module)) {
            return Result.error("生产工单不允许在此修改");
        }
        SysTask task = findTask(module, taskId);
        if (task == null) {
            return Result.error("任务不存在");
        }
        if (dto.getTitle() != null) task.setTitle(dto.getTitle());
        if (dto.getDescription() != null) task.setDescription(dto.getDescription());
        if (dto.getPriority() != null) task.setPriority(dto.getPriority());
        if (dto.getAssigneeName() != null) task.setAssigneeName(dto.getAssigneeName());
        if (dto.getDeadline() != null) task.setDeadline(dto.getDeadline());
        if (dto.getRemark() != null) task.setRemark(dto.getRemark());
        task.setUpdateTime(LocalDateTime.now());
        sysTaskMapper.updateById(task);
        SysTask updatedTask = sysTaskMapper.selectById(taskId);
        return Result.success(updatedTask.getStatus());
    }

    private SysTask findTask(String module, Long taskId) {
        SysTask task = sysTaskMapper.selectById(taskId);
        return task != null && module.equals(task.getKanbanModule()) ? task : null;
    }
}
