package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.TaskAssignItemDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionTaskNode;
import com.jjx.production.domain.vo.TaskNodeVO;
import com.jjx.production.enums.TaskNodeStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionTaskNodeMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.impl.TaskNodeServiceImpl;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.mapper.SysUserMapper;
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
 * P1 Task Tree Core 定向测试：统一 TaskNode 模型（替代旧 DispatchNode + Assignment）
 * - root 建立/幂等
 * - 多人分配后父节点可分配数量正确递减
 * - 超过可分配数量拒绝
 * - 部分分配允许
 * - 非节点持有人不能分配
 * - 同一父节点多人创建正确（含树结构）
 */
class TaskNodeServiceTest {

    private TaskNodeServiceImpl service;
    private ProductionTaskNodeMapper taskNodeMapper;
    private ProductionOperationExecutionMapper executionMapper;

    /** 已建立根节点（insert 后回填 ID） */
    private ProductionTaskNode root;

    /** 根节点下的子节点集合（occupiedByChildren selectList 返回） */
    private final List<ProductionTaskNode> children = new ArrayList<>();

    private long nextNodeId = 1L;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        taskNodeMapper = mock(ProductionTaskNodeMapper.class);
        executionMapper = mock(ProductionOperationExecutionMapper.class);
        var sysUserMapper = mock(SysUserMapper.class);
        var workReportMapper = mock(ProductionWorkReportMapper.class);

        Constructor<?> ctor = TaskNodeServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (TaskNodeServiceImpl) ctor.newInstance(taskNodeMapper, executionMapper, sysUserMapper,
                workReportMapper, mock(JdbcTemplate.class), mock(SysDeptMapper.class));

        // ensureRoot 的根存在性检查：首次 null，之后返回 root
        when(taskNodeMapper.selectOne(any())).thenReturn(null).thenAnswer(inv -> root);
        // availableToAssign/remaining 走 selectById
        when(taskNodeMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (root != null && id.equals(root.getTaskNodeId())) return root;
            return children.stream().filter(c -> id.equals(c.getTaskNodeId())).findFirst().orElse(null);
        });
        // occupiedByChildren：返回根下的子节点
        when(taskNodeMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(children));
        // insert：回填自增 ID
        when(taskNodeMapper.insert(any(ProductionTaskNode.class))).thenAnswer(inv -> {
            ProductionTaskNode n = inv.getArgument(0);
            n.setTaskNodeId(nextNodeId++);
            return 1;
        });
        // 姓名快照
        when(sysUserMapper.selectById(any())).thenAnswer(inv -> {
            SysUser u = new SysUser();
            u.setUserId(inv.getArgument(0));
            u.setNickName("用户" + inv.getArgument(0));
            return u;
        });
    }

    private ProductionOperationExecution exec(Long id, BigDecimal planned, Long operatorId) {
        ProductionOperationExecution e = new ProductionOperationExecution();
        e.setExecutionId(id);
        e.setInputQuantity(planned);
        e.setOperatorId(operatorId);
        e.setOperatorName("工序负责人");
        return e;
    }

    /** 建立 root=1000（execution 500，operatorId=1） */
    private ProductionTaskNode root1000() {
        when(executionMapper.selectById(500L)).thenReturn(exec(500L, new BigDecimal("1000"), 1L));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            mocked.when(SecurityUtils::getUsername).thenReturn("u1");
            mocked.when(() -> SecurityUtils.hasPermission(anyString())).thenReturn(false);
            root = service.ensureRoot(500L);
        }
        return root;
    }

    private List<TaskAssignItemDTO> assignItems(Object... userIdQtyPairs) {
        List<TaskAssignItemDTO> items = new ArrayList<>();
        for (int i = 0; i < userIdQtyPairs.length; i += 2) {
            TaskAssignItemDTO it = new TaskAssignItemDTO();
            it.setUserId((Long) userIdQtyPairs[i]);
            it.setQuantity((BigDecimal) userIdQtyPairs[i + 1]);
            items.add(it);
        }
        return items;
    }

    private List<ProductionTaskNode> assign(Object... userIdQtyPairs) {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            mocked.when(SecurityUtils::getUsername).thenReturn("u1");
            mocked.when(() -> SecurityUtils.hasPermission(anyString())).thenReturn(false);
            return service.assignChildren(root.getTaskNodeId(), assignItems(userIdQtyPairs));
        }
    }

    // ==================== 1. root 1000 正确建立 ====================

    @Test
    void ensureRoot_createsRootWithPlannedQuantity() {
        when(executionMapper.selectById(500L)).thenReturn(exec(500L, new BigDecimal("1000"), 1L));
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            mocked.when(SecurityUtils::getUsername).thenReturn("u1");
            mocked.when(() -> SecurityUtils.hasPermission(anyString())).thenReturn(false);
            root = service.ensureRoot(500L);
        }
        assertNotNull(root.getTaskNodeId());
        assertNull(root.getParentNodeId(), "根节点 parentNodeId 应为 null");
        assertEquals(500L, root.getExecutionId());
        assertEquals(new BigDecimal("1000"), root.getTaskQuantity());
        assertEquals(BigDecimal.ZERO, root.getRecalledQuantity());
        verify(taskNodeMapper).insert(root);
    }

    @Test
    void ensureRoot_isIdempotent() {
        root1000();
        // 第二次 ensureRoot：selectOne 返回已存在根，不重复 insert
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            mocked.when(SecurityUtils::getUsername).thenReturn("u1");
            mocked.when(() -> SecurityUtils.hasPermission(anyString())).thenReturn(false);
            ProductionTaskNode again = service.ensureRoot(500L);
            assertSame(root, again);
        }
        verify(taskNodeMapper, times(1)).insert(root);
    }

    // ==================== 2. 1000 分张三300 + 李四200 → 父节点可分配500 ====================

    @Test
    void assignThenParentAvailableDropsTo500() {
        root1000();
        List<ProductionTaskNode> created = assign(
                101L, new BigDecimal("300"),
                102L, new BigDecimal("200"));
        assertEquals(2, created.size());
        children.addAll(created);

        assertEquals(new BigDecimal("500"), service.availableToAssign(root.getTaskNodeId()));
    }

    // ==================== 3. 再分王五300 → 剩200 ====================

    @Test
    void assignThird_remaining200() {
        root1000();
        List<ProductionTaskNode> first = assign(
                101L, new BigDecimal("300"),
                102L, new BigDecimal("200"));
        children.addAll(first);
        List<ProductionTaskNode> second = assign(103L, new BigDecimal("300"));
        children.addAll(second);

        assertEquals(new BigDecimal("200"), service.availableToAssign(root.getTaskNodeId()));
        assertEquals(new BigDecimal("200"), service.remaining(root.getTaskNodeId()));
    }

    // ==================== 4. 超过200拒绝 ====================

    @Test
    void assignExceedingAvailable_rejected() {
        root1000();
        List<ProductionTaskNode> first = assign(
                101L, new BigDecimal("300"),
                102L, new BigDecimal("200"));
        children.addAll(first);
        List<ProductionTaskNode> second = assign(103L, new BigDecimal("300"));
        children.addAll(second);
        // 已分配 800，剩余可分配 200
        assertEquals(new BigDecimal("200"), service.availableToAssign(root.getTaskNodeId()));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            mocked.when(SecurityUtils::getUsername).thenReturn("u1");
            mocked.when(() -> SecurityUtils.hasPermission(anyString())).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.assignChildren(root.getTaskNodeId(), assignItems(103L, new BigDecimal("201"))));
            assertTrue(ex.getMessage().contains("超过节点可分配数量"), ex.getMessage());
        }
        // 拒绝后未创建新子节点，可分配数量不变
        assertEquals(3, children.size());
        assertEquals(new BigDecimal("200"), service.availableToAssign(root.getTaskNodeId()));
    }

    // ==================== 5. 可以部分分配 ====================

    @Test
    void partialAssign_allowed() {
        root1000();
        List<ProductionTaskNode> created = assign(101L, new BigDecimal("100"));
        children.addAll(created);

        assertEquals(new BigDecimal("900"), service.availableToAssign(root.getTaskNodeId()));
    }

    // ==================== 6. 非当前节点持有人不能分配 ====================

    @Test
    void nonHolderCannotAssign() {
        root1000(); // root.assigneeId = 1
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(99L); // 非持有人
            mocked.when(SecurityUtils::getUsername).thenReturn("u99");
            mocked.when(() -> SecurityUtils.hasPermission(anyString())).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.assignChildren(root.getTaskNodeId(), assignItems(101L, new BigDecimal("100"))));
            assertTrue(ex.getMessage().contains("持有人"), ex.getMessage());
        }
        // 仅 ensureRoot 创建了根节点，非持有人分配未创建任何子节点
        verify(taskNodeMapper, times(1)).insert(any(ProductionTaskNode.class));
    }

    // ==================== 7. 同一父节点多人创建正确（含树结构） ====================

    @Test
    void multipleAssigneesSameParent_createdCorrectly() {
        root1000();
        List<ProductionTaskNode> created = assign(
                101L, new BigDecimal("300"),
                102L, new BigDecimal("200"),
                103L, new BigDecimal("500"));
        children.addAll(created);
        assertEquals(3, created.size());

        // 三个子节点字段正确
        for (ProductionTaskNode c : created) {
            assertEquals(root.getTaskNodeId(), c.getParentNodeId());
            assertEquals(500L, c.getExecutionId());
            assertNotNull(c.getAssigneeName());
            assertEquals(BigDecimal.ZERO, c.getRecalledQuantity());
        }
        // 合计 1000 全部分完 → 父节点可分配 0
        assertEquals(BigDecimal.ZERO, service.availableToAssign(root.getTaskNodeId()));

        // 树结构：root 下 3 个直接子节点
        when(taskNodeMapper.selectList(any())).thenAnswer(inv -> {
            List<ProductionTaskNode> all = new ArrayList<>();
            all.add(root);
            all.addAll(children);
            return all;
        });
        TaskNodeVO tree = service.getTaskTree(500L);
        assertEquals(root.getTaskNodeId(), tree.getTaskNodeId());
        assertEquals(3, tree.getChildren().size());
        assertEquals(new BigDecimal("0"), tree.getRemainingQuantity());
        assertEquals(3, tree.getChildren().stream()
                .map(TaskNodeVO::getAssigneeId).distinct().count());
        // selfReported：P1 未接入 WorkReport，恒为 0（完成量后续从 WorkReport 动态汇总，不落 TaskNode）
        assertEquals(BigDecimal.ZERO, tree.getSelfReported());
    }
}
