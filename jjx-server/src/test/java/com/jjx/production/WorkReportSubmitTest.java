package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.WorkReportReadService;
import com.jjx.production.service.impl.WorkReportActionServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * P2-C 回归测试：SUBMIT 报工核心规则
 * - 有 add 权限提交成功，保存 execution/reporter 锚点
 * - 无 add 权限不可报
 * - 数量校验（负数/0+0/超计划允许/不良缺原因）
 */
class WorkReportSubmitTest {

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
        var jdbcTemplate = mock(JdbcTemplate.class);
        // orderNoOf 等辅助查询返回空
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(new java.util.ArrayList<>());
        service = (WorkReportActionServiceImpl) ctor.newInstance(workReportMapper, executionMapper,
                projectionService, readService, jdbcTemplate,
                mock(com.jjx.production.service.QualityInspectionService.class));
    }

    private ProductionOperationExecution exec(int status) {
        ProductionOperationExecution e = new ProductionOperationExecution();
        e.setExecutionId(3L);
        e.setOrderId(2L);
        e.setExecutionStatus(status);
        e.setEquipmentId(7L);
        e.setEquipmentName("印刷机1");
        return e;
    }

    private WorkReportSubmitDTO dto() {
        WorkReportSubmitDTO d = new WorkReportSubmitDTO();
        d.setExecutionId(3L);
        d.setQualifiedQuantity(new BigDecimal("950"));
        d.setDefectiveQuantity(new BigDecimal("50"));
        d.setLaborHours(new BigDecimal("2.5"));
        d.setMachineHours(new BigDecimal("2"));
        d.setDefectReason("边缘毛刺");
        return d;
    }

    @Test
    void submitSuccessSavesCoreFactAnchors() {
        when(executionMapper.selectById(3L)).thenReturn(exec(ExecutionStatusEnum.EXECUTING.getCode()));
        when(workReportMapper.insert(any(ProductionWorkReport.class))).thenAnswer(inv -> {
            ((ProductionWorkReport) inv.getArgument(0)).setReportId(10L);
            return 1;
        });
        var vo = new com.jjx.production.domain.vo.WorkReportVO();
        vo.setReportId(10L);
        when(readService.getById(10L)).thenReturn(vo);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(true);
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            var result = service.submit(dto(), "印刷一组工人", 104L);
            assertNotNull(result);
            assertEquals(10L, result.getReportId());
        }

        // 锚点保存：execution/reporter/数量/状态
        var cap = ArgumentCaptor.forClass(ProductionWorkReport.class);
        verify(workReportMapper).insert(cap.capture());
        ProductionWorkReport r = cap.getValue();
        assertEquals(3L, r.getExecutionId());
        assertEquals(104L, r.getReporterId());
        assertEquals("印刷一组工人", r.getReporterName());
        assertEquals(new BigDecimal("950"), r.getQualifiedQuantity());
        assertEquals(new BigDecimal("50"), r.getDefectiveQuantity());
        assertEquals(WorkReportStatusEnum.SUBMITTED.getCode(), r.getReportStatus());
        assertNotNull(r.getReportTime());
        // 客户端不能决定 status/reportTime：reportStatus 由后端设置
        assertEquals(WorkReportStatusEnum.SUBMITTED.getCode(), r.getReportStatus());
        // projection 重算调用
        verify(projectionService).recalculate(3L);
    }

    @Test
    void noAddPermissionCannotSubmit() {
        when(executionMapper.selectById(3L)).thenReturn(exec(ExecutionStatusEnum.EXECUTING.getCode()));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            assertThrows(BusinessException.class, () -> service.submit(dto(), "印刷一组工人", 104L));
        }
        verify(workReportMapper, never()).insert(any(ProductionWorkReport.class));
    }

    @Test
    void notStartedOrCompletedCannotSubmit() {
        // WAITING(0) 不可报工
        when(executionMapper.selectById(3L)).thenReturn(exec(0));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(true);
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.submit(dto(), "印刷一组工人", 104L));
            assertTrue(ex.getMessage().contains("状态不允许报工"));
        }
        // COMPLETED(4) 不可报工
        when(executionMapper.selectById(3L)).thenReturn(exec(ExecutionStatusEnum.COMPLETED.getCode()));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(true);
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            assertThrows(BusinessException.class, () -> service.submit(dto(), "印刷一组工人", 104L));
        }
    }

    @Test
    void quantityValidation() {
        // 负数拒绝
        when(executionMapper.selectById(3L)).thenReturn(exec(ExecutionStatusEnum.EXECUTING.getCode()));
        WorkReportSubmitDTO neg = dto();
        neg.setQualifiedQuantity(new BigDecimal("-1"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(true);
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            assertThrows(BusinessException.class, () -> service.submit(neg, "印刷一组工人", 104L));
        }
        // 0+0 拒绝
        WorkReportSubmitDTO zero = dto();
        zero.setQualifiedQuantity(BigDecimal.ZERO);
        zero.setDefectiveQuantity(BigDecimal.ZERO);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(true);
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.submit(zero, "印刷一组工人", 104L));
            assertTrue(ex.getMessage().contains("之和必须大于 0"));
        }
        // 超计划允许（无 planned 校验）
        WorkReportSubmitDTO over = dto();
        over.setQualifiedQuantity(new BigDecimal("99999"));
        when(workReportMapper.insert(any(ProductionWorkReport.class))).thenAnswer(inv -> {
            ((ProductionWorkReport) inv.getArgument(0)).setReportId(11L);
            return 1;
        });
        when(readService.getById(any())).thenReturn(new com.jjx.production.domain.vo.WorkReportVO());
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(true);
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            assertDoesNotThrow(() -> service.submit(over, "印刷一组工人", 104L));
        }
    }

    @Test
    void defectiveRequiresReason() {
        when(executionMapper.selectById(3L)).thenReturn(exec(ExecutionStatusEnum.EXECUTING.getCode()));
        WorkReportSubmitDTO noReason = dto();
        noReason.setDefectReason(null);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(true);
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.submit(noReason, "印刷一组工人", 104L));
            assertTrue(ex.getMessage().contains("不良原因必填"));
        }
    }
}
