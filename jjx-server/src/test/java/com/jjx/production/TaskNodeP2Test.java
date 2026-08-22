package com.jjx.production;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.TaskAssignItemDTO;
import com.jjx.production.domain.dto.WorkReportCancelDTO;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionTaskNode;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionTaskNodeMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.WorkReportReadService;
import com.jjx.production.service.impl.TaskNodeServiceImpl;
import com.jjx.production.service.impl.WorkReportActionServiceImpl;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.mapper.SysUserMapper;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.jdbc.core.JdbcTemplate;

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
 * P2 定向测试：TaskNode 收回/退回 + WorkReport 接入（selfRemaining 动态汇总）
 * 覆盖用户指定 13 个用例：
 *  1. 节点500 报工100 → selfRemaining 400
 *  2. 已分节点200 + 自己报100 → selfRemaining 200
 *  3. 报工超过 selfRemaining 拒绝
 *  4. 非节点本人报工拒绝
 *  5. 撤销报工后容量恢复
 *  6. 父节点收回直接子节点剩余
 *  7. 已完成数量不可收回
 *  8. 已下分数量不可直接收回
 *  9. 部分收回
 * 10. 节点本人部分退回
 * 11. root 不能退回
 * 12. 非本人不能退回
 * 13. 收回后父节点可重新分配
 * <p>
 * 说明：TaskNodeServiceImpl / WorkReportActionServiceImpl 均为真实实现，共享同一套内存事实存储
 * （节点 Map + 报工 List 模拟 MySQL 行），selfReported 完全由 production_work_report 动态汇总，
 * TaskNode 不落完成量。
 */
class TaskNodeP2Test {

    private TaskNodeServiceImpl taskNodeService;
    private WorkReportActionServiceImpl reportService;

    private ProductionTaskNodeMapper taskNodeMapper;
    private ProductionOperationExecutionMapper executionMapper;
    private ProductionWorkReportMapper workReportMapper;

    /** 内存事实存储 */
    private final Map<Long, ProductionTaskNode> nodes = new LinkedHashMap<>();
    private final List<ProductionWorkReport> reports = new ArrayList<>();

    private long nextNodeId = 100L;
    private long nextReportId = 1000L;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() throws Exception {
        // 初始化 lambda 缓存，使 LambdaQueryWrapper.getSqlSegment() 可在纯单测中解析列名
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ProductionTaskNode.class);
        taskNodeMapper = mock(ProductionTaskNodeMapper.class);
        executionMapper = mock(ProductionOperationExecutionMapper.class);
        workReportMapper = mock(ProductionWorkReportMapper.class);
        var sysUserMapper = mock(SysUserMapper.class);
        var projectionService = mock(WorkReportProjectionService.class);
        var readService = mock(WorkReportReadService.class);
        var jdbcTemplate = mock(JdbcTemplate.class);
        var qualityInspectionService = mock(com.jjx.production.service.QualityInspectionService.class);

        // TaskNodeServiceImpl（真实）
        var taskCtor = TaskNodeServiceImpl.class.getDeclaredConstructors()[0];
        taskCtor.setAccessible(true);
        taskNodeService = (TaskNodeServiceImpl) taskCtor.newInstance(
                taskNodeMapper, executionMapper, sysUserMapper, workReportMapper,
                jdbcTemplate, mock(SysDeptMapper.class));

        // WorkReportActionServiceImpl（真实）：共享 workReportMapper，insert/update 直接写内存存储
        var reportCtor = WorkReportActionServiceImpl.class.getDeclaredConstructors()[0];
        reportCtor.setAccessible(true);
        reportService = (WorkReportActionServiceImpl) reportCtor.newInstance(
                workReportMapper, executionMapper, projectionService, readService, jdbcTemplate,
                taskNodeService, qualityInspectionService);

        // —— Mapper 行为映射到内存存储 ——
        when(taskNodeMapper.selectById(any())).thenAnswer(inv -> nodes.get(inv.getArgument(0)));
        when(taskNodeMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(nodes.values()));
        when(taskNodeMapper.selectOne(any())).thenAnswer(inv -> {
            // FOR UPDATE 行锁查询：从 wrapper 参数值中解析节点ID
            // 说明：MyBatis-Plus 的 paramNameValuePairs 惰性填充，先触发 getSqlSegment() 生成 SQL 段
            AbstractWrapper w = (AbstractWrapper) inv.getArgument(0);
            w.getSqlSegment();
            for (Object v : w.getParamNameValuePairs().values()) {
                if (v instanceof Long id) {
                    ProductionTaskNode n = nodes.get(id);
                    if (n != null) return n;
                }
            }
            return null;
        });
        when(taskNodeMapper.insert(any(ProductionTaskNode.class))).thenAnswer(inv -> {
            ProductionTaskNode n = inv.getArgument(0);
            n.setTaskNodeId(nextNodeId++);
            nodes.put(n.getTaskNodeId(), n);
            return 1;
        });
        when(taskNodeMapper.updateById(any(ProductionTaskNode.class))).thenAnswer(inv -> 1);

        when(workReportMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(reports));
        when(workReportMapper.insert(any(ProductionWorkReport.class))).thenAnswer(inv -> {
            ProductionWorkReport r = inv.getArgument(0);
            r.setReportId(nextReportId++);
            reports.add(r);
            return 1;
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
        when(workReportMapper.selectById(any())).thenAnswer(inv -> reports.stream()
                .filter(r -> r.getReportId().equals(inv.getArgument(0))).findFirst().orElse(null));

        when(executionMapper.selectById(500L)).thenReturn(execution(500L));
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(new ArrayList<>());
        when(qualityInspectionService.listByWorkReportId(any())).thenReturn(new ArrayList<>());
        when(sysUserMapper.selectById(any())).thenAnswer(inv -> {
            SysUser u = new SysUser();
            u.setUserId(inv.getArgument(0));
            u.setNickName("用户" + inv.getArgument(0));
            return u;
        });
    }

    // ==================== 工具 ====================

    private ProductionOperationExecution execution(Long id) {
        ProductionOperationExecution e = new ProductionOperationExecution();
        e.setExecutionId(id);
        e.setOrderId(1L);
        e.setExecutionStatus(ExecutionStatusEnum.EXECUTING.getCode());
        e.setInputQuantity(new BigDecimal("1000"));
        e.setOperatorId(1L);
        e.setOperatorName("工序负责人");
        return e;
    }

    /** 直接构造节点入库（精确控制树结构） */
    private ProductionTaskNode node(Long id, Long parentId, Long assigneeId, BigDecimal taskQty) {
        ProductionTaskNode n = new ProductionTaskNode();
        n.setTaskNodeId(id);
        n.setExecutionId(500L);
        n.setParentNodeId(parentId);
        n.setAssigneeId(assigneeId);
        n.setAssigneeName("用户" + assigneeId);
        n.setTaskQuantity(taskQty);
        n.setRecalledQuantity(BigDecimal.ZERO);
        nodes.put(id, n);
        return n;
    }

    /** 报工（走真实 submit 流程；operator=节点持有人） */
    private void submitWork(Long nodeId, Long assigneeId, BigDecimal qualified, BigDecimal defective) {
        WorkReportSubmitDTO dto = new WorkReportSubmitDTO();
        dto.setExecutionId(500L);
        dto.setTaskNodeId(nodeId);
        dto.setQualifiedQuantity(qualified);
        dto.setDefectiveQuantity(defective);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(true);
            reportService.submit(dto, "操作员" + assigneeId, assigneeId);
        }
    }

    private void withUser(long userId, Runnable action) {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(userId);
            mocked.when(SecurityUtils::getUsername).thenReturn("u" + userId);
            mocked.when(() -> SecurityUtils.hasPermission(anyString())).thenReturn(false);
            action.run();
        }
    }

    private BusinessException recallAs(long userId, Long childNodeId, BigDecimal qty) {
        return assertThrows(BusinessException.class, () ->
                withUser(userId, () -> taskNodeService.recall(childNodeId, qty)));
    }

    private BusinessException returnAs(long userId, Long nodeId, BigDecimal qty) {
        return assertThrows(BusinessException.class, () ->
                withUser(userId, () -> taskNodeService.returnNode(nodeId, qty)));
    }

    // ==================== 1. 节点500，报工100 → selfRemaining 400 ====================

    @Test
    void report100_selfRemaining400() {
        node(1L, null, 1L, new BigDecimal("500"));
        submitWork(1L, 1L, new BigDecimal("100"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("400"), taskNodeService.remaining(1L));
        assertEquals(new BigDecimal("400"), taskNodeService.availableToAssign(1L));
    }

    // ==================== 2. 已分子节点200 + 自己报100 → selfRemaining 200 ====================

    @Test
    void child200_plusSelfReport100_selfRemaining200() {
        node(1L, null, 1L, new BigDecimal("500"));
        node(2L, 1L, 101L, new BigDecimal("200"));
        submitWork(1L, 1L, new BigDecimal("100"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("200"), taskNodeService.remaining(1L));
    }

    // ==================== 3.0 TT-FINAL-04：顺序报工不超限（锁后 remaining 实时可见） ====================

    @Test
    void sequentialSubmitsCannotExceedSelfRemaining() {
        node(1L, null, 1L, new BigDecimal("100"));
        submitWork(1L, 1L, new BigDecimal("60"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("40"), taskNodeService.remaining(1L));
        // 第二次报工 50 > 剩余 40 → 拒绝；报工与报工不能共同消耗同一份容量
        WorkReportSubmitDTO dto = new WorkReportSubmitDTO();
        dto.setExecutionId(500L);
        dto.setTaskNodeId(1L);
        dto.setQualifiedQuantity(new BigDecimal("50"));
        dto.setDefectiveQuantity(BigDecimal.ZERO);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reportService.submit(dto, "操作员1", 1L));
            assertTrue(ex.getMessage().contains("超过节点剩余可报数量"), ex.getMessage());
        }
        // 仍只有一条报工，容量守恒
        assertEquals(1, reports.stream().filter(r ->
                com.jjx.production.enums.WorkReportStatusEnum.SUBMITTED.getCode().equals(r.getReportStatus())).count());
        assertEquals(new BigDecimal("40"), taskNodeService.remaining(1L));
    }

    // ==================== 3. 报工超过 selfRemaining 拒绝 ====================

    @Test
    void reportExceedingSelfRemaining_rejected() {
        node(1L, null, 1L, new BigDecimal("500"));
        WorkReportSubmitDTO dto = new WorkReportSubmitDTO();
        dto.setExecutionId(500L);
        dto.setTaskNodeId(1L);
        dto.setQualifiedQuantity(new BigDecimal("600"));
        dto.setDefectiveQuantity(BigDecimal.ZERO);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reportService.submit(dto, "操作员1", 1L));
            assertTrue(ex.getMessage().contains("超过节点剩余可报数量"), ex.getMessage());
        }
        // 拒绝后未产生任何报工
        assertTrue(reports.isEmpty());
    }

    // ==================== 4. 非节点本人报工拒绝 ====================

    @Test
    void nonAssigneeCannotSubmit() {
        node(1L, null, 1L, new BigDecimal("500"));
        WorkReportSubmitDTO dto = new WorkReportSubmitDTO();
        dto.setExecutionId(500L);
        dto.setTaskNodeId(1L);
        dto.setQualifiedQuantity(new BigDecimal("100"));
        dto.setDefectiveQuantity(BigDecimal.ZERO);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:add")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reportService.submit(dto, "路人", 99L)); // 非节点持有人 99
            assertTrue(ex.getMessage().contains("持有人本人"), ex.getMessage());
        }
        assertTrue(reports.isEmpty());
    }

    // ==================== 5. 撤销报工后容量恢复 ====================

    @Test
    void cancelReport_restoresSelfRemaining() {
        node(1L, null, 1L, new BigDecimal("500"));
        submitWork(1L, 1L, new BigDecimal("100"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("400"), taskNodeService.remaining(1L));

        WorkReportCancelDTO cancel = new WorkReportCancelDTO();
        cancel.setCancelReason("报错重报");
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:work-report:cancel")).thenReturn(true);
            reportService.cancel(reports.get(0).getReportId(), cancel, "操作员1", 1L);
        }
        // 撤销后 SUBMITTED 报工不再计入 selfReported，容量自动恢复
        assertEquals(new BigDecimal("500"), taskNodeService.remaining(1L));
    }

    // ==================== 6. 父节点收回直接子节点剩余 ====================

    @Test
    void parentRecallChildRemaining() {
        node(1L, null, 1L, new BigDecimal("1000"));
        ProductionTaskNode child = node(2L, 1L, 101L, new BigDecimal("300"));
        assertEquals(new BigDecimal("700"), taskNodeService.availableToAssign(1L));

        withUser(1L, () -> taskNodeService.recall(2L, new BigDecimal("300")));
        // 收回后子节点 effective=0，父节点容量全部恢复
        assertEquals(new BigDecimal("300"), child.getRecalledQuantity());
        assertEquals(new BigDecimal("1000"), taskNodeService.availableToAssign(1L));
    }

    // ==================== 7. 已完成数量不可收回 ====================

    @Test
    void completedQuantityCannotBeRecalled() {
        node(1L, null, 1L, new BigDecimal("1000"));
        ProductionTaskNode child = node(2L, 1L, 101L, new BigDecimal("500"));
        submitWork(2L, 101L, new BigDecimal("100"), BigDecimal.ZERO); // 已完成 100

        // selfRemaining=400：收回 500（含已完成）被拒绝
        BusinessException ex = recallAs(1L, 2L, new BigDecimal("500"));
        assertTrue(ex.getMessage().contains("超过子节点可收回数量"), ex.getMessage());
        assertEquals(BigDecimal.ZERO, child.getRecalledQuantity());
    }

    // ==================== 8. 已下分数量不可直接收回 ====================

    @Test
    void assignedDownQuantityCannotBeRecalledDirectly() {
        node(1L, null, 1L, new BigDecimal("1000"));
        ProductionTaskNode childA = node(2L, 1L, 101L, new BigDecimal("500"));
        node(3L, 2L, 102L, new BigDecimal("200")); // A 已下分 200 给下级

        // A.selfRemaining = 500 - 200 = 300：直接收回 400 被拒绝
        BusinessException ex = recallAs(1L, 2L, new BigDecimal("400"));
        assertTrue(ex.getMessage().contains("超过子节点可收回数量"), ex.getMessage());
        assertEquals(BigDecimal.ZERO, childA.getRecalledQuantity());

        // 只收回自身剩余 300 允许：A effective=200，父节点可分配 = 1000-200=800
        withUser(1L, () -> taskNodeService.recall(2L, new BigDecimal("300")));
        assertEquals(new BigDecimal("300"), childA.getRecalledQuantity());
        assertEquals(new BigDecimal("800"), taskNodeService.availableToAssign(1L));
    }

    // ==================== 9. 部分收回 ====================

    @Test
    void partialRecall_allowed() {
        node(1L, null, 1L, new BigDecimal("1000"));
        ProductionTaskNode child = node(2L, 1L, 101L, new BigDecimal("300"));
        withUser(1L, () -> taskNodeService.recall(2L, new BigDecimal("100")));

        assertEquals(new BigDecimal("100"), child.getRecalledQuantity());
        assertEquals(new BigDecimal("200"), taskNodeService.remaining(2L));
        assertEquals(new BigDecimal("800"), taskNodeService.availableToAssign(1L));
    }

    // ==================== 10. 节点本人部分退回 ====================

    @Test
    void assigneePartialReturn() {
        node(1L, null, 1L, new BigDecimal("1000"));
        ProductionTaskNode child = node(2L, 1L, 101L, new BigDecimal("500"));
        withUser(101L, () -> taskNodeService.returnNode(2L, new BigDecimal("200")));

        assertEquals(new BigDecimal("200"), child.getRecalledQuantity());
        assertEquals(new BigDecimal("300"), taskNodeService.remaining(2L));
        // 父节点容量恢复：1000 - (500-200) = 700
        assertEquals(new BigDecimal("700"), taskNodeService.availableToAssign(1L));
    }

    // ==================== 11. root 不能退回 ====================

    @Test
    void rootCannotReturn() {
        node(1L, null, 1L, new BigDecimal("1000"));
        BusinessException ex = returnAs(1L, 1L, new BigDecimal("100"));
        assertTrue(ex.getMessage().contains("根节点不允许退回"), ex.getMessage());
    }

    // ==================== 12. 非本人不能退回 ====================

    @Test
    void nonAssigneeCannotReturn() {
        node(1L, null, 1L, new BigDecimal("1000"));
        node(2L, 1L, 101L, new BigDecimal("500"));
        BusinessException ex = returnAs(99L, 2L, new BigDecimal("100"));
        assertTrue(ex.getMessage().contains("持有人本人可以退回"), ex.getMessage());
    }

    // ==================== 13. 收回后父节点可重新分配 ====================

    @Test
    void afterRecall_parentCanReassign() {
        node(1L, null, 1L, new BigDecimal("1000"));
        ProductionTaskNode child = node(2L, 1L, 101L, new BigDecimal("300"));
        // 先收回全部
        withUser(1L, () -> taskNodeService.recall(2L, new BigDecimal("300")));
        assertEquals(new BigDecimal("1000"), taskNodeService.availableToAssign(1L));

        // 重新分配 300 给新持有人 103
        TaskAssignItemDTO item = new TaskAssignItemDTO();
        item.setUserId(103L);
        item.setQuantity(new BigDecimal("300"));
        withUser(1L, () -> taskNodeService.assignChildren(1L, List.of(item)));

        assertEquals(new BigDecimal("700"), taskNodeService.availableToAssign(1L));
        // 原子节点已收回 300，effective=0 不再占用容量
        assertEquals(new BigDecimal("300"), child.getRecalledQuantity());
    }
}
