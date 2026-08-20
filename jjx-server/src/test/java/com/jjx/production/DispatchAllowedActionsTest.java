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
 * V1 Fix Pack 回归测试：FIX-1（无 dispatch + assign 权限 → allowedActions 含 ASSIGN）
 * <p>
 * 覆盖：
 * 1. 无 dispatch（cur=null）+ 有 assign 权限 → allowedActions 包含 ASSIGN
 * 2. 无 dispatch + 无权限 → 不包含 ASSIGN
 * 3. 有 ACTIVE responsibility（cur 非空）→ 不再提供首次 ASSIGN（提供 DELEGATE/REASSIGN）
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

    private com.jjx.production.domain.vo.DispatchNodeVO activeNode(Long assigneeId, Long parentNodeId) {
        com.jjx.production.domain.vo.DispatchNodeVO n = new com.jjx.production.domain.vo.DispatchNodeVO();
        n.setNodeId(10L);
        n.setDispatchId(1L);
        n.setAssigneeId(assigneeId);
        n.setParentNodeId(parentNodeId);
        n.setNodeStatus("ACTIVE");
        return n;
    }

    @Test
    void noDispatch_withAssignPermission_includesAssign() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(true);
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);

            List<String> a = actions(new DispatchVO(), null);
            assertTrue(a.contains("ASSIGN"), "无 dispatch + assign 权限 → allowedActions 应含 ASSIGN: " + a);
        }
    }

    @Test
    void noDispatch_withoutPermission_noAssign() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(false);
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);

            List<String> a = actions(new DispatchVO(), null);
            assertFalse(a.contains("ASSIGN"), "无权限不应含 ASSIGN: " + a);
        }
    }

    @Test
    void withActiveResponsibility_noInitialAssign() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(true);
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);

            // 有 ACTIVE node（assignee=当前用户）→ 不再提供首次 ASSIGN，提供 DELEGATE/REASSIGN
            List<String> a = actions(new DispatchVO(), activeNode(1L, null));
            assertFalse(a.contains("ASSIGN"), "已有 ACTIVE responsibility 不应提供首次 ASSIGN: " + a);
            assertTrue(a.contains("DELEGATE"), "应提供 DELEGATE: " + a);
            assertTrue(a.contains("REASSIGN"), "应提供 REASSIGN: " + a);
        }
    }

    @Test
    void withActiveResponsibility_otherAssignee_viewOnly() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(false);
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);

            // ACTIVE node 是别人（assignee=2）且无 assign 权限 → 无任何动作
            List<String> a = actions(new DispatchVO(), activeNode(2L, null));
            assertTrue(a.isEmpty(), "非本人且无权限应无动作: " + a);
        }
    }
}
