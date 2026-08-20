package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.OrderStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * WP-B 回归测试：completeExecution Assignment-aware gate
 * 覆盖：
 * 17. 存在未完成 Assignment（remaining>0）→ 拒绝完成
 * 18. unassigned > 0 → 拒绝完成
 * 19. 全部分配 + 全部报完 → 允许进入原 completeExecution 后续 gate
 */
class ExecutionAssignmentCompleteGateTest {

    private ProductionOperationExecutionServiceImpl service;
    private JdbcTemplate jdbcTemplate;
    private ProductionOperationExecutionMapper executionMapper;

    @BeforeEach
    void setUp() throws Exception {
        executionMapper = mock(ProductionOperationExecutionMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        var workReportMapper = mock(com.jjx.production.mapper.ProductionWorkReportMapper.class);
        var projectionService = mock(com.jjx.production.service.WorkReportProjectionService.class);
        var dispatchService = mock(com.jjx.production.service.DispatchService.class);
        var dispatchMapper = mock(com.jjx.production.mapper.ProductionDispatchMapper.class);
        var nodeReadService = mock(com.jjx.production.service.DispatchNodeReadService.class);
        var qualityActionService = mock(com.jjx.production.service.QualityActionService.class);
        var orderMapper = mock(ProductionOrderMapper.class);
        var recordService = mock(com.jjx.production.service.ProductionOperationRecordService.class);
        var routingItemMapper = mock(com.jjx.product.mapper.EngineeringRoutingItemMapper.class);

        // 构造器按字段顺序（从源码 grep 确认，这里用反射 + 全 mock）
        Constructor<?> ctor = ProductionOperationExecutionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        int n = ctor.getParameterCount();
        Object[] args = new Object[n];
        // 按已知字段类型填充 mock
        java.lang.reflect.Parameter[] params = ctor.getParameters();
        for (int i = 0; i < n; i++) {
            Class<?> t = params[i].getType();
            args[i] = mock(t);
        }
        service = (ProductionOperationExecutionServiceImpl) ctor.newInstance(args);

        // 注入 jdbcTemplate（Assignment gate 用）
        java.lang.reflect.Field f = ProductionOperationExecutionServiceImpl.class.getDeclaredField("jdbcTemplate");
        f.setAccessible(true);
        f.set(service, jdbcTemplate);

        // executionMapper 注入（getById 用 ServiceImpl baseMapper，这里反射注入）
        java.lang.reflect.Field bm = null;
        Class<?> c = service.getClass();
        while (c != null && bm == null) {
            try {
                bm = c.getDeclaredField("baseMapper");
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        bm.setAccessible(true);
        bm.set(service, executionMapper);
    }

    private ProductionOperationExecution exec(int status, BigDecimal input) {
        ProductionOperationExecution e = new ProductionOperationExecution();
        e.setExecutionId(1L);
        e.setOrderId(5L);
        e.setExecutionStatus(status);
        e.setInputQuantity(input);
        return e;
    }

    private void mockCommon() {
        ProductionOrder order = new ProductionOrder();
        order.setOrderId(5L);
        order.setOrderStatus(OrderStatusEnum.IN_PROGRESS.getCode());
        // canCompleteExecution 内部用 executionMapper.selectById（baseMapper.selectById）
        // 已通过 baseMapper=executionMapper mock；但 getById 走 ServiceImpl → selectById
        when(executionMapper.selectById(1L)).thenReturn(exec(ExecutionStatusEnum.EXECUTING.getCode(), new BigDecimal("1000")));
        // 至少一条报工（P2 gate）
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);
    }

    // ==================== 17. 存在 remaining>0 Assignment → 拒绝 ====================

    @Test
    void remainingAssignment_blockComplete() throws Exception {
        mockCommon();
        // 有 ACTIVE assignment（activeCnt=1），且 remaining>0（remainingCnt=1）
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    if (sql.contains("> COALESCE")) {
                        return 1L; // remainingCnt = 1（存在剩余）
                    }
                    if (sql.contains("COUNT(*)")) {
                        return 1L; // 有 ACTIVE assignment
                    }
                    return 0L;
                });
        // P2 gate：hasAnySubmitted 用 projectionService mock → 默认 false？需 true
        // 这里直接测 Assignment gate：用反射调 completeExecution 会先走 hasAnySubmitted
        // 简化：不 mock projectionService 具体行为，直接看 Assignment 错误优先于 P2 gate？
        // 顺序：状态 → 完工冻结 → canCompleteExecution → hasAnySubmitted → Assignment gate
        // hasAnySubmitted 在 Assignment gate 之前，需 mock 为 true
        // projectionService 是全 mock → hasAnySubmitted 默认 false → 先报"尚无有效报工"
        // 为测 Assignment gate，让 hasAnySubmitted=true
        // （本项目通过字段注入：找到 projectionService 字段）
        java.lang.reflect.Field pf = ProductionOperationExecutionServiceImpl.class.getDeclaredField("workReportProjectionService");
        pf.setAccessible(true);
        com.jjx.production.service.WorkReportProjectionService ps =
                (com.jjx.production.service.WorkReportProjectionService) pf.get(service);
        when(ps.hasAnySubmitted(1L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.completeExecution(1L));
        assertTrue(ex.getMessage().contains("未完成的作业分配"), ex.getMessage());
    }

    // ==================== 18. unassigned>0 → 拒绝 ====================

    @Test
    void unassignedPositive_blockComplete() throws Exception {
        mockCommon();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    if (sql.contains("> COALESCE")) {
                        return 0L; // remainingCnt = 0（无剩余）
                    }
                    if (sql.contains("COUNT(*)")) {
                        return 1L; // activeCnt = 1（存在 Assignment）
                    }
                    return 0L;
                });
        // assignedSum = 600 < planned 1000 → unassigned>0
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
                .thenReturn(new BigDecimal("600"));

        java.lang.reflect.Field pf = ProductionOperationExecutionServiceImpl.class.getDeclaredField("workReportProjectionService");
        pf.setAccessible(true);
        com.jjx.production.service.WorkReportProjectionService ps =
                (com.jjx.production.service.WorkReportProjectionService) pf.get(service);
        when(ps.hasAnySubmitted(1L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.completeExecution(1L));
        assertTrue(ex.getMessage().contains("未全部分配"), ex.getMessage());
    }

    // ==================== 19. 全分配+全报完 → 进入后续 gate ====================

    @Test
    void allAssignedAllReported_proceedsToLaterGate() throws Exception {
        mockCommon();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    if (sql.contains("> COALESCE")) {
                        return 0L; // remainingCnt = 0（全部报完）
                    }
                    if (sql.contains("COUNT(*)")) {
                        return 1L; // activeCnt = 1
                    }
                    return 0L;
                });
        // assignedSum = 1000 = planned → unassigned=0
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
                .thenReturn(new BigDecimal("1000"));

        java.lang.reflect.Field pf = ProductionOperationExecutionServiceImpl.class.getDeclaredField("workReportProjectionService");
        pf.setAccessible(true);
        com.jjx.production.service.WorkReportProjectionService ps =
                (com.jjx.production.service.WorkReportProjectionService) pf.get(service);
        when(ps.hasAnySubmitted(1L)).thenReturn(true);

        // 后续 gate：canCompleteExecution（status 允许）+ updateById
        when(executionMapper.updateById(any(ProductionOperationExecution.class))).thenReturn(1);
        // updateOrderCompletedQuantity 内部查询 → 容忍异常（try-catch 或无 mock 默认）
        // dispatchService.syncByExecution 等后续在 try-catch 中

        // 应不再抛 Assignment 错误；后续若因其他 mock 空异常，属于测试环境噪音
        // 这里验证：不抛 "未完成的作业分配"/"未全部分配"
        try {
            service.completeExecution(1L);
            // 到达这里说明 Assignment gate 通过
        } catch (BusinessException e) {
            assertFalse(e.getMessage().contains("未完成的作业分配"), "不应因 Assignment remaining 拒绝: " + e.getMessage());
            assertFalse(e.getMessage().contains("未全部分配"), "不应因 unassigned 拒绝: " + e.getMessage());
        }
    }
}
