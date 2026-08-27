package com.jjx.production;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.OrderStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.ProductionTaskService;
import com.jjx.production.service.QualityActionService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionOperationExecutionCompletionTest {

    @Mock ProductionOperationExecutionMapper executionMapper;
    @Mock ProductionOrderMapper orderMapper;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock WorkReportProjectionService projectionService;
    @Mock QualityActionService qualityActionService;
    @Mock ProductionTaskService productionTaskService;

    private ProductionOperationExecutionServiceImpl service;

    @BeforeAll
    static void initializeMybatisTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "completion-test"),
                ProductionOperationExecution.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        var ctor = ProductionOperationExecutionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = spy((ProductionOperationExecutionServiceImpl) ctor.newInstance(
                executionMapper, orderMapper, jdbcTemplate, projectionService,
                qualityActionService, productionTaskService));
    }

    @Test
    void completesAndCreatesFqcWhenNoOtherExecutionIsUnfinished() {
        ProductionOperationExecution execution = executing(11L, 22L);
        doReturn(execution).when(service).getById(11L);
        doReturn(true).when(service).update(any(Wrapper.class));
        doReturn(0L).when(service).count(any(Wrapper.class));

        assertTrue(service.completeExecution(11L));

        verify(productionTaskService).assertExecutionCompletable(11L);
        verify(qualityActionService).createFqcForExecution(11L);
    }

    @Test
    void doesNotCreateFqcWhileAnotherExecutionIsUnfinished() {
        ProductionOperationExecution execution = executing(11L, 22L);
        doReturn(execution).when(service).getById(11L);
        doReturn(true).when(service).update(any(Wrapper.class));
        doReturn(1L).when(service).count(any(Wrapper.class));

        assertTrue(service.completeExecution(11L));

        verify(qualityActionService, never()).createFqcForExecution(any());
    }

    @Test
    void rejectsCompletionUnlessExecutionIsExecuting() {
        ProductionOperationExecution execution = executing(11L, 22L);
        execution.setExecutionStatus(ExecutionStatusEnum.PAUSED.getCode());
        doReturn(execution).when(service).getById(11L);

        assertThrows(BusinessException.class, () -> service.completeExecution(11L));
        verify(productionTaskService, never()).assertExecutionCompletable(any());
    }

    @Test
    void rejectsStartingExecutionBeforeOrderIsInProgress() {
        ProductionOperationExecution execution = executing(11L, 22L);
        execution.setExecutionStatus(ExecutionStatusEnum.PENDING.getCode());
        doReturn(execution).when(service).getById(11L);
        ProductionOrder order = order(22L, OrderStatusEnum.PLANNED);
        when(orderMapper.selectById(22L)).thenReturn(order);

        BusinessException error = assertThrows(BusinessException.class, () -> service.startExecution(11L));

        assertTrue(error.getMessage().contains("请先启动生产工单"));
        assertTrue(error.getMessage().contains("已计划"));
    }

    @Test
    void startsExecutionAfterOrderIsInProgress() {
        ProductionOperationExecution execution = executing(11L, 22L);
        execution.setExecutionStatus(ExecutionStatusEnum.PENDING.getCode());
        doReturn(execution).when(service).getById(11L);
        when(orderMapper.selectById(22L)).thenReturn(order(22L, OrderStatusEnum.IN_PROGRESS));
        doReturn(true).when(service).updateById(execution);

        assertTrue(service.startExecution(11L));
        assertEquals(ExecutionStatusEnum.EXECUTING.getCode(), execution.getExecutionStatus());
    }

    private ProductionOperationExecution executing(Long executionId, Long orderId) {
        ProductionOperationExecution execution = new ProductionOperationExecution();
        execution.setExecutionId(executionId);
        execution.setOrderId(orderId);
        execution.setExecutionStatus(ExecutionStatusEnum.EXECUTING.getCode());
        return execution;
    }

    private ProductionOrder order(Long orderId, OrderStatusEnum status) {
        ProductionOrder order = new ProductionOrder();
        order.setOrderId(orderId);
        order.setOrderStatus(status.getCode());
        return order;
    }
}
