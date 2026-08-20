package com.jjx.production;

import com.jjx.production.domain.dto.ProductionOperationExecutionQueryDTO;
import com.jjx.production.domain.entity.ProductionDispatch;
import com.jjx.production.domain.entity.ProductionExecutionAssignment;
import com.jjx.production.domain.vo.ProductionOperationExecutionVO;
import com.jjx.production.mapper.ProductionDispatchMapper;
import com.jjx.production.mapper.ProductionExecutionAssignmentMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.DispatchNodeReadService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl;
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
 * WP-D 回归测试：工序执行列表三视图（scope=mine / scope=done）
 * <p>
 * - mine：责任人（ACTIVE Node assignee 且未完成）∪ 执行人（ACTIVE Assignment 剩余>0），去重、排除已完成
 * - done：我的 Assignment 剩余==0（完成/释放）或 Execution 已完成
 */
class ExecutionScopeFilterTest {

    private ProductionOperationExecutionServiceImpl service;
    private ProductionOperationExecutionMapper executionMapper;
    private ProductionDispatchMapper dispatchMapper;
    private ProductionExecutionAssignmentMapper assignmentMapper;
    private ProductionWorkReportMapper workReportMapper;
    private DispatchNodeReadService nodeReadService;
    private JdbcTemplate jdbcTemplate;

    private final List<ProductionOperationExecutionVO> vos = new ArrayList<>();
    private final List<ProductionExecutionAssignment> assignments = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicLong currentDispatchIndex = new java.util.concurrent.atomic.AtomicLong(0);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        executionMapper = mock(ProductionOperationExecutionMapper.class);
        var orderMapper = mock(com.jjx.production.mapper.ProductionOrderMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        var dispatchService = mock(com.jjx.production.service.DispatchService.class);
        var workReportProjectionService = mock(WorkReportProjectionService.class);
        nodeReadService = mock(DispatchNodeReadService.class);
        dispatchMapper = mock(ProductionDispatchMapper.class);
        var qualityActionService = mock(com.jjx.production.service.QualityActionService.class);
        assignmentMapper = mock(ProductionExecutionAssignmentMapper.class);
        workReportMapper = mock(ProductionWorkReportMapper.class);

        Constructor<?> ctor = ProductionOperationExecutionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (ProductionOperationExecutionServiceImpl) ctor.newInstance(
                executionMapper, orderMapper, jdbcTemplate, dispatchService, workReportProjectionService,
                nodeReadService, dispatchMapper, qualityActionService, assignmentMapper, workReportMapper);
        // ServiceImpl.list() 走 baseMapper（反射构造不注入）→ 手动设置
        java.lang.reflect.Field bm = null;
        Class<?> k = service.getClass();
        while (k != null && bm == null) {
            try {
                bm = k.getDeclaredField("baseMapper");
            } catch (NoSuchFieldException e) {
                k = k.getSuperclass();
            }
        }
        assertNotNull(bm, "baseMapper field not found");
        bm.setAccessible(true);
        bm.set(service, executionMapper);

        currentDispatchIndex.set(0);
        // 默认：无 CANCELLED 工单（isOrderCancelled 单 vararg）；无 Assignment；无 dispatch
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        when(dispatchMapper.selectOne(any())).thenReturn(null);
        when(assignmentMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(workReportMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(new ArrayList<>());
    }

    private ProductionOperationExecutionVO vo(Long id, int status, Long assigneeId, String name) {
        ProductionOperationExecutionVO v = new ProductionOperationExecutionVO();
        v.setExecutionId(id);
        v.setOrderId(5L);
        v.setOrderNo("WO-1");
        v.setProcessName("贴膜");
        v.setInputQuantity(new BigDecimal("1000"));
        v.setExecutionStatus(status);
        v.setCurrentAssigneeId(assigneeId);
        v.setCurrentAssigneeName(name);
        return v;
    }

    private ProductionDispatch dispatchOf(Long executionId) {
        ProductionDispatch d = new ProductionDispatch();
        d.setDispatchId(100L);
        d.setExecutionId(executionId);
        return d;
    }

    private com.jjx.production.domain.vo.DispatchNodeVO node(Long assigneeId) {
        com.jjx.production.domain.vo.DispatchNodeVO n = new com.jjx.production.domain.vo.DispatchNodeVO();
        n.setNodeId(10L);
        n.setDispatchId(100L);
        n.setAssigneeId(assigneeId);
        n.setAssigneeName("组长");
        n.setNodeStatus("ACTIVE");
        return n;
    }

    private ProductionExecutionAssignment assignment(Long id, Long executionId, Long assigneeId,
                                                     BigDecimal assigned, BigDecimal released) {
        ProductionExecutionAssignment a = new ProductionExecutionAssignment();
        a.setAssignmentId(id);
        a.setExecutionId(executionId);
        a.setOrderId(5L);
        a.setAssigneeId(assigneeId);
        a.setAssigneeName("执行人" + assigneeId);
        a.setAssignedQuantity(assigned);
        a.setReleasedQuantity(released);
        a.setAssignmentStatus(com.jjx.production.enums.AssignmentStatusEnum.ACTIVE.getCode());
        return a;
    }

    @Test
    @SuppressWarnings("unchecked")
    void scopeMine_includesAssigneeAndExecutor_dedupExcludesCompleted() throws Exception {
        // 用户 1 = 责任人（execution 1）；用户 2 = 执行人（execution 2 有 ACTIVE 分配 300/0，无报工 → 剩余 300）
        // execution 3 = 已完成（责任人=用户1）→ mine 中排除；execution 4 = 执行人但分配已完成（300/300 报工）→ 排除
        vos.clear(); assignments.clear();
        vos.add(vo(1L, 2, 1L, "组长"));   // 责任人=我，执行中 → 保留
        vos.add(vo(2L, 2, 99L, "别人"));  // 执行人有分配 → 保留（我是执行人）
        vos.add(vo(3L, 4, 1L, "组长"));   // 已完成 → mine 排除
        vos.add(vo(4L, 2, 99L, "别人"));  // 我的分配已完成（300/300）→ mine 排除

        // execution 2：我（用户1）的 ACTIVE 分配（300，未报工 → remaining 300）
        assignments.add(assignment(1L, 2L, 1L, new BigDecimal("300"), BigDecimal.ZERO));
        // execution 4：我的分配已全部释放（released=300 → effective=0 → remaining=0）
        assignments.add(assignment(2L, 4L, 1L, new BigDecimal("300"), new BigDecimal("300")));

        when(executionMapper.selectList(any())).thenAnswer(inv -> {
            List<com.jjx.production.domain.entity.ProductionOperationExecution> list = new ArrayList<>();
            for (ProductionOperationExecutionVO v : vos) {
                com.jjx.production.domain.entity.ProductionOperationExecution e =
                        new com.jjx.production.domain.entity.ProductionOperationExecution();
                e.setExecutionId(v.getExecutionId());
                e.setOrderId(v.getOrderId());
                e.setInputQuantity(v.getInputQuantity());
                e.setExecutionStatus(v.getExecutionStatus());
                list.add(e);
            }
            return list;
        });
        // assignment 列表（loadAssignmentsByExecutionIds 用）
        when(assignmentMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(assignments));
        // dispatch：计数方式（不解析 wrapper，避免 lambda cache 异常）；execution1 → dispatch 1001，其他 → 1002+
        when(dispatchMapper.selectOne(any())).thenAnswer(inv -> {
            long n = currentDispatchIndex.getAndIncrement() + 1L;
            ProductionDispatch d = new ProductionDispatch();
            d.setDispatchId(1000L + n);
            d.setExecutionId(n);
            return d;
        });
        // node：dispatch 1001（execution1）→ 用户1（责任人）；其他 → 用户99
        when(nodeReadService.getCurrentActiveNode(any())).thenAnswer(inv -> {
            Long did = inv.getArgument(0);
            return did == 1001L ? node(1L) : node(99L);
        });
        // workReport：默认空（reported=0）；exec4 的 remaining=0 由 released=300 表达

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            mocked.when(() -> SecurityUtils.hasPermission(anyString())).thenReturn(true);

            ProductionOperationExecutionQueryDTO q = new ProductionOperationExecutionQueryDTO();
            q.setScope("mine");
            List<ProductionOperationExecutionVO> result = service.queryExecutionList(q);
            // execution1（责任人）、execution2（执行人剩余300）；3/4 排除
            List<Long> ids = result.stream().map(ProductionOperationExecutionVO::getExecutionId).toList();
            assertTrue(ids.contains(1L), "责任人任务应保留: " + ids);
            assertTrue(ids.contains(2L), "执行人任务应保留: " + ids);
            assertFalse(ids.contains(3L), "已完成不应出现在我的当前任务: " + ids);
            assertFalse(ids.contains(4L), "剩余归零的分配不应出现在我的当前任务: " + ids);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void scopeDone_includesCompletedExecutorAndDoneAssignment() throws Exception {
        // 用户2 参与 execution2（分配已完成 300/300）与 execution4（Execution 已完成）
        vos.clear(); assignments.clear();
        vos.add(vo(2L, 2, 99L, "别人")); // 我的分配完成（剩余0）→ done 保留
        vos.add(vo(4L, 4, 99L, "别人")); // Execution 已完成，我参与 → done 保留
        assignments.add(assignment(1L, 2L, 1L, new BigDecimal("300"), new BigDecimal("300")));
        assignments.add(assignment(2L, 4L, 1L, new BigDecimal("300"), new BigDecimal("300")));

        when(executionMapper.selectList(any())).thenAnswer(inv -> {
            List<com.jjx.production.domain.entity.ProductionOperationExecution> list = new ArrayList<>();
            for (ProductionOperationExecutionVO v : vos) {
                com.jjx.production.domain.entity.ProductionOperationExecution e =
                        new com.jjx.production.domain.entity.ProductionOperationExecution();
                e.setExecutionId(v.getExecutionId());
                e.setOrderId(v.getOrderId());
                e.setInputQuantity(v.getInputQuantity());
                e.setExecutionStatus(v.getExecutionStatus());
                list.add(e);
            }
            return list;
        });
        currentDispatchIndex.set(0);
        when(dispatchMapper.selectOne(any())).thenAnswer(inv -> {
            ProductionDispatch d = new ProductionDispatch();
            d.setDispatchId(1002L);
            d.setExecutionId(2L);
            return d;
        });
        when(nodeReadService.getCurrentActiveNode(any())).thenReturn(node(99L));
        // workReport：默认空；remaining=0 由 released 表达
        // assignment 列表（loadAssignmentsByExecutionIds + myInvolvedExecutionIds）
        when(assignmentMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(assignments));
        // myInvolvedExecutionIds：jdbcTemplate 查 node 为空
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(new ArrayList<>());

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            mocked.when(() -> SecurityUtils.hasPermission(anyString())).thenReturn(true);

            ProductionOperationExecutionQueryDTO q = new ProductionOperationExecutionQueryDTO();
            q.setScope("done");
            List<ProductionOperationExecutionVO> result = service.queryExecutionList(q);
            List<Long> ids = result.stream().map(ProductionOperationExecutionVO::getExecutionId).toList();
            assertTrue(ids.contains(2L), "分配完成应出现在我已完成: " + ids);
            assertTrue(ids.contains(4L), "Execution 已完成应出现在我已完成: " + ids);
        }
    }
}
