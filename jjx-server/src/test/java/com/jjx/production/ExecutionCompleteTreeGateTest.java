package com.jjx.production;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionTaskNode;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionTaskNodeMapper;
import com.jjx.production.service.QualityActionService;
import com.jjx.production.service.TaskNodeService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl;
import com.jjx.production.service.impl.TaskNodeServiceImpl;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TT-FINAL-03 最小定向测试：Execution Complete 接 Task Tree 闭环 Gate
 * <p>
 * A 部分：TaskNodeServiceImpl.isExecutionTreeClosed 纯逻辑（内存 mapper）：
 *   - 无节点 / 部分报工 / 下级仍有剩余 / 上级仍持有 → 未闭环
 *   - 全部报工闭环 / 报工+取消全量归还 → 闭环
 *   - recall / return 后重新形成未闭环 → 未闭环
 * B 部分：ProductionOperationExecutionServiceImpl.completeExecution 的 Gate 顺序：
 *   - 无有效报工 → 拒绝（先于闭环 Gate）
 *   - 有报工但任务树未闭环 → 拒绝
 *   - 有报工且任务树闭环 → 允许
 */
class ExecutionCompleteTreeGateTest {

    // ==================== A：isExecutionTreeClosed 纯逻辑 ====================

    private TaskNodeServiceImpl taskNodeService;
    private ProductionTaskNodeMapper taskNodeMapper;
    private final Map<Long, ProductionTaskNode> nodes = new LinkedHashMap<>();
    private long nextNodeId = 100L;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ProductionTaskNode.class);
        taskNodeMapper = mock(ProductionTaskNodeMapper.class);
        var executionMapper = mock(ProductionOperationExecutionMapper.class);
        var workReportMapper = mock(com.jjx.production.mapper.ProductionWorkReportMapper.class);
        var jdbcTemplate = mock(JdbcTemplate.class);

        var ctor = TaskNodeServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        taskNodeService = (TaskNodeServiceImpl) ctor.newInstance(
                taskNodeMapper, executionMapper, mock(SysUserMapper.class), workReportMapper,
                jdbcTemplate, mock(SysDeptMapper.class));

        when(taskNodeMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(nodes.values()));
        when(taskNodeMapper.selectOne(any())).thenAnswer(inv -> {
            AbstractWrapper w = (AbstractWrapper) inv.getArgument(0);
            w.getSqlSegment();
            for (Object v : w.getParamNameValuePairs().values()) {
                if (v instanceof Long id && nodes.containsKey(id)) {
                    return nodes.get(id);
                }
            }
            return nodes.isEmpty() ? null : nodes.values().iterator().next();
        });
        when(taskNodeMapper.selectById(any())).thenAnswer(inv -> nodes.get(inv.getArgument(0)));
        when(taskNodeMapper.insert(any(ProductionTaskNode.class))).thenAnswer(inv -> {
            ProductionTaskNode n = inv.getArgument(0);
            n.setTaskNodeId(nextNodeId++);
            nodes.put(n.getTaskNodeId(), n);
            return 1;
        });
        when(taskNodeMapper.updateById(any(ProductionTaskNode.class))).thenAnswer(inv -> 1);
        when(workReportMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(reportRows));
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(new ArrayList<>());
    }

    private ProductionTaskNode node(Long id, Long parentId, Long assigneeId, BigDecimal taskQty) {
        ProductionTaskNode n = new ProductionTaskNode();
        n.setTaskNodeId(id);
        n.setExecutionId(500L);
        n.setParentNodeId(parentId);
        n.setAssigneeId(assigneeId);
        n.setAssigneeName(assigneeId == null ? null : "用户" + assigneeId);
        n.setTaskQuantity(taskQty);
        n.setRecalledQuantity(BigDecimal.ZERO);
        nodes.put(id, n);
        return n;
    }

    /** 模拟报工：直接向内存 report 表写入 SUBMITTED 行（isExecutionTreeClosed 通过 workReportMapper 汇总） */
    private void reportOn(Long nodeId, BigDecimal qty) {
        com.jjx.production.domain.entity.ProductionWorkReport r =
                new com.jjx.production.domain.entity.ProductionWorkReport();
        r.setReportId(nextNodeId++);
        r.setExecutionId(500L);
        r.setTaskNodeId(nodeId);
        r.setQualifiedQuantity(qty);
        r.setDefectiveQuantity(BigDecimal.ZERO);
        r.setReportStatus(com.jjx.production.enums.WorkReportStatusEnum.SUBMITTED.getCode());
        reportRows.add(r);
    }

    private final List<com.jjx.production.domain.entity.ProductionWorkReport> reportRows = new ArrayList<>();

    // ==================== A 用例 ====================

    @Test
    void noNodes_notClosed() {
        assertFalse(taskNodeService.isExecutionTreeClosed(500L));
    }

    @Test
    void rootStillHolding_notClosed() {
        // 系统根 200，未分配未报工 → 未闭环
        node(1L, null, null, new BigDecimal("200"));
        assertFalse(taskNodeService.isExecutionTreeClosed(500L));
    }

    @Test
    void partialReport_notClosed() {
        // 根 200 → 主任 200；主任仅报工 100 → 未闭环
        node(1L, null, null, new BigDecimal("200"));
        node(2L, 1L, 30L, new BigDecimal("200"));
        reportOn(2L, new BigDecimal("100"));
        assertFalse(taskNodeService.isExecutionTreeClosed(500L));
    }

    @Test
    void childStillHolding_notClosed() {
        // 根 200 → 主任 150（根剩 50）；主任 → 组长 100；组长仍持有 100 → 未闭环
        node(1L, null, null, new BigDecimal("200"));
        node(2L, 1L, 30L, new BigDecimal("150"));
        node(3L, 2L, 40L, new BigDecimal("100"));
        assertFalse(taskNodeService.isExecutionTreeClosed(500L));
    }

    @Test
    void fullyReported_closed() {
        // 根 200 → 主任 200 → 组长 100；主任报 100、组长报 100 → 全链路闭环
        node(1L, null, null, new BigDecimal("200"));
        node(2L, 1L, 30L, new BigDecimal("200"));
        node(3L, 2L, 40L, new BigDecimal("100"));
        reportOn(2L, new BigDecimal("100"));
        reportOn(3L, new BigDecimal("100"));
        assertTrue(taskNodeService.isExecutionTreeClosed(500L));
    }

    @Test
    void fullCancelRestoresCapacity_notClosed() {
        // 闭环后撤销报工 → 容量恢复 → 未闭环
        node(1L, null, null, new BigDecimal("200"));
        node(2L, 1L, 30L, new BigDecimal("200"));
        reportOn(2L, new BigDecimal("200"));
        assertTrue(taskNodeService.isExecutionTreeClosed(500L));
        // 撤销（从 report 表移除）→ 恢复未闭环
        reportRows.clear();
        assertFalse(taskNodeService.isExecutionTreeClosed(500L));
    }

    @Test
    void recallReopensNotClosed() {
        // 根 200 → 主任 200 → 组长 100；主任报 100、组长报 100 → 闭环
        node(1L, null, null, new BigDecimal("200"));
        node(2L, 1L, 30L, new BigDecimal("200"));
        node(3L, 2L, 40L, new BigDecimal("100"));
        reportOn(2L, new BigDecimal("100"));
        reportOn(3L, new BigDecimal("100"));
        assertTrue(taskNodeService.isExecutionTreeClosed(500L));
        // 主任收回组长 100 → 主任重新持有 100（未报工）→ 未闭环
        node(3L, 2L, 40L, new BigDecimal("100")).setRecalledQuantity(new BigDecimal("100"));
        assertFalse(taskNodeService.isExecutionTreeClosed(500L));
    }

    @Test
    void returnReopensNotClosed() {
        // 根 200 → 主任 200 → 组长 100；主任报 100、组长报 100 → 闭环
        node(1L, null, null, new BigDecimal("200"));
        node(2L, 1L, 30L, new BigDecimal("200"));
        node(3L, 2L, 40L, new BigDecimal("100"));
        reportOn(2L, new BigDecimal("100"));
        reportOn(3L, new BigDecimal("100"));
        assertTrue(taskNodeService.isExecutionTreeClosed(500L));
        // 主任退回 100 给根 → 根恢复持有 100 → 未闭环
        node(2L, 1L, 30L, new BigDecimal("200")).setRecalledQuantity(new BigDecimal("100"));
        assertFalse(taskNodeService.isExecutionTreeClosed(500L));
    }

    // ==================== B：completeExecution Gate 顺序 ====================

    private ProductionOperationExecutionServiceImpl execService;
    private ProductionOperationExecutionMapper execMapper;
    private ProductionOrderMapper orderMapper;
    private WorkReportProjectionService projectionService;
    private TaskNodeService taskNodeServiceMock;

    private ProductionOperationExecutionServiceImpl buildExecService() throws Exception {
        execMapper = mock(ProductionOperationExecutionMapper.class);
        orderMapper = mock(ProductionOrderMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        projectionService = mock(WorkReportProjectionService.class);
        var qualityActionService = mock(QualityActionService.class);
        taskNodeServiceMock = mock(TaskNodeService.class);

        var ctor = ProductionOperationExecutionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        ProductionOperationExecutionServiceImpl svc = (ProductionOperationExecutionServiceImpl) ctor.newInstance(
                execMapper, orderMapper, jdbcTemplate, projectionService, qualityActionService,
                taskNodeServiceMock);
        ReflectionTestUtils.setField(svc, "baseMapper", execMapper);
        return svc;
    }

    private ProductionOperationExecution executingExecution() {
        ProductionOperationExecution e = new ProductionOperationExecution();
        e.setExecutionId(500L);
        e.setOrderId(1L);
        e.setExecutionStatus(ExecutionStatusEnum.EXECUTING.getCode());
        return e;
    }

    @Test
    void complete_noSubmittedReport_rejectedBeforeTreeGate() throws Exception {
        execService = buildExecService();
        ProductionOperationExecution e = executingExecution();
        when(execMapper.selectById(500L)).thenReturn(e);
        when(orderMapper.selectById(1L)).thenReturn(null); // 无完工冻结
        when(projectionService.hasAnySubmitted(500L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> execService.completeExecution(500L));
        assertTrue(ex.getMessage().contains("尚无有效报工"), ex.getMessage());
        verify(taskNodeServiceMock, never()).isExecutionTreeClosed(any());
    }

    @Test
    void complete_treeNotClosed_rejected() throws Exception {
        execService = buildExecService();
        ProductionOperationExecution e = executingExecution();
        when(execMapper.selectById(500L)).thenReturn(e);
        when(orderMapper.selectById(1L)).thenReturn(null);
        when(projectionService.hasAnySubmitted(500L)).thenReturn(true);
        when(taskNodeServiceMock.isExecutionTreeClosed(500L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> execService.completeExecution(500L));
        assertTrue(ex.getMessage().contains("任务树未闭环"), ex.getMessage());
        verify(execMapper, never()).updateById(any(ProductionOperationExecution.class));
    }

    @Test
    void complete_treeClosed_allowed() throws Exception {
        execService = buildExecService();
        ProductionOperationExecution e = executingExecution();
        when(execMapper.selectById(500L)).thenReturn(e);
        when(orderMapper.selectById(1L)).thenReturn(null);
        when(projectionService.hasAnySubmitted(500L)).thenReturn(true);
        when(taskNodeServiceMock.isExecutionTreeClosed(500L)).thenReturn(true);
        when(execMapper.updateById(any(ProductionOperationExecution.class))).thenReturn(1);
        when(execMapper.selectList(any())).thenReturn(new ArrayList<>()); // updateOrderCompletedQuantity 的 list()

        assertTrue(execService.completeExecution(500L));
        assertEquals(ExecutionStatusEnum.COMPLETED.getCode(), e.getExecutionStatus());
    }
}
