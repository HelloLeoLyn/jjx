package com.jjx.production;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionTaskNode;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.MyTaskNodeVO;
import com.jjx.production.domain.vo.TaskTreeEventVO;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionTaskNodeMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
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
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
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
 * TT-FINAL-05 H + TT-FINAL-06 最小定向测试：
 * A. myTaskNodes 状态投影：全部下分后 selfRemaining=0 不等于本人完成（必须子树闭环才 COMPLETED）
 * B. executionEvents 流水：分配/收回/退回（sys_oper_log）+ 报工/撤销报工（work_report）按时间聚合
 */
class TaskTreeFlowProjectionTest {

    private TaskNodeServiceImpl service;
    private ProductionTaskNodeMapper taskNodeMapper;
    private ProductionOperationExecutionMapper executionMapper;
    private ProductionWorkReportMapper workReportMapper;

    private final Map<Long, ProductionTaskNode> nodes = new LinkedHashMap<>();
    private final List<ProductionWorkReport> reports = new ArrayList<>();
    private final List<Map<String, Object>> operLogRows = new ArrayList<>();
    private long nextId = 100L;

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

        var ctor = TaskNodeServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (TaskNodeServiceImpl) ctor.newInstance(taskNodeMapper, executionMapper, sysUserMapper,
                workReportMapper, jdbcTemplate, mock(SysDeptMapper.class));

        when(taskNodeMapper.selectList(any())).thenAnswer(inv -> selectListByWrapper((AbstractWrapper) inv.getArgument(0)));
        when(taskNodeMapper.selectOne(any())).thenAnswer(inv -> {
            AbstractWrapper w = (AbstractWrapper) inv.getArgument(0);
            w.getSqlSegment();
            for (Object v : w.getParamNameValuePairs().values()) {
                if (v instanceof Long id && nodes.containsKey(id)) return nodes.get(id);
            }
            return null;
        });
        when(taskNodeMapper.selectById(any())).thenAnswer(inv -> nodes.get(inv.getArgument(0)));
        when(taskNodeMapper.insert(any(ProductionTaskNode.class))).thenAnswer(inv -> {
            ProductionTaskNode n = inv.getArgument(0);
            n.setTaskNodeId(nextId++);
            nodes.put(n.getTaskNodeId(), n);
            return 1;
        });
        when(workReportMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(reports));
        when(sysUserMapper.selectById(any())).thenAnswer(inv -> {
            SysUser u = new SysUser();
            u.setUserId(inv.getArgument(0));
            u.setNickName("用户" + inv.getArgument(0));
            return u;
        });
        when(executionMapper.selectBatchIds(any())).thenAnswer(inv -> {
            List<ProductionOperationExecution> es = new ArrayList<>();
            for (Object id : (java.util.Collection<?>) inv.getArgument(0)) {
                if (id.equals(500L)) es.add(execution(500L));
            }
            return es;
        });
        // jdbcTemplate：sys_oper_log 查询喂流水行；其他（order_no）返回空
        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            RowCallbackHandler handler = inv.getArgument(1);
            if (sql.contains("sys_oper_log")) {
                for (Map<String, Object> row : operLogRows) {
                    handler.processRow(fakeResultSet(row));
                }
            }
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any());
    }


    private List<ProductionTaskNode> selectListByWrapper(AbstractWrapper<?, ?, ?> w) {
        w.getSqlSegment();
        List<ProductionTaskNode> all = new ArrayList<>(nodes.values());
        // 单元素 in(executionId) 会被 MP 展开为数值参数（如 500）→ 按 executionId 过滤
        Object execId = null;
        Object userId = null;
        for (Object v : w.getParamNameValuePairs().values()) {
            if (v instanceof Long) {
                if (Long.valueOf(500L).equals(v)) execId = v;
                else if (userId == null) userId = v;
            } else if (v instanceof java.util.Collection<?>) {
                return all;
            }
        }
        if (execId != null) {
            final Object eid = execId;
            return all.stream().filter(n -> eid.equals(n.getExecutionId())).collect(java.util.stream.Collectors.toList());
        }
        if (userId != null) {
            final Object uid = userId;
            return all.stream().filter(n -> uid.equals(n.getAssigneeId())).collect(java.util.stream.Collectors.toList());
        }
        return all;
    }

    private ResultSet fakeResultSet(Map<String, Object> row) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenAnswer(inv -> {
            Object v = row.get(inv.getArgument(0));
            return v == null ? null : String.valueOf(v);
        });
        when(rs.getTimestamp(anyString())).thenAnswer(inv ->
                row.get(inv.getArgument(0)) == null ? null : Timestamp.valueOf((LocalDateTime) row.get(inv.getArgument(0))));
        return rs;
    }

    private ProductionOperationExecution execution(Long id) {
        ProductionOperationExecution e = new ProductionOperationExecution();
        e.setExecutionId(id);
        e.setOrderId(1L);
        e.setProcessName("冲型");
        e.setExecutionStatus(ExecutionStatusEnum.EXECUTING.getCode());
        e.setInputQuantity(new BigDecimal("200"));
        return e;
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

    // ==================== A：myTaskNodes 状态投影 ====================

    @Test
    void myNode_allAssignedDown_notCompleted() {
        // 根200 → 我的节点150 → 下级150（全部下分，自己剩余0，但子树未闭环）
        node(1L, null, null, new BigDecimal("200"));
        node(2L, 1L, 30L, new BigDecimal("150"));
        node(3L, 2L, 40L, new BigDecimal("150"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(30L);
            List<MyTaskNodeVO> vos = service.myTaskNodes();
            assertEquals(1, vos.size());
            MyTaskNodeVO vo = vos.get(0);
            assertEquals(0, BigDecimal.ZERO.compareTo(vo.getSelfRemaining()));
            assertEquals(0, new BigDecimal("150").compareTo(vo.getChildOccupied()));
            // 全部下分 ≠ 完成：必须是 ACTIVE，不能是 COMPLETED
            assertEquals("ACTIVE", vo.getStatus());
        }
    }

    @Test
    void myNode_subtreeClosed_completed() {
        // 根200 → 我的节点150 → 下级150；下级报工150 → 本人子树闭环
        node(1L, null, null, new BigDecimal("200"));
        node(2L, 1L, 30L, new BigDecimal("150"));
        node(3L, 2L, 40L, new BigDecimal("150"));
        reports.add(report(3L, "150", "0", WorkReportStatusEnum.SUBMITTED, LocalDateTime.now()));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(30L);
            List<MyTaskNodeVO> vos = service.myTaskNodes();
            assertEquals(1, vos.size());
            assertEquals("COMPLETED", vos.get(0).getStatus());
        }
    }

    // ==================== B：executionEvents 流水 ====================

    private ProductionWorkReport report(Long nodeId, String q, String d, WorkReportStatusEnum st, LocalDateTime time) {
        ProductionWorkReport r = new ProductionWorkReport();
        r.setReportId(nextId++);
        r.setExecutionId(500L);
        r.setTaskNodeId(nodeId);
        r.setQualifiedQuantity(new BigDecimal(q));
        r.setDefectiveQuantity(new BigDecimal(d));
        r.setReportStatus(st.getCode());
        r.setReportTime(time);
        r.setReporterName("张三");
        if (st == WorkReportStatusEnum.CANCELLED) {
            r.setCancelledAt(time);
            r.setCancelledByName("李四");
            r.setCancelReason("报错重报");
        }
        return r;
    }

    private void operLog(String url, String param, String realName, LocalDateTime time) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("oper_url", url);
        row.put("oper_param", param);
        row.put("real_name", realName);
        row.put("username", "u" + realName);
        row.put("create_time", time);
        operLogRows.add(row);
    }

    @Test
    void executionEvents_mergeSortedAndEnriched() {
        node(1L, null, null, new BigDecimal("200"));
        node(2L, 1L, 30L, new BigDecimal("150"));
        node(3L, 2L, 40L, new BigDecimal("100"));
        LocalDateTime t1 = LocalDateTime.of(2026, 8, 21, 9, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 8, 21, 9, 30);
        LocalDateTime t3 = LocalDateTime.of(2026, 8, 21, 10, 0);
        LocalDateTime t4 = LocalDateTime.of(2026, 8, 21, 10, 30);
        LocalDateTime t5 = LocalDateTime.of(2026, 8, 21, 11, 0);

        operLog("/production/task-node/1/assign", "{\"parentNodeId\":1,\"items\":[{\"userId\":30,\"quantity\":150}]}", "生产管理员", t1);
        operLog("/production/task-node/3/recall", "{\"childNodeId\":3,\"dto\":{\"quantity\":50,\"remark\":\"调整安排\"}}", "用户30", t3);
        operLog("/production/task-node/2/return", "{\"nodeId\":2,\"dto\":{\"quantity\":30}}", "用户30", t5);
        reports.add(report(3L, "40", "0", WorkReportStatusEnum.SUBMITTED, t2));
        reports.add(report(3L, "10", "0", WorkReportStatusEnum.CANCELLED, t4));

        List<TaskTreeEventVO> events = service.executionEvents(500L);
        // 5 条事件：分配 / 报工 / 收回 / 撤销报工 / 退回（时间升序）
        assertEquals(5, events.size());
        assertEquals("ASSIGN", events.get(0).getAction());
        assertEquals("生产管理员", events.get(0).getOperatorName());
        assertEquals("用户30", events.get(0).getTargetName());
        assertEquals(0, new BigDecimal("150").compareTo(events.get(0).getQuantity()));

        assertEquals("WORK_REPORT", events.get(1).getAction());
        assertEquals("张三", events.get(1).getOperatorName());
        assertEquals(0, new BigDecimal("40").compareTo(events.get(1).getQuantity()));

        assertEquals("RECALL", events.get(2).getAction());
        assertEquals("用户40", events.get(2).getTargetName());
        assertEquals(0, new BigDecimal("50").compareTo(events.get(2).getQuantity()));
        assertEquals("调整安排", events.get(2).getRemark());

        assertEquals("WORK_REPORT_CANCEL", events.get(3).getAction());
        assertEquals("李四", events.get(3).getOperatorName());
        assertEquals("报错重报", events.get(3).getRemark());

        assertEquals("RETURN", events.get(4).getAction());
        // 退回：操作人 = 节点本人；涉及人员 = 父节点持有人（此处退回给系统根 → 无人员，显示 '-'）
        assertEquals("用户30", events.get(4).getOperatorName());
        assertNull(events.get(4).getTargetName());
        assertEquals(0, new BigDecimal("30").compareTo(events.get(4).getQuantity()));
    }
}
