package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.notification.service.NotificationService;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionTask;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.WorkReportVO;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionTaskMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.ProductionRoleResolver;
import com.jjx.production.service.ProductionTaskService;
import com.jjx.production.service.QualityInspectionService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.WorkReportReadService;
import com.jjx.production.service.impl.WorkReportActionServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkReportSnapshotProxyTest {

    private ProductionWorkReportMapper workReportMapper;
    private ProductionOperationExecutionMapper executionMapper;
    private ProductionTaskMapper taskMapper;
    private ProductionTaskService taskService;
    private WorkReportProjectionService projectionService;
    private WorkReportReadService readService;
    private ProductionRoleResolver roleResolver;
    private JdbcTemplate jdbcTemplate;
    private WorkReportActionServiceImpl service;

    @BeforeEach
    void setUp() {
        workReportMapper = mock(ProductionWorkReportMapper.class);
        executionMapper = mock(ProductionOperationExecutionMapper.class);
        taskMapper = mock(ProductionTaskMapper.class);
        taskService = mock(ProductionTaskService.class);
        projectionService = mock(WorkReportProjectionService.class);
        readService = mock(WorkReportReadService.class);
        roleResolver = mock(ProductionRoleResolver.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new WorkReportActionServiceImpl(workReportMapper, executionMapper, taskMapper,
                taskService, projectionService, readService, mock(QualityInspectionService.class),
                jdbcTemplate, mock(NotificationService.class),
                mock(com.jjx.framework.common.RedisSequenceService.class),
                roleResolver);
    }

    @Test
    void submitByAssigneeKeepsPersonalReportAndSnapshotsReviewer() {
        stubSubmitContext(10L, 20L, 30L);
        stubUserNames(Map.of(30L, "组长"));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getRealName).thenReturn("工人甲");
            service.submit(submitDto(null), "worker-a", 10L);
        }

        ProductionWorkReport saved = insertedReport();
        assertEquals(10L, saved.getReporterId());
        assertEquals("工人甲", saved.getReporterName());
        assertNull(saved.getProxyId());
        assertNull(saved.getProxyName());
        assertEquals(30L, saved.getPendingReviewerId());
        assertEquals("组长", saved.getPendingReviewerName());
    }

    @Test
    void submitWithoutProxyPermissionIsRejected() {
        stubTask(10L, 20L);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(() -> SecurityUtils.hasPermission("production:work-report:proxy")).thenReturn(false);
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.submit(submitDto(10L), "proxy", 99L));
            assertTrue(error.getMessage().contains("只有任务当前执行人可以报工"));
        }
        verify(workReportMapper, never()).insert(any(ProductionWorkReport.class));
    }

    @Test
    void submitWithProxyPermissionWritesReporterAndProxySnapshots() {
        stubSubmitContext(10L, 20L, 30L);
        stubUserNames(Map.of(10L, "工人甲", 30L, "组长"));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(() -> SecurityUtils.hasPermission("production:work-report:proxy")).thenReturn(true);
            security.when(SecurityUtils::getRealName).thenReturn("调度员乙");
            service.submit(submitDto(10L), "dispatcher-b", 99L);
        }

        ProductionWorkReport saved = insertedReport();
        assertEquals(10L, saved.getReporterId());
        assertEquals("工人甲", saved.getReporterName());
        assertEquals(99L, saved.getProxyId());
        assertEquals("调度员乙", saved.getProxyName());
    }

    @Test
    void approvalGateUsesSnapshotInsteadOfCurrentParentAssignee() {
        ProductionWorkReport report = pendingReport(30L);
        when(workReportMapper.selectById(1L)).thenReturn(report);

        when(roleResolver.isGlobalProductionScope()).thenReturn(false);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.approve(1L, null, "new-parent", 31L));
        assertTrue(error.getMessage().contains("提交时点审批人"));
        verify(taskMapper, never()).selectById(anyLong());
    }

    @Test
    void approvalGateAllowsSnapshotReviewer() {
        ProductionWorkReport report = pendingReport(30L);
        when(workReportMapper.selectById(1L)).thenReturn(report);
        when(workReportMapper.update(any(), any())).thenReturn(1);
        when(readService.getById(1L)).thenReturn(new WorkReportVO());
        when(roleResolver.isGlobalProductionScope()).thenReturn(false);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getRealName).thenReturn("组长");
            service.approve(1L, null, "leader", 30L);
        }
    }

    @Test
    void emptyReviewerSnapshotFallsBackToProductionManager() {
        ProductionWorkReport report = pendingReport(null);
        when(workReportMapper.selectById(1L)).thenReturn(report);
        when(workReportMapper.update(any(), any())).thenReturn(1);
        when(readService.getById(1L)).thenReturn(new WorkReportVO());
        when(roleResolver.isGlobalProductionScope()).thenReturn(false, true);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getRealName).thenReturn("生产管理员");
            assertThrows(BusinessException.class, () -> service.approve(1L, null, "worker", 10L));
            service.approve(1L, null, "manager", 99L);
        }
    }

    private void stubTask(Long assigneeId, Long parentTaskId) {
        ProductionTask task = new ProductionTask();
        task.setTaskId(1L);
        task.setExecutionId(2L);
        task.setAssigneeId(assigneeId);
        task.setParentTaskId(parentTaskId);
        task.setStatus("ACTIVE");
        when(taskMapper.selectByIdForUpdate(1L)).thenReturn(task);
    }

    private void stubSubmitContext(Long assigneeId, Long parentTaskId, Long parentAssigneeId) {
        stubTask(assigneeId, parentTaskId);
        ProductionTask parent = new ProductionTask();
        parent.setTaskId(parentTaskId);
        parent.setAssigneeId(parentAssigneeId);
        when(taskMapper.selectById(parentTaskId)).thenReturn(parent);

        ProductionOperationExecution execution = new ProductionOperationExecution();
        execution.setExecutionId(2L);
        execution.setOrderId(3L);
        execution.setExecutionStatus(ExecutionStatusEnum.EXECUTING.getValue());
        when(executionMapper.selectById(2L)).thenReturn(execution);
        when(taskService.remainingQuantity(1L)).thenReturn(BigDecimal.TEN);
        when(workReportMapper.insert(any(ProductionWorkReport.class))).thenAnswer(invocation -> {
            ProductionWorkReport report = invocation.getArgument(0);
            report.setReportId(100L);
            return 1;
        });
        when(readService.getById(100L)).thenReturn(new WorkReportVO());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubUserNames(Map<Long, String> names) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    Object queryArg = invocation.getArgument(2);
                    if (sql.contains("FROM sys_user")) {
                        String name = names.get(queryArg);
                        return name == null ? List.of() : List.of(name);
                    }
                    return List.of();
                });
    }

    private WorkReportSubmitDTO submitDto(Long reporterId) {
        WorkReportSubmitDTO dto = new WorkReportSubmitDTO();
        dto.setTaskId(1L);
        dto.setExecutionId(2L);
        dto.setReporterId(reporterId);
        dto.setQualifiedQuantity(BigDecimal.ONE);
        dto.setDefectiveQuantity(BigDecimal.ZERO);
        return dto;
    }

    private ProductionWorkReport insertedReport() {
        ArgumentCaptor<ProductionWorkReport> captor = ArgumentCaptor.forClass(ProductionWorkReport.class);
        verify(workReportMapper).insert(captor.capture());
        return captor.getValue();
    }

    private ProductionWorkReport pendingReport(Long reviewerId) {
        ProductionWorkReport report = new ProductionWorkReport();
        report.setReportId(1L);
        report.setExecutionId(2L);
        report.setTaskId(1L);
        report.setReportStatus(WorkReportStatusEnum.PENDING.getCode());
        report.setPendingReviewerId(reviewerId);
        return report;
    }
}
