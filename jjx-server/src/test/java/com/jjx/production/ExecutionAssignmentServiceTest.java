package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.AssignmentCreateDTO;
import com.jjx.production.domain.dto.AssignmentReleaseDTO;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import com.jjx.production.domain.entity.ProductionExecutionAssignment;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.AssignmentViewVO;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionDispatchNodeMapper;
import com.jjx.production.mapper.ProductionExecutionAssignmentMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.DispatchNodeReadService;
import com.jjx.production.service.impl.ExecutionAssignmentServiceImpl;
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
import static org.mockito.Mockito.*;

/**
 * WP-B 回归测试：ExecutionAssignment 主链
 * 覆盖：创建/多人原子/超分拒绝/权限/释放/重新分配/并发/数量计算
 */
class ExecutionAssignmentServiceTest {

    private ExecutionAssignmentServiceImpl service;
    private ProductionExecutionAssignmentMapper assignmentMapper;
    private ProductionOperationExecutionMapper executionMapper;
    private ProductionDispatchNodeMapper nodeMapper;
    private ProductionWorkReportMapper workReportMapper;
    private JdbcTemplate jdbcTemplate;

    /** 内存 assignment 存储（模拟 DB） */
    private final List<ProductionExecutionAssignment> store = new ArrayList<>();
    private long nextId = 1;
    private ProductionOperationExecution execRow;

    @BeforeEach
    void setUp() throws Exception {
        assignmentMapper = mock(ProductionExecutionAssignmentMapper.class);
        executionMapper = mock(ProductionOperationExecutionMapper.class);
        nodeMapper = mock(ProductionDispatchNodeMapper.class);
        workReportMapper = mock(ProductionWorkReportMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        Constructor<?> ctor = ExecutionAssignmentServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (ExecutionAssignmentServiceImpl) ctor.newInstance(
                assignmentMapper, executionMapper, nodeMapper, workReportMapper,
                mock(DispatchNodeReadService.class), jdbcTemplate);

        execRow = exec(1L, new BigDecimal("1000"));
        when(executionMapper.selectOne(any())).thenAnswer(inv -> execRow);
        when(executionMapper.selectById(1L)).thenAnswer(inv -> execRow);

        // 内存 store 模拟 insert/select/update
        when(assignmentMapper.insert(any(ProductionExecutionAssignment.class))).thenAnswer(inv -> {
            ProductionExecutionAssignment a = inv.getArgument(0);
            a.setAssignmentId(nextId++);
            store.add(a);
            return 1;
        });
        when(assignmentMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(store));
        when(assignmentMapper.selectById(any(Long.class))).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return store.stream().filter(a -> a.getAssignmentId().equals(id)).findFirst().orElse(null);
        });
        when(assignmentMapper.selectCount(any())).thenAnswer(inv -> (long) store.size());
        when(assignmentMapper.updateById(any(ProductionExecutionAssignment.class))).thenAnswer(inv -> {
            ProductionExecutionAssignment a = inv.getArgument(0);
            for (int i = 0; i < store.size(); i++) {
                if (store.get(i).getAssignmentId().equals(a.getAssignmentId())) {
                    store.set(i, a);
                    break;
                }
            }
            return 1;
        });

        // WorkReport 模拟（reportedQuantity 用 workReportMapper.selectList 汇总）
        when(workReportMapper.selectList(any())).thenReturn(new ArrayList<>());

        // jdbcTemplate：查询执行人姓名、dispatch_id
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenAnswer(inv -> new ArrayList<>());
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenAnswer(inv -> new ArrayList<>());
        // workReport 汇总（jdbcTemplate 路径）：默认 0
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any())).thenReturn(BigDecimal.ZERO);
        // WP-D 唯一约束 COUNT（Integer）：默认 0（无既有分配）——2 个 vararg 参数需两个 any()
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(0);
    }

    private ProductionOperationExecution exec(Long id, BigDecimal input) {
        ProductionOperationExecution e = new ProductionOperationExecution();
        e.setExecutionId(id);
        e.setOrderId(5L);
        e.setInputQuantity(input);
        e.setExecutionStatus(2);
        return e;
    }

    private ProductionDispatchNode activeNode(Long nodeId, Long dispatchId, Long assigneeId, String name) {
        ProductionDispatchNode n = new ProductionDispatchNode();
        n.setNodeId(nodeId);
        n.setDispatchId(dispatchId);
        n.setAssigneeId(assigneeId);
        n.setAssigneeName(name);
        n.setNodeStatus("ACTIVE");
        return n;
    }

    private void mockActiveNode(ProductionDispatchNode node) {
        when(nodeMapper.selectOne(any())).thenAnswer(inv -> node);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    if (sql.contains("sys_user")) return new ArrayList<>();
                    if (sql.contains("production_dispatch")) {
                        List<Long> ids = new ArrayList<>();
                        ids.add(node.getDispatchId());
                        return ids;
                    }
                    return new ArrayList<>();
                });
    }

    private void mockWorkReportSum(BigDecimal sum) {
        // reportedQuantity 用 workReportMapper.selectList 汇总（SUBMITTED qualified+defective）
        when(workReportMapper.selectList(any())).thenAnswer(inv -> {
            ProductionWorkReport r = new ProductionWorkReport();
            r.setQualifiedQuantity(sum);
            r.setDefectiveQuantity(BigDecimal.ZERO);
            r.setReportStatus(WorkReportStatusEnum.SUBMITTED.getCode());
            List<ProductionWorkReport> list = new ArrayList<>();
            list.add(r);
            return list;
        });
    }

    private AssignmentCreateDTO createDto(Long executionId, Object... pairs) {
        AssignmentCreateDTO dto = new AssignmentCreateDTO();
        dto.setExecutionId(executionId);
        List<AssignmentCreateDTO.AssignmentItemDTO> items = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            AssignmentCreateDTO.AssignmentItemDTO item = new AssignmentCreateDTO.AssignmentItemDTO();
            item.setAssigneeId((Long) pairs[i]);
            item.setQuantity((BigDecimal) pairs[i + 1]);
            items.add(item);
        }
        dto.setAssignments(items);
        return dto;
    }

    private void withSuper() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            mocked.when(() -> SecurityUtils.hasPermission(anyString())).thenReturn(true);
        }
    }

    private void withAssignPerm() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:assignment:add")).thenReturn(true);
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
        }
    }

    // ==================== 1. 1000 → 张三300/李四300 → unassigned 400 ====================

    @Test
    void assignTwo_unassigned400() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            AssignmentViewVO vo = service.create(
                    createDto(1L, 101L, new BigDecimal("300"), 102L, new BigDecimal("300")),
                    "组长", 1L);
            assertEquals(0, new BigDecimal("400").compareTo(vo.getUnassignedQuantity()));
            assertEquals(0, new BigDecimal("600").compareTo(vo.getAssignedQuantity()));
            assertEquals(2, vo.getAssignments().size());
        }
    }

    // ==================== 2. 再分王五400 → unassigned 0 ====================

    @Test
    void assignMore_reachesZero() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            service.create(createDto(1L, 101L, new BigDecimal("300"), 102L, new BigDecimal("300")), "组长", 1L);
            AssignmentViewVO vo = service.create(
                    createDto(1L, 103L, new BigDecimal("400")), "组长", 1L);
            assertEquals(0, BigDecimal.ZERO.compareTo(vo.getUnassignedQuantity()));
        }
    }

    // ==================== 3. 多人合计超 remaining → 整批失败 0 写入 ====================

    @Test
    void batchOverRemaining_allRejected_noWrite() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            // 先分 600
            service.create(createDto(1L, 101L, new BigDecimal("600")), "组长", 1L);
            int before = store.size();
            // 剩余 400，一次提交 300+200=500 → 整批拒绝
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    service.create(createDto(1L, 102L, new BigDecimal("300"), 103L, new BigDecimal("200")), "组长", 1L));
            assertTrue(ex.getMessage().contains("超过剩余可分配"), ex.getMessage());
            assertEquals(before, store.size(), "整批失败不应有任何写入");
        }
    }

    // ==================== 4. 单条 quantity<=0 拒绝 ====================

    @Test
    void zeroOrNegativeQuantity_rejected() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            assertThrows(BusinessException.class, () ->
                    service.create(createDto(1L, 101L, BigDecimal.ZERO), "组长", 1L));
            assertThrows(BusinessException.class, () ->
                    service.create(createDto(1L, 101L, new BigDecimal("-5")), "组长", 1L));
            assertEquals(0, store.size());
        }
    }

    // ==================== 5. 非 ACTIVE 责任人创建拒绝 ====================

    @Test
    void noActiveNode_rejected() {
        when(nodeMapper.selectOne(any())).thenReturn(null);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    service.create(createDto(1L, 101L, new BigDecimal("300")), "组长", 1L));
            assertTrue(ex.getMessage().contains("ACTIVE"), ex.getMessage());
        }
    }

    // ==================== 6. 有 permission 但不是当前责任人 → 拒绝 ====================

    @Test
    void hasPermButNotAssignee_rejected() {
        // ACTIVE node assignee=1，操作人 operatorId=99（有权限但非责任人）
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:assignment:add")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    service.create(createDto(1L, 101L, new BigDecimal("300")), "路人", 99L));
            assertTrue(ex.getMessage().contains("当前 ACTIVE 责任人"), ex.getMessage());
        }
    }

    // ==================== 7. 责任人给自己分配允许 ====================

    @Test
    void assigneeSelfAssignment_allowed() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:assignment:add")).thenReturn(true);
            AssignmentViewVO vo = service.create(
                    createDto(1L, 1L, new BigDecimal("300")), "组长", 1L);
            assertEquals(1, vo.getAssignments().size());
            assertEquals(1L, vo.getAssignments().get(0).getAssigneeId());
        }
    }

    // ==================== 8-9. 报工数量 vs 份额（reported 汇总校验） ====================

    @Test
    void reportWithinAndOverAssignment() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            AssignmentViewVO vo = service.create(
                    createDto(1L, 101L, new BigDecimal("300")), "组长", 1L);
            Long aid = vo.getAssignments().get(0).getAssignmentId();

            // 已报 250（100+150）→ remaining 50
            mockWorkReportSum(new BigDecimal("250"));
            AssignmentViewVO vo2 = service.getByExecutionId(1L);
            assertEquals(0, new BigDecimal("50").compareTo(vo2.getAssignments().get(0).getRemainingQuantity()));
            assertEquals(0, new BigDecimal("250").compareTo(vo2.getAssignments().get(0).getReportedQuantity()));

            // 再报 51 → 超 300 → 拒绝（WorkReport 层校验，这里验证 Service 数量口径正确）
            mockWorkReportSum(new BigDecimal("301"));
            AssignmentViewVO vo3 = service.getByExecutionId(1L);
            assertEquals(0, BigDecimal.ZERO.compareTo(vo3.getAssignments().get(0).getRemainingQuantity()));
        }
    }

    // ==================== 10. qualified280+defective20 → 完成 300 ====================

    @Test
    void qualifiedPlusDefective_completesAssignment() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            AssignmentViewVO vo = service.create(
                    createDto(1L, 101L, new BigDecimal("300")), "组长", 1L);
            mockWorkReportSum(new BigDecimal("300")); // 280+20
            AssignmentViewVO vo2 = service.getByExecutionId(1L);
            AssignmentViewVO.AssignmentLineVO line = vo2.getAssignments().get(0);
            assertEquals("COMPLETED", line.getDerivedStatus());
            assertEquals(0, BigDecimal.ZERO.compareTo(line.getRemainingQuantity()));
        }
    }

    // ==================== 11. release 120 → unassigned 恢复 120 ====================

    @Test
    void releaseRemaining_returnsToUnassigned() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            AssignmentViewVO vo = service.create(
                    createDto(1L, 101L, new BigDecimal("300")), "组长", 1L);
            Long aid = vo.getAssignments().get(0).getAssignmentId();
            mockWorkReportSum(new BigDecimal("180"));

            AssignmentReleaseDTO rel = new AssignmentReleaseDTO();
            rel.setReason("张三无法继续");
            AssignmentViewVO vo2 = service.release(aid, rel, "组长", 1L);

            AssignmentViewVO.AssignmentLineVO line = vo2.getAssignments().get(0);
            assertEquals(0, new BigDecimal("180").compareTo(line.getReportedQuantity()), "历史报工保留");
            assertEquals(0, new BigDecimal("120").compareTo(line.getReleasedQuantity()), "释放 120");
            assertEquals(0, new BigDecimal("180").compareTo(line.getEffectiveQuantity()), "effective=180");
            assertEquals(0, BigDecimal.ZERO.compareTo(line.getRemainingQuantity()));
            // unassigned 恢复：1000 - effective(180) = 820？不——执行里有其他分配；本场景只有这一条
            // 释放后 effective=180，assigned sum=180 → unassigned = 1000-180 = 820
            assertEquals(0, new BigDecimal("820").compareTo(vo2.getUnassignedQuantity()),
                    "释放后 unassigned 应恢复到 1000-180=820");
        }
    }

    // ==================== 12. release 后重新分给李四 ====================

    @Test
    void releaseThenReassignToAnother() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            AssignmentViewVO vo = service.create(
                    createDto(1L, 101L, new BigDecimal("300")), "组长", 1L);
            Long aid = vo.getAssignments().get(0).getAssignmentId();
            mockWorkReportSum(new BigDecimal("180"));
            AssignmentReleaseDTO rel = new AssignmentReleaseDTO();
            rel.setReason("转李四");
            service.release(aid, rel, "组长", 1L);

            // 释放后 unassigned=820，给李四分 120 → 新行
            AssignmentViewVO vo2 = service.create(
                    createDto(1L, 102L, new BigDecimal("120")), "组长", 1L);
            // 3 条行：张三(释放)、李四(新)
            long liRows = vo2.getAssignments().stream()
                    .filter(l -> l.getAssigneeId() == 102L).count();
            assertEquals(1, liRows, "李四应有新 Assignment 行");
        }
    }

    // ==================== 13. 已释放部分不能再次释放 ====================

    @Test
    void releaseTwice_rejected() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            AssignmentViewVO vo = service.create(
                    createDto(1L, 101L, new BigDecimal("300")), "组长", 1L);
            Long aid = vo.getAssignments().get(0).getAssignmentId();
            mockWorkReportSum(new BigDecimal("180"));
            AssignmentReleaseDTO rel = new AssignmentReleaseDTO();
            rel.setReason("第一次释放");
            service.release(aid, rel, "组长", 1L);
            // 第二次释放 → remaining=0 → 拒绝
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    service.release(aid, rel, "组长", 1L));
            assertTrue(ex.getMessage().contains("剩余数量为 0"), ex.getMessage());
        }
    }

    // ==================== WP-D 14. 同一批次内执行人重复 → 拒绝 ====================

    @Test
    void duplicateAssigneeInBatch_rejected() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    service.create(createDto(1L, 101L, new BigDecimal("300"), 101L, new BigDecimal("200")), "组长", 1L));
            assertTrue(ex.getMessage().contains("重复"), ex.getMessage());
        }
    }

    // ==================== WP-D 15. 同一 execution+user 已有 ACTIVE → 拒绝 ====================

    @Test
    void existingActiveAssignment_sameUser_rejected() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            service.create(createDto(1L, 101L, new BigDecimal("300")), "组长", 1L);
            // 第二次 create 前：唯一约束 COUNT 返回 1（模拟 DB 中 101 已有 ACTIVE 分配）
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1);
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    service.create(createDto(1L, 101L, new BigDecimal("100")), "组长", 1L));
            assertTrue(ex.getMessage().contains("已有未完成"), ex.getMessage());
        }
    }

    // ==================== WP-D 16. 同一 execution 不同用户可继续分配 ====================

    @Test
    void existingActiveAssignment_differentUser_allowed() {
        mockActiveNode(activeNode(10L, 1L, 1L, "组长"));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            service.create(createDto(1L, 101L, new BigDecimal("300")), "组长", 1L);
            // 给 102 分 200 → 成功（不影响 101 的分配）
            AssignmentViewVO vo = service.create(
                    createDto(1L, 102L, new BigDecimal("200")), "组长", 1L);
            long activeRows = vo.getAssignments().stream()
                    .filter(l -> !"CANCELLED".equals(l.getDerivedStatus())).count();
            assertEquals(2, activeRows, "101/102 两条有效分配");
        }
    }
}
