package com.jjx.production;

import com.jjx.production.domain.vo.ProductionOperationExecutionVO;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.QualityActionService;
import com.jjx.production.service.TaskNodeService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * TT-E2E-03 最小定向测试：派工列表 myAssignableNodeId 投影
 * 场景：真实 TaskNode 持有人有剩余可分配数量 → 投影该节点（分配任务按钮显示条件）；
 *       系统 Root 不作为人员节点；剩余为 0 → 不投影。
 */
class TaskAssignableProjectionTest {

    private ProductionOperationExecutionServiceImpl service;
    private JdbcTemplate jdbcTemplate;
    private List<Map<String, Object>> nodeRows;
    private List<Map<String, Object>> workReportRows;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        jdbcTemplate = mock(JdbcTemplate.class);
        Constructor<?> ctor = ProductionOperationExecutionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (ProductionOperationExecutionServiceImpl) ctor.newInstance(
                mock(ProductionOperationExecutionMapper.class),
                mock(ProductionOrderMapper.class),
                jdbcTemplate,
                mock(WorkReportProjectionService.class),
                mock(QualityActionService.class),
                mock(TaskNodeService.class));
        nodeRows = new ArrayList<>();
        workReportRows = new ArrayList<>();

        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            RowCallbackHandler handler = inv.getArgument(1);
            if (sql.contains("production_task_node")) {
                for (Map<String, Object> row : nodeRows) {
                    handler.processRow(fakeResultSet(row));
                }
            }
            if (sql.contains("production_work_report")) {
                for (Map<String, Object> row : workReportRows) {
                    handler.processRow(fakeResultSet(row));
                }
            }
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class));
    }

    private ResultSet fakeResultSet(Map<String, Object> row) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(anyString())).thenAnswer(inv -> ((Number) row.get(inv.getArgument(0))).longValue());
        when(rs.getObject(anyString())).thenAnswer(inv -> row.get(inv.getArgument(0)));
        when(rs.getString(anyString())).thenAnswer(inv -> {
            Object v = row.get(inv.getArgument(0));
            return v == null ? null : String.valueOf(v);
        });
        when(rs.getBigDecimal(anyString())).thenAnswer(inv -> (BigDecimal) row.get(inv.getArgument(0)));
        return rs;
    }

    private Map<String, Object> node(Long id, Long parentId, Long assigneeId, String assigneeName,
                                     String quantity, String recalled) {
        Map<String, Object> row = new HashMap<>();
        row.put("execution_id", 500L);
        row.put("task_node_id", id);
        row.put("parent_node_id", parentId);
        row.put("assignee_id", assigneeId);
        row.put("assignee_name", assigneeName);
        row.put("task_quantity", new BigDecimal(quantity));
        row.put("recalled_quantity", new BigDecimal(recalled));
        return row;
    }

    private Map<String, Object> report(Long nodeId, String total) {
        Map<String, Object> row = new HashMap<>();
        row.put("task_node_id", nodeId);
        row.put("total", new BigDecimal(total));
        return row;
    }

    private void enrich(List<ProductionOperationExecutionVO> vos) throws Exception {
        Method m = ProductionOperationExecutionServiceImpl.class
                .getDeclaredMethod("enrichTaskNodeChain", List.class, Set.class);
        m.setAccessible(true);
        m.invoke(service, vos, Set.of(500L));
    }

    private ProductionOperationExecutionVO vo() {
        ProductionOperationExecutionVO vo = new ProductionOperationExecutionVO();
        vo.setExecutionId(500L);
        return vo;
    }

    @Test
    void holderWithRemainingQuantity_projectsAssignableNode() throws Exception {
        // 根(无人员) 100 + 冲型车间主任(30)持有 60 + 其下级已占 40 → 主任剩余 20
        nodeRows.add(node(1000L, null, null, null, "100", "0"));
        nodeRows.add(node(1001L, 1000L, 30L, "冲型车间主任", "60", "0"));
        nodeRows.add(node(1002L, 1001L, 31L, "班组长", "40", "0"));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(30L);
            List<ProductionOperationExecutionVO> vos = List.of(vo());
            enrich(vos);
            assertEquals(1001L, vos.get(0).getMyAssignableNodeId());
            assertEquals(0, new BigDecimal("20").compareTo(vos.get(0).getMyAssignableQuantity()));
        }
    }

    @Test
    void systemRootOrNonHolder_notProjected() throws Exception {
        // 只有系统根（assignee=NULL）与另一人的节点；当前用户 99 非任何节点持有人
        nodeRows.add(node(1000L, null, null, null, "100", "0"));
        nodeRows.add(node(1001L, 1000L, 30L, "冲型车间主任", "60", "0"));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(99L);
            List<ProductionOperationExecutionVO> vos = List.of(vo());
            enrich(vos);
            assertNull(vos.get(0).getMyAssignableNodeId());
            assertNull(vos.get(0).getMyAssignableQuantity());
        }
    }

    @Test
    void holderFullyOccupied_notProjected() throws Exception {
        // 主任持有 60，下级已占 60 → 剩余 0，不投影
        nodeRows.add(node(1000L, null, null, null, "100", "0"));
        nodeRows.add(node(1001L, 1000L, 30L, "冲型车间主任", "60", "0"));
        nodeRows.add(node(1002L, 1001L, 31L, "班组长", "60", "0"));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(30L);
            List<ProductionOperationExecutionVO> vos = List.of(vo());
            enrich(vos);
            assertNull(vos.get(0).getMyAssignableNodeId());
            assertNull(vos.get(0).getMyAssignableQuantity());
        }
    }

    @Test
    void holderProjection_sumsMyTaskChildAndOwnHeld() throws Exception {
        // 系统根 1000 + 主任持有 1000 + 直接子节点班组长 600 + 主任自报 200
        nodeRows.add(node(1000L, null, null, null, "1000", "0"));
        nodeRows.add(node(1001L, 1000L, 30L, "冲型车间主任", "1000", "0"));
        nodeRows.add(node(1002L, 1001L, 31L, "班组长", "600", "0"));
        workReportRows.add(report(1001L, "200"));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(30L);
            List<ProductionOperationExecutionVO> vos = List.of(vo());
            enrich(vos);
            ProductionOperationExecutionVO v = vos.get(0);
            assertEquals(0, new BigDecimal("1000").compareTo(v.getMyTaskQuantity()));
            assertEquals(0, new BigDecimal("600").compareTo(v.getMyChildOccupied()));
            assertEquals(0, new BigDecimal("200").compareTo(v.getMyOwnHeld()));
        }
    }

    @Test
    void nonHolderOrNoNodes_projectsZero() throws Exception {
        // 只有系统根 + 他人节点；当前用户 99 非任何节点持有人
        nodeRows.add(node(1000L, null, null, null, "1000", "0"));
        nodeRows.add(node(1001L, 1000L, 30L, "冲型车间主任", "1000", "0"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(99L);
            List<ProductionOperationExecutionVO> vos = List.of(vo());
            enrich(vos);
            ProductionOperationExecutionVO v = vos.get(0);
            assertEquals(0, BigDecimal.ZERO.compareTo(v.getMyTaskQuantity()));
            assertEquals(0, BigDecimal.ZERO.compareTo(v.getMyChildOccupied()));
            assertEquals(0, BigDecimal.ZERO.compareTo(v.getMyOwnHeld()));
        }

        // 无任何任务节点
        nodeRows.clear();
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(99L);
            List<ProductionOperationExecutionVO> vos = List.of(vo());
            enrich(vos);
            ProductionOperationExecutionVO v = vos.get(0);
            assertEquals(0, BigDecimal.ZERO.compareTo(v.getMyTaskQuantity()));
            assertEquals(0, BigDecimal.ZERO.compareTo(v.getMyChildOccupied()));
            assertEquals(0, BigDecimal.ZERO.compareTo(v.getMyOwnHeld()));
        }
    }

    @Test
    void holderWithRecall_myTaskKeepsTaskQuantity() throws Exception {
        // 主任持有 1000、已收回 300 → effective 700；我的任务按 taskQuantity=1000，自己剩余=700
        nodeRows.add(node(1000L, null, null, null, "1000", "0"));
        nodeRows.add(node(1001L, 1000L, 30L, "冲型车间主任", "1000", "300"));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(30L);
            List<ProductionOperationExecutionVO> vos = List.of(vo());
            enrich(vos);
            ProductionOperationExecutionVO v = vos.get(0);
            assertEquals(0, new BigDecimal("1000").compareTo(v.getMyTaskQuantity()));
            assertEquals(0, BigDecimal.ZERO.compareTo(v.getMyChildOccupied()));
            assertEquals(0, new BigDecimal("700").compareTo(v.getMyOwnHeld()));
        }
    }

}
