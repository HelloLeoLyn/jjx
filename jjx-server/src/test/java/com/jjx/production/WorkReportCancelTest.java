package com.jjx.production;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.WorkReportCancelDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionDispatchMapper;
import com.jjx.production.mapper.ProductionDispatchNodeMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.WorkReportReadService;
import com.jjx.production.service.impl.WorkReportActionServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P2-C 回归测试：CANCEL 报工核心规则
 * - 权限（work-report:cancel）+ 业务关系（reporter 本人/超管）
 * - 已完成 execution 拒绝撤销
 * - 重复 cancel 幂等
 * - 条件更新防并发
 */
class WorkReportCancelTest {

    private WorkReportActionServiceImpl service;
    private ProductionWorkReportMapper workReportMapper;
    private ProductionOperationExecutionMapper executionMapper;
    private WorkReportProjectionService projectionService;
    private WorkReportReadService readService;

    @BeforeEach
    void setUp() throws Exception {
        workReportMapper = mock(ProductionWorkReportMapper.class);
        executionMapper = mock(ProductionOperationExecutionMapper.class);
        projectionService = mock(WorkReportProjectionService.class);
        readService = mock(WorkReportReadService.class);
        var ctor = WorkReportActionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (WorkReportActionServiceImpl) ctor.newInstance(workReportMapper, executionMapper,
                mock(ProductionDispatchMapper.class), mock(ProductionDispatchNodeMapper.class),
                mock(com.jjx.system.mapper.SysUserMapper.class), projectionService, readService, null,
                mock(com.jjx.production.service.QualityInspectionService.class));
    }

    private ProductionWorkReport submittedReport() {
        ProductionWorkReport r = new ProductionWorkReport();
        r.setReportId(10L);
        r.setExecutionId(3L);
        r.setReporterId(104L);
        r.setReporterName("印刷一组工人");
        r.setReportStatus(WorkReportStatusEnum.SUBMITTED.getCode());
        r.setQualifiedQuantity(new java.math.BigDecimal("950"));
        return r;
    }

    private ProductionOperationExecution exec(int status) {
        ProductionOperationExecution e = new ProductionOperationExecution();
        e.setExecutionId(3L);
        e.setExecutionStatus(status);
        return e;
    }

    private WorkReportCancelDTO cancelDto() {
        WorkReportCancelDTO d = new WorkReportCancelDTO();
        d.setCancelReason("报错重报");
        return d;
    }

    @Test
    void reporterWithCancelPermissionCanCancel() {
        ProductionWorkReport r = submittedReport();
        when(workReportMapper.selectById(10L)).thenReturn(r);
        when(executionMapper.selectById(3L)).thenReturn(exec(ExecutionStatusEnum.EXECUTING.getCode()));
        when(workReportMapper.update(any(), any())).thenReturn(1);
        var vo = new com.jjx.production.domain.vo.WorkReportVO();
        vo.setReportId(10L);
        when(readService.getById(10L)).thenReturn(vo);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:cancel")).thenReturn(true);
            var result = service.cancel(10L, cancelDto(), "印刷一组工人", 104L);
            assertNotNull(result);
        }
        // 条件更新：SUBMITTED→CANCELLED
        var cap = org.mockito.ArgumentCaptor.forClass(ProductionWorkReport.class);
        verify(workReportMapper).update(cap.capture(), any());
        assertEquals(WorkReportStatusEnum.CANCELLED.getCode(), cap.getValue().getReportStatus());
        assertNotNull(cap.getValue().getCancelledAt());
        assertEquals("报错重报", cap.getValue().getCancelReason());
        verify(projectionService).recalculate(3L);
    }

    @Test
    void noCancelPermissionDenied() {
        when(workReportMapper.selectById(10L)).thenReturn(submittedReport());
        when(executionMapper.selectById(3L)).thenReturn(exec(ExecutionStatusEnum.EXECUTING.getCode()));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:cancel")).thenReturn(false);
            assertThrows(BusinessException.class, () -> service.cancel(10L, cancelDto(), "印刷一组工人", 104L));
        }
        verify(workReportMapper, never()).update(any(), any());
    }

    @Test
    void nonReporterWithoutAdminDenied() {
        when(workReportMapper.selectById(10L)).thenReturn(submittedReport());
        when(executionMapper.selectById(3L)).thenReturn(exec(ExecutionStatusEnum.EXECUTING.getCode()));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:cancel")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.cancel(10L, cancelDto(), "路人", 99L));
            assertTrue(ex.getMessage().contains("提交人本人或管理员"));
        }
    }

    @Test
    void completedExecutionCannotCancel() {
        when(workReportMapper.selectById(10L)).thenReturn(submittedReport());
        when(executionMapper.selectById(3L)).thenReturn(exec(ExecutionStatusEnum.COMPLETED.getCode()));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:cancel")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.cancel(10L, cancelDto(), "印刷一组工人", 104L));
            assertTrue(ex.getMessage().contains("已完成"));
        }
    }

    @Test
    void repeatedCancelIsIdempotent() {
        // 已 CANCELLED：条件更新 affectedRows=0 → 重新读取发现已撤销 → 幂等返回
        ProductionWorkReport cancelled = submittedReport();
        cancelled.setReportStatus(WorkReportStatusEnum.CANCELLED.getCode());
        when(workReportMapper.selectById(10L)).thenReturn(cancelled);
        when(executionMapper.selectById(3L)).thenReturn(exec(ExecutionStatusEnum.EXECUTING.getCode()));
        when(workReportMapper.update(any(), any())).thenReturn(0); // 条件更新未命中
        var vo = new com.jjx.production.domain.vo.WorkReportVO();
        vo.setReportId(10L);
        when(readService.getById(10L)).thenReturn(vo);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:cancel")).thenReturn(true);
            // 幂等：不抛异常，返回当前状态；且不重算 projection（rows=0 分支直接返回）
            var result = service.cancel(10L, cancelDto(), "印刷一组工人", 104L);
            assertNotNull(result);
        }
        // 幂等分支不重算 projection
        verify(projectionService, never()).recalculate(any());
    }

    @Test
    void concurrentStatusChangeThrows() {
        // affectedRows=0 但重新读取仍是 SUBMITTED → 并发冲突
        when(workReportMapper.selectById(10L)).thenReturn(submittedReport());
        when(executionMapper.selectById(3L)).thenReturn(exec(ExecutionStatusEnum.EXECUTING.getCode()));
        when(workReportMapper.update(any(), any())).thenReturn(0);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:cancel")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.cancel(10L, cancelDto(), "印刷一组工人", 104L));
            assertTrue(ex.getMessage().contains("状态已变化"));
        }
    }
}
