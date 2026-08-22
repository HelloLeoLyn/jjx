package com.jjx.production;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.TaskAssignItemDTO;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.entity.ProductionTaskNode;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.MyTaskNodeVO;
import com.jjx.production.domain.vo.TaskTreeEventVO;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.OrderStatusEnum;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionTaskNodeMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.QualityActionService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.WorkReportReadService;
import com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl;
import com.jjx.production.service.impl.TaskNodeServiceImpl;
import com.jjx.production.service.impl.WorkReportActionServiceImpl;
import com.jjx.production.service.impl.WorkReportProjectionServiceImpl;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.mapper.SysUserMapper;
import com.jjx.system.utils.SecurityUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TT-FINAL-E2E：多级任务树 + 多用户视角（Service 级多账号模拟，无法真实登录切换账号）
 * Execution = 200：
 *   生产管理员(1) → 车间主任(30) 150
 *   车间主任(30) → 班组长(40) 100（保留 50）
 *   班组长(40) → 工人A(50) 40 + 工人B(51) 30（保留 30）
 *   工人A 报工 10；班组长从工人B 收回 10；主任越级收回被拒；组长退回
 *   Complete Gate：未闭环拒绝 → 全闭环允许；流水聚合
 * 说明：真实 DB/UI 多账号登录验证标 MANUAL_REQUIRED。
 */
class TaskTreeFinalE2ETest {

    private TaskNodeServiceImpl taskNodeService;
    private WorkReportActionServiceImpl reportService;
    private ProductionOperationExecutionServiceImpl execService;
    private WorkReportProjectionServiceImpl projectionService;

    private final Map<Long, ProductionTaskNode> nodes = new LinkedHashMap<>();
    private final List<ProductionWorkReport> reports = new ArrayList<>();
    private final Map<Long, ProductionOperationExecution> executions = new LinkedHashMap<>();
    private final List<Map<String, Object>> operLogRows = new ArrayList<>();
    private long nextNodeId = 100L;
    private long nextReportId = 1000L;
    private java.time.LocalDateTime eventClock;

    private ProductionTaskNodeMapper taskNodeMapper;
    private ProductionOperationExecutionMapper executionMapper;
    private ProductionWorkReportMapper workReportMapper;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ProductionTaskNode.class);
        taskNodeMapper = mock(ProductionTaskNodeMapper.class);
        executionMapper = mock(ProductionOperationExecutionMapper.class);
        workReportMapper = mock(ProductionWorkReportMapper.class);
        var sysUserMapper = mock(SysUserMapper.class);
        var jdbcTemplate = mock(JdbcTemplate.class);
        var qualityInspectionService = mock(com.jjx.production.service.QualityInspectionService.class);

        taskNodeService = (TaskNodeServiceImpl) newCtor(TaskNodeServiceImpl.class, 6).newInstance(
                taskNodeMapper, executionMapper, sysUserMapper, workReportMapper, jdbcTemplate,
                mock(SysDeptMapper.class));
        projectionService = (WorkReportProjectionServiceImpl) newCtor(WorkReportProjectionServiceImpl.class, 3)
                .newInstance(jdbcTemplate, executionMapper, workReportMapper);
        var readService = mock(WorkReportReadService.class);
        reportService = (WorkReportActionServiceImpl) newCtor(WorkReportActionServiceImpl.class, 7).newInstance(
                workReportMapper, executionMapper, projectionService, readService, jdbcTemplate,
                taskNodeService, qualityInspectionService);
        var orderMapper = mock(ProductionOrderMapper.class);
        var qualityActionService = mock(QualityActionService.class);
        execService = (ProductionOperationExecutionServiceImpl) newCtor(
                ProductionOperationExecutionServiceImpl.class, 6).newInstance(
                executionMapper, orderMapper, jdbcTemplate, projectionService, qualityActionService,
                taskNodeService);
        org.springframework.test.util.ReflectionTestUtils.setField(execService, "baseMapper", executionMapper);

        // —— 内存事实存储映射 ——
        when(taskNodeMapper.selectById(any())).thenAnswer(inv -> nodes.get(inv.getArgument(0)));
        when(taskNodeMapper.selectList(any())).thenAnswer(inv -> selectListByWrapper((AbstractWrapper) inv.getArgument(0)));
        when(taskNodeMapper.selectOne(any())).thenAnswer(inv -> {
            AbstractWrapper w = (AbstractWrapper) inv.getArgument(0);
            w.getSqlSegment();
            for (Object v : w.getParamNameValuePairs().values()) {
                if (v instanceof Long id && nodes.containsKey(id)) return nodes.get(id);
            }
            return nodes.isEmpty() ? null : nodes.values().iterator().next();
        });
        when(taskNodeMapper.insert(any(ProductionTaskNode.class))).thenAnswer(inv -> {
            ProductionTaskNode n = inv.getArgument(0);
            n.setTaskNodeId(nextNodeId++);
            nodes.put(n.getTaskNodeId(), n);
            return 1;
        });
        when(taskNodeMapper.updateById(any(ProductionTaskNode.class))).thenAnswer(inv -> 1);

        when(workReportMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(reports));
        when(workReportMapper.selectById(any())).thenAnswer(inv -> reports.stream()
                .filter(r -> r.getReportId().equals(inv.getArgument(0))).findFirst().orElse(null));
        when(workReportMapper.insert(any(ProductionWorkReport.class))).thenAnswer(inv -> {
            ProductionWorkReport r = inv.getArgument(0);
            r.setReportId(nextReportId++);
            reports.add(r);
            return 1;
        });
        when(workReportMapper.selectCount(any())).thenAnswer(inv -> {
            long c = reports.stream().filter(r ->
                    WorkReportStatusEnum.SUBMITTED.getCode().equals(r.getReportStatus())).count();
            return c;
        });
        when(workReportMapper.update(any(ProductionWorkReport.class), any())).thenAnswer(inv -> {
            ProductionWorkReport upd = inv.getArgument(0);
            ProductionWorkReport cur = reports.stream()
                    .filter(r -> r.getReportId().equals(upd.getReportId())).findFirst().orElse(null);
            if (cur != null && WorkReportStatusEnum.SUBMITTED.getCode().equals(cur.getReportStatus())) {
                cur.setReportStatus(upd.getReportStatus());
                cur.setCancelledAt(upd.getCancelledAt());
                cur.setCancelReason(upd.getCancelReason());
                return 1;
            }
            return 0;
        });

        // execution：selectById / selectBatchIds / updateById（投影重算与状态更新写入内存）
        when(executionMapper.selectById(any())).thenAnswer(inv -> executions.get(inv.getArgument(0)));
        when(executionMapper.selectBatchIds(any())).thenAnswer(inv -> {
            List<ProductionOperationExecution> es = new ArrayList<>();
            for (Object id : (java.util.Collection<?>) inv.getArgument(0)) {
                ProductionOperationExecution e = executions.get(id);
                if (e != null) es.add(e);
            }
            return es;
        });
        when(executionMapper.updateById(any(ProductionOperationExecution.class))).thenAnswer(inv -> {
            ProductionOperationExecution upd = inv.getArgument(0);
            ProductionOperationExecution cur = executions.get(upd.getExecutionId());
            if (cur != null) {
                if (upd.getOutputQuantity() != null) cur.setOutputQuantity(upd.getOutputQuantity());
                if (upd.getQualifiedQuantity() != null) cur.setQualifiedQuantity(upd.getQualifiedQuantity());
                if (upd.getDefectiveQuantity() != null) cur.setDefectiveQuantity(upd.getDefectiveQuantity());
                if (upd.getExecutionStatus() != null) cur.setExecutionStatus(upd.getExecutionStatus());
                if (upd.getActualEndTime() != null) cur.setActualEndTime(upd.getActualEndTime());
                if (upd.getActualLaborHours() != null) cur.setActualLaborHours(upd.getActualLaborHours());
                if (upd.getActualMachineHours() != null) cur.setActualMachineHours(upd.getActualMachineHours());
            }
            return 1;
        });
        when(executionMapper.selectCount(any())).thenReturn(0L);
        when(executionMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(executions.values()));

        when(orderMapper.selectById(any())).thenAnswer(inv -> {
            ProductionOrder o = new ProductionOrder();
            o.setOrderId(1L);
            o.setOrderStatus(OrderStatusEnum.IN_PROGRESS.getCode());
            return o;
        });

        // jdbcTemplate：SUM 投影从内存报工计算；order_no / sys_oper_log 返回空
        when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenAnswer(inv -> {
                    BigDecimal q = BigDecimal.ZERO, d = BigDecimal.ZERO;
                    for (ProductionWorkReport r : reports) {
                        if (WorkReportStatusEnum.SUBMITTED.getCode().equals(r.getReportStatus())) {
                            q = q.add(nvl(r.getQualifiedQuantity()));
                            d = d.add(nvl(r.getDefectiveQuantity()));
                        }
                    }
                    return new BigDecimal[]{q, d, q.add(d), BigDecimal.ZERO, BigDecimal.ZERO};
                });
        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            org.springframework.jdbc.core.RowCallbackHandler handler = inv.getArgument(1);
            if (sql.contains("sys_oper_log")) {
                for (Map<String, Object> row : operLogRows) {
                    handler.processRow(fakeOperLogResultSet(row));
                }
            }
            return null;
        }).when(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class), any());
        when(qualityInspectionService.listByWorkReportId(any())).thenReturn(new ArrayList<>());
        when(sysUserMapper.selectById(any())).thenAnswer(inv -> {
            SysUser u = new SysUser();
            u.setUserId(inv.getArgument(0));
            u.setNickName("用户" + inv.getArgument(0));
            return u;
        });
        when(readService.getById(any())).thenAnswer(inv -> new com.jjx.production.domain.vo.WorkReportVO());

        // 初始 Execution：工序总量 200，状态 PENDING（分配任务不得自动开始）
        ProductionOperationExecution e = new ProductionOperationExecution();
        e.setExecutionId(500L);
        e.setOrderId(1L);
        e.setProcessName("冲型");
        e.setInputQuantity(new BigDecimal("200"));
        e.setExecutionStatus(ExecutionStatusEnum.PENDING.getCode());
        e.setOperatorId(1L);
        e.setOperatorName("工序负责人");
        executions.put(500L, e);
    }


    private List<ProductionTaskNode> selectListByWrapper(AbstractWrapper<?, ?, ?> w) {
        String sql = w.getSqlSegment();
        List<ProductionTaskNode> all = new ArrayList<>(nodes.values());
        List<Object> longs = new ArrayList<>();
        for (Object v : w.getParamNameValuePairs().values()) {
            if (v instanceof Long) longs.add(v);
            else if (v instanceof java.util.Collection<?> col) {
                for (Object e : col) if (e instanceof Long) longs.add(e);
            }
        }
        if (sql.contains("execution_id")) {
            final List<Object> eids = longs;
            return all.stream().filter(n -> eids.contains(n.getExecutionId())).collect(java.util.stream.Collectors.toList());
        }
        if (sql.contains("assignee_id") && !longs.isEmpty()) {
            final Object uid = longs.get(0);
            return all.stream().filter(n -> uid.equals(n.getAssigneeId())).collect(java.util.stream.Collectors.toList());
        }
        if (sql.contains("parent_node_id") && !longs.isEmpty()) {
            final Object pid = longs.get(0);
            return all.stream().filter(n -> pid.equals(n.getParentNodeId())).collect(java.util.stream.Collectors.toList());
        }
        return all;
    }

    private java.sql.ResultSet fakeOperLogResultSet(Map<String, Object> row) throws Exception {
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        when(rs.getString(anyString())).thenAnswer(inv -> {
            Object v = row.get(inv.getArgument(0));
            return v == null ? null : String.valueOf(v);
        });
        when(rs.getTimestamp(anyString())).thenAnswer(inv ->
                java.sql.Timestamp.valueOf((java.time.LocalDateTime) row.get(inv.getArgument(0))));
        return rs;
    }

    private void recordOperLog(String url, String param) {
        eventClock = LocalDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("oper_url", url);
        row.put("oper_param", param);
        row.put("real_name", null);
        row.put("username", "u");
        row.put("create_time", eventClock);
        operLogRows.add(row);
    }

    private static Constructor<?> newCtor(Class<?> clazz, int params) throws Exception {
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            if (c.getParameterCount() == params) {
                c.setAccessible(true);
                return c;
            }
        }
        throw new IllegalStateException("no ctor with " + params + " params for " + clazz);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Long asUser(long userId, java.util.function.Supplier<Long> action) {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(userId);
            mocked.when(SecurityUtils::getUsername).thenReturn("u" + userId);
            // 只有超管(1)拥有 *:*:* / task:admin；其余用户保留业务动作权限但受身份边界约束
            mocked.when(() -> SecurityUtils.hasPermission(anyString())).thenAnswer(inv -> {
                String p = inv.getArgument(0);
                if ("*:*:*".equals(p) || "production:task:admin".equals(p)) return userId == 1L;
                return true;
            });
            return action.get();
        }
    }

    private void assignAs(long userId, Long parentNodeId, long targetUserId, String qty) {
        List<TaskAssignItemDTO> items = new ArrayList<>();
        TaskAssignItemDTO it = new TaskAssignItemDTO();
        it.setUserId(targetUserId);
        it.setQuantity(new BigDecimal(qty));
        items.add(it);
        asUser(userId, () -> {
            taskNodeService.assignChildren(parentNodeId, items);
            return 0L;
        });
        // 模拟 OperLogAspect：分配动作写 sys_oper_log（bizType=production_task）
        recordOperLog("/production/task-node/" + parentNodeId + "/assign",
                "{\"parentNodeId\":" + parentNodeId + ",\"items\":[{\"userId\":" + targetUserId
                        + ",\"quantity\":" + qty + "}]}");
    }

    /** 模拟工序执行页“开始”动作（报工/完成要求 Execution 处于执行中） */
    private void startExecution() {
        executions.get(500L).setExecutionStatus(ExecutionStatusEnum.EXECUTING.getCode());
    }

    private void submitAs(long userId, Long nodeId, String qty) {
        WorkReportSubmitDTO dto = new WorkReportSubmitDTO();
        dto.setExecutionId(500L);
        dto.setTaskNodeId(nodeId);
        dto.setQualifiedQuantity(new BigDecimal(qty));
        dto.setDefectiveQuantity(BigDecimal.ZERO);
        asUser(userId, () -> {
            reportService.submit(dto, "用户" + userId, userId);
            return 0L;
        });
    }

    private void recallAs(long userId, Long childNodeId, String qty) {
        asUser(userId, () -> {
            taskNodeService.recall(childNodeId, new BigDecimal(qty));
            return 0L;
        });
        recordOperLog("/production/task-node/" + childNodeId + "/recall",
                "{\"childNodeId\":" + childNodeId + ",\"dto\":{\"quantity\":" + qty + "}}");
    }

    private Long myTaskQuantity(long userId) {
        return asUser(userId, () -> {
            List<MyTaskNodeVO> vos = taskNodeService.myTaskNodes();
            return vos.stream().map(v -> nvl(v.getTaskQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add).longValue();
        });
    }

    // ==================== 基础链路构建（供各用例复用） ====================

    private long[] buildChain() {
        asUser(1L, () -> { taskNodeService.ensureRoot(500L); return 0L; });
        ProductionTaskNode root = taskNodeService.getNode(nodes.values().iterator().next().getTaskNodeId());
        assignAs(1L, root.getTaskNodeId(), 30L, "150");   // 管理员 → 主任 150
        assignAs(30L, nodeOf(30L).getTaskNodeId(), 40L, "100"); // 主任 → 组长 100
        assignAs(40L, nodeOf(40L).getTaskNodeId(), 50L, "40");  // 组长 → 工人A 40
        assignAs(40L, nodeOf(40L).getTaskNodeId(), 51L, "30");  // 组长 → 工人B 30
        return new long[]{root.getTaskNodeId(), nodeOf(30L).getTaskNodeId(),
                nodeOf(40L).getTaskNodeId(), nodeOf(50L).getTaskNodeId(), nodeOf(51L).getTaskNodeId()};
    }

    private ProductionTaskNode nodeOf(Long assigneeId) {
        return nodes.values().stream()
                .filter(n -> java.util.Objects.equals(assigneeId, n.getAssigneeId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("node of " + assigneeId));
    }

    // ==================== E2E-01：Root → 主任（部分分配） ====================

    @Test
    void e2e01_rootToDirector_partialAssign_keepsRoot50() {
        long[] chain = buildChain();
        ProductionTaskNode root = nodes.get(chain[0]);
        ProductionTaskNode director = nodes.get(chain[1]);
        // Root 无人员、不作为人员节点
        assertNull(root.getAssigneeId());
        assertNull(root.getAssigneeName());
        // 主任 TaskNode = 150
        assertEquals(0, new BigDecimal("150").compareTo(director.getTaskQuantity()));
        // Root 可继续分配 = 50（部分分配后可继续）
        assertEquals(0, new BigDecimal("50").compareTo(taskNodeService.availableToAssign(root.getTaskNodeId())));
        // 分配不自动开始 Execution（保持 PENDING）
        assertEquals(ExecutionStatusEnum.PENDING.getCode(), executions.get(500L).getExecutionStatus());
        // 管理员视角：我的任务（管理员无节点）为 0，不出现 admin 业务节点
        assertEquals(0L, myTaskQuantity(1L));
    }

    // ==================== E2E-02：主任 → 组长（部分分配） ====================

    @Test
    void e2e02_directorToGroupLeader_keeps50() {
        buildChain();
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(30L);
            List<MyTaskNodeVO> vos = taskNodeService.myTaskNodes();
            assertEquals(1, vos.size());
            MyTaskNodeVO vo = vos.get(0);
            assertEquals(0, new BigDecimal("150").compareTo(vo.getTaskQuantity()));
            assertEquals(0, new BigDecimal("100").compareTo(vo.getChildOccupied()));
            assertEquals(0, new BigDecimal("50").compareTo(vo.getSelfRemaining()));
            assertEquals(0, new BigDecimal("50").compareTo(vo.getAvailableToAssign()));
            assertEquals("ACTIVE", vo.getStatus());
        }
    }

    // ==================== E2E-03：组长 → 多工人 ====================

    @Test
    void e2e03_groupLeaderToWorkers_quantitiesAccurate() {
        buildChain();
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(40L);
            List<MyTaskNodeVO> vos = taskNodeService.myTaskNodes();
            MyTaskNodeVO vo = vos.get(0);
            assertEquals(0, new BigDecimal("100").compareTo(vo.getTaskQuantity()));
            assertEquals(0, new BigDecimal("70").compareTo(vo.getChildOccupied())); // 40+30
            assertEquals(0, new BigDecimal("30").compareTo(vo.getSelfRemaining()));
        }
        // 工人视角：我的任务 = 40 / 30，绝不能是 200
        assertEquals(40L, myTaskQuantity(50L));
        assertEquals(30L, myTaskQuantity(51L));
    }

    // ==================== E2E-04：工人A 报工 ====================

    @Test
    void e2e04_workerAReport10() {
        long[] chain = buildChain();
        startExecution();
        submitAs(50L, chain[3], "10");
        assertEquals(1, reports.stream().filter(r ->
                WorkReportStatusEnum.SUBMITTED.getCode().equals(r.getReportStatus())).count());
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(50L);
            List<MyTaskNodeVO> vos = taskNodeService.myTaskNodes();
            MyTaskNodeVO vo = vos.get(0);
            assertEquals(0, new BigDecimal("40").compareTo(vo.getTaskQuantity()));
            assertEquals(0, new BigDecimal("10").compareTo(vo.getSelfReported()));
            assertEquals(0, new BigDecimal("30").compareTo(vo.getSelfRemaining()));
        }
        // WorkReport 绑定正确 taskNodeId
        assertEquals(chain[3], reports.get(0).getTaskNodeId());
        // 上级查看进度同步（execution 投影重算）
        assertEquals(0, new BigDecimal("10").compareTo(executions.get(500L).getOutputQuantity()));
    }

    // ==================== E2E-05：工人B 视角（不串数据） ====================

    @Test
    void e2e05_workerBPerspective_onlyOwnNode() {
        buildChain();
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(51L);
            List<MyTaskNodeVO> vos = taskNodeService.myTaskNodes();
            assertEquals(1, vos.size());
            assertEquals(0, new BigDecimal("30").compareTo(vos.get(0).getTaskQuantity()));
        }
    }

    // ==================== E2E-06：班组长收回工人B ====================

    @Test
    void e2e06_groupLeaderRecallFromWorkerB() {
        long[] chain = buildChain();
        startExecution();
        submitAs(50L, chain[3], "10");
        // 班组长从工人B剩余 30 中收回 10
        recallAs(40L, chain[4], "10");
        // 工人B 有效任务 30 → 20
        assertEquals(0, new BigDecimal("20").compareTo(nvl(nodes.get(chain[4]).getTaskQuantity())
                .subtract(nvl(nodes.get(chain[4]).getRecalledQuantity()))));
        // 班组长容量恢复 10（原剩余 30 → 40）
        assertEquals(0, new BigDecimal("40").compareTo(taskNodeService.availableToAssign(chain[2])));
        // 已报工数量不可收回：工人A 剩余 30，收回 31 必须拒绝（11 < 30 是合法部分收回）
        BusinessException ex = assertThrows(BusinessException.class, () ->
                asUser(40L, () -> {
                    taskNodeService.recall(chain[3], new BigDecimal("31"));
                    return 0L;
                }));
        assertNotNull(ex);
        // 收回后可立即重新分配（组长把恢复的 10 分给工人B）
        assignAs(40L, chain[2], 51L, "10");
        assertEquals(0, new BigDecimal("30").compareTo(taskNodeService.availableToAssign(chain[2])));
    }

    // ==================== E2E-07：越级收回被拒 ====================

    @Test
    void e2e07_directorCannotRecallGrandchild() {
        long[] chain = buildChain();
        // 主任(30) 直接收回工人A(50) → 拒绝（非直接子节点）
        assertThrows(BusinessException.class, () ->
                asUser(30L, () -> {
                    taskNodeService.recall(chain[3], new BigDecimal("5"));
                    return 0L;
                }));
        // 主任可收回直接子节点（班组长）
        asUser(30L, () -> {
            taskNodeService.recall(chain[2], new BigDecimal("5"));
            return 0L;
        });
        assertEquals(0, new BigDecimal("5").compareTo(nvl(nodes.get(chain[2]).getRecalledQuantity())));
    }

    // ==================== E2E-08：Return 部分退回 ====================

    @Test
    void e2e08_groupLeaderReturnOnlySelfRemaining() {
        long[] chain = buildChain();
        startExecution();
        submitAs(50L, chain[3], "10");
        // 组长 selfRemaining = 100 - (40+30) - 0 = 30；退回 20 给主任
        asUser(40L, () -> {
            taskNodeService.returnNode(chain[2], new BigDecimal("20"));
            return 0L;
        });
        // 组长节点已退回 20；主任可分配容量恢复 20（50 → 70）
        assertEquals(0, new BigDecimal("20").compareTo(nvl(nodes.get(chain[2]).getRecalledQuantity())));
        assertEquals(0, new BigDecimal("70").compareTo(taskNodeService.availableToAssign(chain[1])));
        // 不能退回已报工数量（工人A 已报 10，超过 selfRemaining 退回被拒）
        assertThrows(BusinessException.class, () ->
                asUser(40L, () -> {
                    taskNodeService.returnNode(chain[2], new BigDecimal("999"));
                    return 0L;
                }));
        // 非本人不能退回
        assertThrows(BusinessException.class, () ->
                asUser(51L, () -> {
                    taskNodeService.returnNode(chain[2], new BigDecimal("5"));
                    return 0L;
                }));
    }

    // ==================== E2E-09：多用户视角一致性 ====================

    @Test
    void e2e09_multiUserPerspectives_consistent() {
        buildChain();
        startExecution();
        submitAs(50L, nodeOf(50L).getTaskNodeId(), "10");
        asUser(40L, () -> {
            taskNodeService.recall(nodeOf(51L).getTaskNodeId(), new BigDecimal("10"));
            return 0L;
        });
        // 管理员：无人员节点 → 我的任务 0（只可查看完整树）
        assertEquals(0L, myTaskQuantity(1L));
        assertEquals(150L, myTaskQuantity(30L)); // 主任 150
        assertEquals(100L, myTaskQuantity(40L)); // 组长 100
        assertEquals(40L, myTaskQuantity(50L));  // 工人A 40
        assertEquals(30L, myTaskQuantity(51L));  // 工人B 30（收回后仍 30：数量为节点总量，非有效剩余）
        // Execution.inputQuantity=200 只代表工序总量，不进入个人任务数量
        assertNotEquals(200L, myTaskQuantity(51L));
    }

    // ==================== E2E-10：Complete Gate ====================

    @Test
    void e2e10_completeGate_rejectOpenTree_allowClosedTree() {
        // 全量分配链：根200 → 主任200 → 组长100（主任留100）→ 工人A40+工人B30（组长留30）
        asUser(1L, () -> { taskNodeService.ensureRoot(500L); return 0L; });
        ProductionTaskNode root = taskNodeService.getNode(nodeOf(null).getTaskNodeId());
        assignAs(1L, root.getTaskNodeId(), 30L, "200");
        assignAs(30L, nodeOf(30L).getTaskNodeId(), 40L, "100");
        assignAs(40L, nodeOf(40L).getTaskNodeId(), 50L, "40");
        assignAs(40L, nodeOf(40L).getTaskNodeId(), 51L, "30");
        startExecution();

        // 任务树未闭环（下级仍有剩余）→ 拒绝
        BusinessException ex = assertThrows(BusinessException.class, () ->
                asUser(1L, () -> {
                    execService.completeExecution(500L);
                    return 0L;
                }));
        assertTrue(ex.getMessage().contains("任务树未闭环") || ex.getMessage().contains("尚无有效报工"),
                ex.getMessage());

        // 全量报工闭环：工人A 40、工人B 30、组长 30、主任 100
        submitAs(50L, nodeOf(50L).getTaskNodeId(), "40");
        submitAs(51L, nodeOf(51L).getTaskNodeId(), "30");
        submitAs(40L, nodeOf(40L).getTaskNodeId(), "30");
        submitAs(30L, nodeOf(30L).getTaskNodeId(), "100");
        assertTrue(taskNodeService.isExecutionTreeClosed(500L));

        // 闭环后普通完成允许
        Long done = asUser(1L, () -> {
            execService.completeExecution(500L);
            return 1L;
        });
        assertEquals(1L, done);
        assertEquals(ExecutionStatusEnum.COMPLETED.getCode(), executions.get(500L).getExecutionStatus());
        // 数量守恒：报工总量 = 200 = 工序总量
        BigDecimal total = reports.stream()
                .filter(r -> WorkReportStatusEnum.SUBMITTED.getCode().equals(r.getReportStatus()))
                .map(r -> nvl(r.getQualifiedQuantity()).add(nvl(r.getDefectiveQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("200").compareTo(total));
    }

    // ==================== E2E-11：流水聚合 ====================

    @Test
    void e2e11_eventsFlow_sequence() {
        long[] chain = buildChain();
        startExecution();
        submitAs(50L, chain[3], "10");
        recallAs(40L, chain[4], "10");
        List<TaskTreeEventVO> events = taskNodeService.executionEvents(500L);
        // 4 次分配 + 1 报工 + 1 收回 = 6 条（按时间升序）
        assertEquals(6, events.size());
        assertEquals("ASSIGN", events.get(0).getAction());
        assertEquals("ASSIGN", events.get(1).getAction());
        assertEquals("ASSIGN", events.get(2).getAction());
        assertEquals("ASSIGN", events.get(3).getAction());
        assertEquals("WORK_REPORT", events.get(4).getAction());
        assertEquals("RECALL", events.get(5).getAction());
    }
}
