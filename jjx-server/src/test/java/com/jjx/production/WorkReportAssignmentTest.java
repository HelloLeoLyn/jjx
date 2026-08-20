package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.domain.entity.ProductionDispatch;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.enums.ExecutionStatusEnum;
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
 * WP-B 回归测试：WorkReport Assignment 集成
 * 覆盖：
 * 14. WorkReport 撤销后 Assignment remaining 重新出现（派生口径）
 * 15. 有 Assignment 后责任人无 Assignment 不能走 legacy 报工
 * 16. 无 Assignment 历史 Execution 仍兼容旧报工（ACTIVE node assignee）
 */
class WorkReportAssignmentTest {

    private WorkReportActionServiceImpl service;
    private ProductionWorkReportMapper workReportMapper;
    private ProductionOperationExecutionMapper executionMapper;
    private ProductionDispatchMapper dispatchMapper;
    private ProductionDispatchNodeMapper nodeMapper;
    private JdbcTemplate jdbcTemplate;

    private ProductionOperationExecution execRow;
    private ProductionDispatch dispatchRow;
    private ProductionDispatchNode activeNodeRow;

    @BeforeEach
    void setUp() throws Exception {
        workReportMapper = mock(ProductionWorkReportMapper.class);
        executionMapper = mock(ProductionOperationExecutionMapper.class);
        dispatchMapper = mock(ProductionDispatchMapper.class);
        nodeMapper = mock(ProductionDispatchNodeMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        Constructor<?> ctor = WorkReportActionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (WorkReportActionServiceImpl) ctor.newInstance(
                workReportMapper, executionMapper, dispatchMapper, nodeMapper,
                mock(com.jjx.system.mapper.SysUserMapper.class),
                mock(WorkReportProjectionService.class), mock(WorkReportReadService.class),
                jdbcTemplate, mock(com.jjx.production.service.QualityInspectionService.class));

        execRow = new ProductionOperationExecution();
        execRow.setExecutionId(1L);
        execRow.setOrderId(5L);
        execRow.setExecutionStatus(ExecutionStatusEnum.EXECUTING.getCode());
        when(executionMapper.selectById(1L)).thenReturn(execRow);

        dispatchRow = new ProductionDispatch();
        dispatchRow.setDispatchId(1L);
        dispatchRow.setExecutionId(1L);
        dispatchRow.setOrderId(5L);
        when(dispatchMapper.selectOne(any())).thenReturn(dispatchRow);

        activeNodeRow = new ProductionDispatchNode();
        activeNodeRow.setNodeId(10L);
        activeNodeRow.setDispatchId(1L);
        activeNodeRow.setAssigneeId(1L);
        activeNodeRow.setAssigneeName("组长");
        activeNodeRow.setNodeStatus("ACTIVE");
        when(nodeMapper.selectOne(any())).thenReturn(activeNodeRow);

        when(workReportMapper.insert(any(com.jjx.production.domain.entity.ProductionWorkReport.class))).thenReturn(1);
    }

    private WorkReportSubmitDTO dto(BigDecimal qualified, BigDecimal defective) {
        WorkReportSubmitDTO d = new WorkReportSubmitDTO();
        d.setExecutionId(1L);
        d.setQualifiedQuantity(qualified);
        d.setDefectiveQuantity(defective);
        d.setLaborHours(BigDecimal.ONE);
        d.setMachineHours(BigDecimal.ONE);
        return d;
    }

    /** 在 SecurityUtils mock 存活期内执行动作 */
    private void withAddPerm(Long operatorId, Runnable action) {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(true);
            mocked.when(SecurityUtils::getUserId).thenReturn(operatorId);
            action.run();
        }
    }

    /** 模拟 jdbcTemplate 返回 ACTIVE assignment 列表 */
    private void mockActiveAssignments(List<Long> assigneeIds, BigDecimal assigned, BigDecimal released) {
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    if (sql.contains("production_execution_assignment")) {
                        List<com.jjx.production.domain.entity.ProductionExecutionAssignment> list = new ArrayList<>();
                        for (Long uid : assigneeIds) {
                            com.jjx.production.domain.entity.ProductionExecutionAssignment a =
                                    new com.jjx.production.domain.entity.ProductionExecutionAssignment();
                            a.setAssignmentId(uid + 100L);
                            a.setAssigneeId(uid);
                            a.setAssignedQuantity(assigned);
                            a.setReleasedQuantity(released);
                            list.add(a);
                        }
                        return list;
                    }
                    if (sql.contains("sys_user")) return new ArrayList<>();
                    return new ArrayList<>();
                });
    }

    private void mockReportedSum(BigDecimal sum) {
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any())).thenReturn(sum);
    }

    // ==================== 15. 有 Assignment 后，责任人无 Assignment 不能 legacy 报工 ====================

    @Test
    void hasAssignment_assigneeWithoutAssignment_cannotReportLegacy() {
        // Execution 有 Assignment（assignee=99 张三），但当前操作人 operatorId=1（ACTIVE node assignee 组长）无 Assignment
        mockActiveAssignments(List.of(99L), new BigDecimal("300"), BigDecimal.ZERO);
        mockReportedSum(BigDecimal.ZERO);

        withAddPerm(1L, () -> { // 组长本人（无 Assignment）
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.submit(dto(new BigDecimal("100"), BigDecimal.ZERO), "组长", 1L));
            assertTrue(ex.getMessage().contains("有效作业分配"), ex.getMessage());
        });
        verify(workReportMapper, never()).insert(any(com.jjx.production.domain.entity.ProductionWorkReport.class));
    }

    // ==================== 15b. 有 Assignment 且 assignee=我 → 正常报工 ====================

    @Test
    void hasAssignment_assigneeIsMe_reportOk() {
        mockActiveAssignments(List.of(1L), new BigDecimal("300"), BigDecimal.ZERO);
        mockReportedSum(new BigDecimal("100"));

        // 累计 100 + 本次 150 = 250 <= 300 → OK
        withAddPerm(1L, () -> assertDoesNotThrow(() ->
                service.submit(dto(new BigDecimal("150"), BigDecimal.ZERO), "组长", 1L)));
        verify(workReportMapper, times(1)).insert(any(com.jjx.production.domain.entity.ProductionWorkReport.class));
    }

    // ==================== 15c. 有 Assignment 但累计超份额 → 拒绝 ====================

    @Test
    void hasAssignment_overAssignment_rejected() {
        mockActiveAssignments(List.of(1L), new BigDecimal("300"), BigDecimal.ZERO);
        mockReportedSum(new BigDecimal("250")); // 已报 250

        // 250 + 本次 60 = 310 > 300 → 拒绝
        withAddPerm(1L, () -> {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.submit(dto(new BigDecimal("60"), BigDecimal.ZERO), "组长", 1L));
            assertTrue(ex.getMessage().contains("超过分配有效数量"), ex.getMessage());
        });
        verify(workReportMapper, never()).insert(any(com.jjx.production.domain.entity.ProductionWorkReport.class));
    }

    // ==================== 16. 无 Assignment 历史 Execution → 旧报工兼容 ====================

    @Test
    void noAssignment_legacyAssignerReportOk() {
        // 无 Assignment（jdbcTemplate 返回空）
        mockActiveAssignments(List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        mockReportedSum(BigDecimal.ZERO);

        // ACTIVE node assignee=1 → 旧报工兼容
        withAddPerm(1L, () -> assertDoesNotThrow(() ->
                service.submit(dto(new BigDecimal("100"), BigDecimal.ZERO), "组长", 1L)));
        verify(workReportMapper, times(1)).insert(any(com.jjx.production.domain.entity.ProductionWorkReport.class));
    }

    // ==================== 16b. 无 Assignment 且非 ACTIVE assignee → 拒绝（旧规则保留） ====================

    @Test
    void noAssignment_notAssignee_rejected() {
        mockActiveAssignments(List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        mockReportedSum(BigDecimal.ZERO);

        // 非 ACTIVE assignee → 拒绝（旧规则保留）
        withAddPerm(99L, () -> {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.submit(dto(new BigDecimal("100"), BigDecimal.ZERO), "路人", 99L));
            assertTrue(ex.getMessage().contains("只有当前责任人"), ex.getMessage());
        });
    }
}
