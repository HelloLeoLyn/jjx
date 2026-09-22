package com.jjx.production;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.service.ProductionOperationRecordService;
import com.jjx.production.service.ProductionTaskService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl;
import com.jjx.quality.service.QualityLotService;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class OrderLevelCompletionTest {

    static {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "order-level-completion-test"),
                ProductionOperationExecution.class);
    }

    @Test
    void nonRootOwnerWithoutAdminIsRejectedWithPermissionMessage() {
        Fixture fixture = fixture(execution(11L, "冲压", 1, ExecutionStatusEnum.EXECUTING));
        when(fixture.taskService.getRootAssigneeId(11L)).thenReturn(200L);

        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(100L);
            security.when(() -> SecurityUtils.hasRole("admin")).thenReturn(false);

            BusinessException error = assertThrows(BusinessException.class,
                    () -> fixture.service.completeOrderExecutions(10L));
            assertTrue(error.getMessage().contains("一级负责人") || error.getMessage().contains("仅超级管理员"));
        }
    }

    @Test
    void rootOwnerAndAdminAreBothAllowedPastPermissionGate() {
        Fixture fixture = fixture(execution(11L, "冲压", 1, ExecutionStatusEnum.EXECUTING));
        when(fixture.taskService.getRootAssigneeId(11L)).thenReturn(100L);

        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(100L);
            security.when(() -> SecurityUtils.hasRole("admin")).thenReturn(false);
            assertTrue(fixture.service.completeOrderExecutions(10L));

            fixture.mapper.open.clear();
            fixture.mapper.updatedExecutionIds.clear();
            fixture.mapper.open.add(execution(11L, "冲压", 1, ExecutionStatusEnum.EXECUTING));
            security.when(SecurityUtils::getUserId).thenReturn(999L);
            security.when(() -> SecurityUtils.hasRole("admin")).thenReturn(true);
            assertTrue(fixture.service.completeOrderExecutions(10L));
        }
    }

    @Test
    void blockersFromAllProcessesAreAggregated() {
        Fixture fixture = fixture(
                execution(11L, "冲压", 1, ExecutionStatusEnum.EXECUTING),
                execution(12L, "装配", 2, ExecutionStatusEnum.EXECUTING));
        when(fixture.taskService.getRootAssigneeId(11L)).thenReturn(100L);
        when(fixture.taskService.getRootAssigneeId(12L)).thenReturn(100L);
        when(fixture.taskService.executionCompletionBlockers(11L)).thenReturn(List.of("冲压报工未审批"));
        when(fixture.taskService.executionCompletionBlockers(12L)).thenReturn(List.of("装配任务未完成"));

        try (var security = loggedInAs(100L, false)) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> fixture.service.completeOrderExecutions(10L));
            assertTrue(error.getMessage().contains("冲压"));
            assertTrue(error.getMessage().contains("装配"));
            assertTrue(error.getMessage().contains("未审批"));
            assertTrue(error.getMessage().contains("未完成"));
        }
    }

    @Test
    void nonExecutingProcessIsBlockedWithProcessName() {
        Fixture fixture = fixture(execution(11L, "预处理", 1, ExecutionStatusEnum.PREPARING));
        when(fixture.taskService.getRootAssigneeId(11L)).thenReturn(100L);

        try (var security = loggedInAs(100L, false)) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> fixture.service.completeOrderExecutions(10L));
            assertTrue(error.getMessage().contains("预处理"));
        }
    }

    @Test
    void allExecutingProcessesAreCompletedAndRootsAreClosed() {
        Fixture fixture = fixture(
                execution(11L, "冲压", 1, ExecutionStatusEnum.EXECUTING),
                execution(12L, "装配", 2, ExecutionStatusEnum.EXECUTING));
        when(fixture.taskService.getRootAssigneeId(11L)).thenReturn(100L);
        when(fixture.taskService.getRootAssigneeId(12L)).thenReturn(100L);
        when(fixture.taskService.executionCompletionBlockers(11L)).thenReturn(List.of());
        when(fixture.taskService.executionCompletionBlockers(12L)).thenReturn(List.of());

        try (var security = loggedInAs(100L, false)) {
            assertTrue(fixture.service.completeOrderExecutions(10L));
        }

        assertEquals(List.of(11L, 12L), fixture.mapper.updatedExecutionIds);
        verify(fixture.taskService).completeRootForExecution(11L);
        verify(fixture.taskService).completeRootForExecution(12L);
        // 当前生产实现没有质量批创建调用；该缺口在报告中说明，不在测试中改生产代码。
        verify(fixture.qualityLotService, never()).createLot(any());
    }

    @Test
    void emptyOpenListAndNullOrderIdAreRejected() {
        Fixture empty = fixture();
        try (var security = loggedInAs(100L, false)) {
            BusinessException noOpen = assertThrows(BusinessException.class,
                    () -> empty.service.completeOrderExecutions(10L));
            assertTrue(noOpen.getMessage().contains("没有待完成的工序"));
        }

        Fixture nullId = fixture();
        BusinessException nullOrder = assertThrows(BusinessException.class,
                () -> nullId.service.completeOrderExecutions(null));
        assertTrue(nullOrder.getMessage().contains("工单ID不能为空"));
    }

    private static MockedStaticSecurity loggedInAs(Long userId, boolean admin) {
        var security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(userId);
        security.when(() -> SecurityUtils.hasRole("admin")).thenReturn(admin);
        return new MockedStaticSecurity(security);
    }

    private static Fixture fixture(ProductionOperationExecution... executions) {
        ExecutionMapperStub mapper = new ExecutionMapperStub(List.of(executions));
        ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
        when(orderMapper.selectById(10L)).thenReturn(new ProductionOrder());
        ProductionTaskService taskService = mock(ProductionTaskService.class);
        QualityLotService qualityLotService = mock(QualityLotService.class);
        ProductionOperationExecutionServiceImpl service = new ProductionOperationExecutionServiceImpl(
                mapper.proxy(), orderMapper, mock(JdbcTemplate.class), mock(WorkReportProjectionService.class),
                taskService, mock(ProductionOperationRecordService.class), qualityLotService);
        try {
            Field baseMapper = com.baomidou.mybatisplus.extension.repository.CrudRepository.class
                    .getDeclaredField("baseMapper");
            baseMapper.setAccessible(true);
            baseMapper.set(service, mapper.proxy());
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
        return new Fixture(service, mapper, taskService, qualityLotService);
    }

    private static ProductionOperationExecution execution(Long id, String name, int order, ExecutionStatusEnum status) {
        ProductionOperationExecution execution = new ProductionOperationExecution();
        execution.setExecutionId(id);
        execution.setProcessName(name);
        execution.setProcessOrder(order);
        execution.setExecutionStatus(status.getValue());
        return execution;
    }

    private record Fixture(ProductionOperationExecutionServiceImpl service, ExecutionMapperStub mapper,
                           ProductionTaskService taskService, QualityLotService qualityLotService) {
    }

    private static final class ExecutionMapperStub {
        private final List<ProductionOperationExecution> open;
        private final List<Long> updatedExecutionIds = new ArrayList<>();

        private ExecutionMapperStub(List<ProductionOperationExecution> open) {
            this.open = new ArrayList<>(open);
        }

        private ProductionOperationExecutionMapper proxy() {
            return (ProductionOperationExecutionMapper) Proxy.newProxyInstance(
                    ProductionOperationExecutionMapper.class.getClassLoader(),
                    new Class<?>[]{ProductionOperationExecutionMapper.class},
                    (ignored, method, args) -> {
                        if (method.getName().equals("selectList")) {
                            return open;
                        }
                        if (method.getName().equals("update")) {
                            updatedExecutionIds.add(open.get(updatedExecutionIds.size()).getExecutionId());
                            return 1;
                        }
                        if (method.getName().equals("toString")) {
                            return "OrderLevelCompletionTestMapper";
                        }
                        return null;
                    });
        }

    }

    private static final class MockedStaticSecurity implements AutoCloseable {
        private final org.mockito.MockedStatic<SecurityUtils> delegate;

        private MockedStaticSecurity(org.mockito.MockedStatic<SecurityUtils> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
