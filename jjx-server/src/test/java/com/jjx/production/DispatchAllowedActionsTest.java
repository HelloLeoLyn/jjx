package com.jjx.production;

import com.jjx.production.domain.vo.DispatchVO;
import com.jjx.production.service.DispatchNodeReadService;
import com.jjx.production.service.impl.DispatchServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * WP-C 回归测试：allowedActions 责任链动作投影
 * <p>
 * 覆盖（对应 WP-C 测试清单 1-11、14）：
 * 1. 无 ACTIVE + assign 权限 → ASSIGN
 * 2. 无 assign 权限 → 无 ASSIGN
 * 3. 当前责任人 + delegate 权限 → DELEGATE
 * 4. 非当前责任人即使有普通功能权限 → 无 DELEGATE
 * 5. 当前责任人本人 → 无 REASSIGN
 * 6. 调度角色 + reassign 权限 → REASSIGN
 * 7. root 节点（parentNodeId=null）→ 无 RETURN
 * 8. 有 parent + 当前责任人 → RETURN
 * 9. 非当前责任人 → 无 RETURN
 * 10. Execution COMPLETED → allowedActions 空
 * 11. Execution CANCELLED → allowedActions 空
 * 14. ASSIGN_WORK 只给当前责任人 + assignment 权限
 */
class DispatchAllowedActionsTest {

    private DispatchServiceImpl service;
    private Method buildAllowedActions;

    @BeforeEach
    void setUp() throws Exception {
        var ctor = DispatchServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object[] nulls = new Object[ctor.getParameterCount()];
        service = (DispatchServiceImpl) ctor.newInstance(nulls);
        Field f = DispatchServiceImpl.class.getDeclaredField("jdbcTemplate");
        f.setAccessible(true);
        f.set(service, null);
        Field nf = DispatchServiceImpl.class.getDeclaredField("nodeReadService");
        nf.setAccessible(true);
        nf.set(service, mock(DispatchNodeReadService.class));

        buildAllowedActions = DispatchServiceImpl.class.getDeclaredMethod(
                "buildAllowedActions", DispatchVO.class,
                com.jjx.production.domain.vo.DispatchNodeVO.class);
        buildAllowedActions.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private List<String> actions(DispatchVO vo, com.jjx.production.domain.vo.DispatchNodeVO cur) throws Exception {
        return (List<String>) buildAllowedActions.invoke(service, vo, cur);
    }

    private DispatchVO vo(Integer execStatus, Integer dispatchStatus) {
        DispatchVO v = new DispatchVO();
        v.setExecutionStatus(execStatus);
        v.setDispatchStatus(dispatchStatus);
        return v;
    }

    private com.jjx.production.domain.vo.DispatchNodeVO activeNode(Long assigneeId, Long parentNodeId) {
        com.jjx.production.domain.vo.DispatchNodeVO n = new com.jjx.production.domain.vo.DispatchNodeVO();
        n.setNodeId(10L);
        n.setDispatchId(1L);
        n.setAssigneeId(assigneeId);
        n.setParentNodeId(parentNodeId);
        n.setNodeStatus("ACTIVE");
        return n;
    }

    /** 权限模板：super=false，指定各权限点 */
    private void perms(MockedStatic<SecurityUtils> mocked, boolean assign, boolean delegate,
                       boolean reassign, boolean ret, boolean assignment, Long me) {
        mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
        mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(assign);
        mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:delegate")).thenReturn(delegate);
        mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:reassign")).thenReturn(reassign);
        mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:return")).thenReturn(ret);
        mocked.when(() -> SecurityUtils.hasPermission("production:assignment:add")).thenReturn(assignment);
        mocked.when(SecurityUtils::getUserId).thenReturn(me);
    }

    // 1. 无 ACTIVE + assign 权限 → ASSIGN
    @Test
    void noActive_withAssignPerm_assign() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, true, false, false, false, false, 1L);
            assertTrue(actions(vo(2, null), null).contains("ASSIGN"));
        }
    }

    // 2. 无 assign 权限 → 无 ASSIGN
    @Test
    void noActive_withoutAssignPerm_noAssign() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, false, false, false, false, false, 1L);
            assertFalse(actions(vo(2, null), null).contains("ASSIGN"));
        }
    }

    // 3. 当前责任人 + delegate 权限 → DELEGATE
    @Test
    void assigneeWithDelegatePerm_delegate() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, false, true, false, false, false, 1L);
            assertTrue(actions(vo(2, 2), activeNode(1L, null)).contains("DELEGATE"));
        }
    }

    // 4. 非当前责任人即使有普通功能权限 → 无 DELEGATE
    @Test
    void notAssignee_noDelegateEvenWithPerm() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            // 有 list 权限（隐含：无 delegate/assign 权限）但非 ACTIVE assignee
            perms(mocked, false, false, false, false, false, 99L);
            assertFalse(actions(vo(2, 2), activeNode(1L, null)).contains("DELEGATE"));
        }
    }

    // 5. 当前责任人本人 → 无 REASSIGN
    @Test
    void assigneeSelf_noReassign() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, false, false, false, false, false, 1L);
            List<String> a = actions(vo(2, 2), activeNode(1L, null));
            assertFalse(a.contains("REASSIGN"), "本人不应有 REASSIGN: " + a);
        }
    }

    // 6. 调度角色 + reassign 权限 → REASSIGN
    @Test
    void dispatcherWithReassignPerm_reassign() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, false, false, true, false, false, 99L);
            assertTrue(actions(vo(2, 2), activeNode(1L, null)).contains("REASSIGN"));
        }
    }

    // 7. root 节点 → 无 RETURN
    @Test
    void rootNode_noReturn() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, false, false, false, true, false, 1L);
            assertFalse(actions(vo(2, 2), activeNode(1L, null)).contains("RETURN"));
        }
    }

    // 8. 有 parent + 当前责任人 → RETURN
    @Test
    void withParent_assignee_return() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, false, false, false, false, false, 1L);
            assertTrue(actions(vo(2, 2), activeNode(1L, 5L)).contains("RETURN"));
        }
    }

    // 9. 非当前责任人 → 无 RETURN（即使有 return 权限？——规则：return 权限可代操作，此场景测"无权限非本人"）
    @Test
    void notAssignee_noReturn() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, false, false, false, false, false, 99L);
            assertFalse(actions(vo(2, 2), activeNode(1L, 5L)).contains("RETURN"));
        }
    }

    // 10. Execution COMPLETED → 空
    @Test
    void executionCompleted_frozen() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, true, true, true, true, true, 1L);
            List<String> a = actions(vo(4, 4), activeNode(1L, 5L));
            assertTrue(a.isEmpty(), "COMPLETED 应冻结: " + a);
        }
    }

    // 11. Execution CANCELLED → 空
    @Test
    void executionCancelled_frozen() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, true, true, true, true, true, 1L);
            List<String> a = actions(vo(6, null), activeNode(1L, 5L));
            assertTrue(a.isEmpty(), "CANCELLED 应冻结: " + a);
        }
    }

    // 14. ASSIGN_WORK：当前责任人本人（有 assignment 权限）→ 有；非本人 → 无
    @Test
    void assignWork_onlyForAssigneeWithPerm() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, false, false, false, false, true, 1L);
            assertTrue(actions(vo(2, 2), activeNode(1L, null)).contains("ASSIGN_WORK"));
        }
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, false, false, false, false, true, 99L);
            assertFalse(actions(vo(2, 2), activeNode(1L, null)).contains("ASSIGN_WORK"));
        }
    }

    // 补充：dispatch COMPLETED → 空
    @Test
    void dispatchCompleted_frozen() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            perms(mocked, true, true, true, true, true, 1L);
            List<String> a = actions(vo(2, 4), activeNode(1L, 5L));
            assertTrue(a.isEmpty(), "Dispatch COMPLETED 应冻结: " + a);
        }
    }
}
