package com.jjx.production;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.TaskAssignItemDTO;
import com.jjx.production.domain.dto.ProductionOperationExecutionQueryDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionTaskNode;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.ProductionOperationExecutionVO;
import com.jjx.production.domain.vo.TaskNodeVO;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionTaskNodeMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.QualityActionService;
import com.jjx.production.service.TaskNodeService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl;
import com.jjx.production.service.impl.TaskNodeServiceImpl;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.mapper.SysUserMapper;
import com.jjx.system.utils.SecurityUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Task Tree 查询视角/数据范围定向测试（TT-QUERY-SCOPE）
 * 覆盖：
 *  1 超级管理员（admin）→ 全部 Execution（无 assignee 范围）
 *  2 production:all → 全部 Execution
 *  3 production:ops → 仅本人 TaskNode 相关 Execution
 *  4 车间主任（dispatch_mgr）→ 仅本人相关
 *  5 班组长（dispatch_leader）→ 仅本人相关
 *  6 操作工（worker）→ 仅本人相关
 *  7 无 TaskNode 普通用户 → 不返回无关 Execution（EXISTS 语义）
 *  8 普通用户任务树 → 只返回本人子树（本人节点为业务根）
 *  9 节点详情保留任务来源（parentNodeId/parentAssigneeName）
 * 10 同一用户多 TaskNode → 按 executionId+assigneeId 聚合
 * 11 Execution 总量 ≠ 我的任务量
 * 12 看得见 ≠ 可越权 assign（身份边界仍拒绝）
 */
class TaskQueryScopeTest {

    // ==================== A. Execution 查询范围（ProductionOperationExecutionServiceImpl） ====================

    private ProductionOperationExecutionServiceImpl execService;
    private ProductionOperationExecutionMapper executionMapper;
    private JdbcTemplate jdbcTemplate;
    private List<Map<String, Object>> nodeRows;
    private List<Map<String, Object>> workReportRows;
    private LambdaQueryWrapper<ProductionOperationExecution> capturedWrapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ProductionOperationExecution.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        executionMapper = mock(ProductionOperationExecutionMapper.class);
        Constructor<?> ctor = ProductionOperationExecutionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        execService = (ProductionOperationExecutionServiceImpl) ctor.newInstance(
                executionMapper, mock(ProductionOrderMapper.class), jdbcTemplate,
                mock(WorkReportProjectionService.class), mock(QualityActionService.class),
                mock(TaskNodeService.class));
        // 让 ServiceImpl.list() 路由到 mock mapper（baseMapper 位于 CrudRepository/ServiceImpl 父类，按类层级查找）
        Field base = null;
        for (Class<?> c = execService.getClass().getSuperclass(); c != null; c = c.getSuperclass()) {
            try {
                base = c.getDeclaredField("baseMapper");
                break;
            } catch (NoSuchFieldException ignored) {
                // 继续向上查找
            }
        }
        assertNotNull(base, "未找到 ServiceImpl.baseMapper 字段");
        base.setAccessible(true);
        base.set(execService, executionMapper);

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
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
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

    private ProductionOperationExecution execution(Long id, String inputQty) {
        ProductionOperationExecution e = new ProductionOperationExecution();
        e.setExecutionId(id);
        e.setOrderId(1L);
        e.setProcessId(2L);
        e.setProcessName("冲形");
        e.setExecutionStatus(0);
        e.setInputQuantity(new BigDecimal(inputQty));
        e.setOperatorId(1L);
        e.setOperatorName("工序负责人");
        return e;
    }

    private String applyScope(LambdaQueryWrapper<ProductionOperationExecution> wrapper) throws Exception {
        Method m = ProductionOperationExecutionServiceImpl.class
                .getDeclaredMethod("applyQueryScope", LambdaQueryWrapper.class);
        m.setAccessible(true);
        return (String) m.invoke(execService, wrapper);
    }

    @Test
    void globalScope_noAssigneeFilter() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::isGlobalProductionScope).thenReturn(true);
            LambdaQueryWrapper<ProductionOperationExecution> w = new LambdaQueryWrapper<>();
            assertEquals("GLOBAL", applyScope(w));
            assertFalse(w.getCustomSqlSegment().contains("production_task_node"));
        }
    }

    @Test
    void personalScope_assigneeExistsFilter() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::isGlobalProductionScope).thenReturn(false);
            mocked.when(SecurityUtils::getUserId).thenReturn(30L);
            LambdaQueryWrapper<ProductionOperationExecution> w = new LambdaQueryWrapper<>();
            assertEquals("PERSONAL", applyScope(w));
            String seg = w.getCustomSqlSegment();
            assertTrue(seg.contains("production_task_node"), seg);
            assertTrue(seg.contains("assignee_id"), seg);
            assertTrue(w.getParamNameValuePairs().containsValue(30L), "EXISTS 参数应为当前用户ID");
        }
    }

    @Test
    void personalUser_multipleNodes_aggregatedCorrectlyAndViewScopePersonal() throws Exception {
        // 同一用户 30 在同一 Execution 下持有两个节点：A=40（已报10）、B=20；Execution 总量 200
        nodeRows.add(node(1000L, null, null, null, "200", "0"));
        nodeRows.add(node(1001L, 1000L, 30L, "冲型车间主任", "40", "0"));
        nodeRows.add(node(1002L, 1000L, 30L, "冲型车间主任", "20", "0"));
        workReportRows.add(report(1001L, "10"));

        when(executionMapper.selectList(any())).thenAnswer(inv -> {
            capturedWrapper = inv.getArgument(0);
            List<ProductionOperationExecution> list = new ArrayList<>();
            list.add(execution(500L, "200"));
            return list;
        });

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::isGlobalProductionScope).thenReturn(false);
            mocked.when(SecurityUtils::getUserId).thenReturn(30L);
            List<ProductionOperationExecutionVO> vos =
                    execService.queryExecutionList(new ProductionOperationExecutionQueryDTO());
            assertEquals(1, vos.size());
            ProductionOperationExecutionVO v = vos.get(0);
            // 10. 同一用户多节点聚合：我的任务=40+20=60；已分下级=0；自己剩余=30+20=50
            assertEquals(0, new BigDecimal("60").compareTo(v.getMyTaskQuantity()));
            assertEquals(0, BigDecimal.ZERO.compareTo(v.getMyChildOccupied()));
            assertEquals(0, new BigDecimal("50").compareTo(v.getMyOwnHeld()));
            // 11. Execution 总量 ≠ 我的任务量
            assertEquals(0, new BigDecimal("200").compareTo(v.getInputQuantity()));
            assertEquals("PERSONAL", v.getViewScope());
        }
        // 范围：普通用户列表必须携带本人 assignee 过滤
        assertTrue(capturedWrapper.getCustomSqlSegment().contains("production_task_node"));
        assertTrue(capturedWrapper.getParamNameValuePairs().containsValue(30L));
    }

    @Test
    void globalUser_queryAllWithoutAssigneeFilter() throws Exception {
        when(executionMapper.selectList(any())).thenAnswer(inv -> {
            capturedWrapper = inv.getArgument(0);
            List<ProductionOperationExecution> list = new ArrayList<>();
            list.add(execution(500L, "200"));
            list.add(execution(501L, "300"));
            return list;
        });
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::isGlobalProductionScope).thenReturn(true);
            List<ProductionOperationExecutionVO> vos =
                    execService.queryExecutionList(new ProductionOperationExecutionQueryDTO());
            assertEquals(2, vos.size());
            assertEquals("GLOBAL", vos.get(0).getViewScope());
        }
        assertFalse(capturedWrapper.getCustomSqlSegment().contains("production_task_node"),
                "全局用户不应带 assignee 范围过滤");
    }

    // ==================== B. 任务树范围（TaskNodeServiceImpl） ====================

    private TaskNodeServiceImpl taskNodeService;
    private ProductionTaskNodeMapper taskNodeMapper;
    private ProductionOperationExecutionMapper taskExecMapper;
    private ProductionWorkReportMapper workReportMapper;
    private final Map<Long, ProductionTaskNode> nodes = new LinkedHashMap<>();
    private final List<ProductionWorkReport> reports = new ArrayList<>();
    private long relatedCount = 1L;

    @BeforeEach
    void setUpTreeHarness() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ProductionTaskNode.class);
        taskNodeMapper = mock(ProductionTaskNodeMapper.class);
        taskExecMapper = mock(ProductionOperationExecutionMapper.class);
        workReportMapper = mock(ProductionWorkReportMapper.class);
        var sysUserMapper = mock(SysUserMapper.class);

        Constructor<?> ctor = TaskNodeServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        taskNodeService = (TaskNodeServiceImpl) ctor.newInstance(
                taskNodeMapper, taskExecMapper, sysUserMapper, workReportMapper,
                mock(JdbcTemplate.class), mock(SysDeptMapper.class));

        when(taskNodeMapper.selectCount(any())).thenAnswer(inv -> relatedCount);
        when(taskNodeMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(nodes.values()));
        when(taskNodeMapper.selectById(any())).thenAnswer(inv -> nodes.get(inv.getArgument(0)));
        when(taskNodeMapper.selectOne(any())).thenAnswer(inv -> {
            AbstractWrapper w = (AbstractWrapper) inv.getArgument(0);
            w.getSqlSegment();
            for (Object v : w.getParamNameValuePairs().values()) {
                if (v instanceof Long id && nodes.containsKey(id)) {
                    return nodes.get(id);
                }
            }
            return nodes.values().stream().filter(n -> n.getParentNodeId() == null).findFirst().orElse(null);
        });
        when(taskNodeMapper.insert(any(ProductionTaskNode.class))).thenAnswer(inv -> 1);
        when(workReportMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(reports));
        when(taskExecMapper.selectById(500L)).thenReturn(execution(500L, "200"));
        when(sysUserMapper.selectById(any())).thenAnswer(inv -> {
            SysUser u = new SysUser();
            u.setUserId(inv.getArgument(0));
            u.setNickName("用户" + inv.getArgument(0));
            return u;
        });
    }

    private ProductionTaskNode node(Long id, Long parentId, Long assigneeId, String qty) {
        ProductionTaskNode n = new ProductionTaskNode();
        n.setTaskNodeId(id);
        n.setExecutionId(500L);
        n.setParentNodeId(parentId);
        n.setAssigneeId(assigneeId);
        n.setAssigneeName(assigneeId == null ? null : "用户" + assigneeId);
        n.setTaskQuantity(new BigDecimal(qty));
        n.setRecalledQuantity(BigDecimal.ZERO);
        nodes.put(id, n);
        return n;
    }

    @Test
    void personalUser_treeReturnsOwnSubtree_withParentSourceInfo() throws Exception {
        node(100L, null, null, "200");   // 系统根
        node(101L, 100L, 30L, "150");    // 冲型车间主任
        node(102L, 101L, 31L, "100");    // 班组长
        node(103L, 102L, 32L, "40");     // 张三

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::isGlobalProductionScope).thenReturn(false);
            mocked.when(SecurityUtils::getUserId).thenReturn(31L); // 班组长
            TaskNodeVO tree = taskNodeService.getTaskTree(500L);
            // 8. 只返回本人子树：虚拟根无人员，直接子级 = 本人节点（班组长）
            assertNull(tree.getAssigneeId());
            assertEquals(1, tree.getChildren().size());
            TaskNodeVO mine = tree.getChildren().get(0);
            assertEquals(102L, mine.getTaskNodeId().longValue());
            assertEquals("用户31", mine.getAssigneeName());
            // 子树：张三
            assertEquals(1, mine.getChildren().size());
            assertEquals(103L, mine.getChildren().get(0).getTaskNodeId().longValue());
            // 9. 任务来源：parentNodeId + parentAssigneeName（上级不在树内也能展示）
            assertEquals(101L, mine.getParentNodeId().longValue());
            assertEquals("用户30", mine.getParentAssigneeName());
            assertEquals("用户31", mine.getChildren().get(0).getParentAssigneeName());
        }
    }

    @Test
    void globalUser_treeReturnsFullPersonTree() throws Exception {
        node(100L, null, null, "200");   // 系统根
        node(101L, 100L, 30L, "150");
        node(102L, 101L, 31L, "100");
        node(103L, 102L, 32L, "40");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::isGlobalProductionScope).thenReturn(true);
            TaskNodeVO tree = taskNodeService.getTaskTree(500L);
            assertEquals(100L, tree.getTaskNodeId().longValue());
            assertNull(tree.getAssigneeId());
            assertEquals(1, tree.getChildren().size());
            assertEquals(30L, tree.getChildren().get(0).getAssigneeId().longValue());
            // 系统根无人员，一级人员节点 parentAssigneeName = null（上级=系统根）
            assertNull(tree.getChildren().get(0).getParentAssigneeName());
        }
    }

    @Test
    void personalUser_unrelatedExecution_rejectedBeforeEnsureRoot() throws Exception {
        node(100L, null, null, "200");
        node(101L, 100L, 30L, "150");
        relatedCount = 0L; // 当前用户与该 Execution 无关联

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::isGlobalProductionScope).thenReturn(false);
            mocked.when(SecurityUtils::getUserId).thenReturn(99L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> taskNodeService.getTaskTree(500L));
            assertTrue(ex.getMessage().contains("无关联"), ex.getMessage());
        }
        // 关联校验必须在 ensureRoot（写副作用）之前：无关查看不得建立系统根
        verify(taskNodeMapper, never()).insert(any(ProductionTaskNode.class));
    }

    @Test
    void viewScope_doesNotGrantOperationRights() throws Exception {
        node(100L, null, null, "200");   // 系统根
        node(101L, 100L, 30L, "100");    // 主任持有 100

        // 用户 99 拥有全局可见范围（能看全部），但没有 task:admin、也不是节点持有人
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::isGlobalProductionScope).thenReturn(true);
            mocked.when(SecurityUtils::getUserId).thenReturn(99L);
            mocked.when(SecurityUtils::getUsername).thenReturn("u99");
            mocked.when(() -> SecurityUtils.hasPermission(anyString())).thenReturn(false);
            List<TaskAssignItemDTO> items = new ArrayList<>();
            TaskAssignItemDTO it = new TaskAssignItemDTO();
            it.setUserId(40L);
            it.setQuantity(new BigDecimal("10"));
            items.add(it);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> taskNodeService.assignChildren(101L, items));
            assertTrue(ex.getMessage().contains("只有当前任务节点持有人可以分配任务"), ex.getMessage());
        }
    }
}
