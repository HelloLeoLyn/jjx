package com.jjx.production;

import com.jjx.production.domain.vo.DispatchNodeVO;
import com.jjx.production.domain.vo.DispatchVO;
import com.jjx.production.service.impl.DispatchServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * P1-D 回归测试：allowedActions 动作能力投影
 * 规则与 DispatchActionServiceImpl 权限一致（前端按钮显隐用，非安全边界）：
 * - 无 ACTIVE：超管/有 assign 权限 → ASSIGN
 * - 有 ACTIVE：超管/有 assign 权限/ACTIVE 本人 → DELEGATE+REASSIGN
 * - 有 ACTIVE 且 parentNodeId!=null 且（超管/ACTIVE 本人）→ RETURN
 */
class DispatchAllowedActionsTest {

    private DispatchServiceImpl service;

    private DispatchNodeVO node(Long id, Long parentId, Long assigneeId) {
        DispatchNodeVO v = new DispatchNodeVO();
        v.setNodeId(id);
        v.setParentNodeId(parentId);
        v.setAssigneeId(assigneeId);
        return v;
    }

    @SuppressWarnings("unchecked")
    private List<String> allowedActions(DispatchVO vo, DispatchNodeVO cur) throws Exception {
        // 反射构造（仅用于静态方法测试；buildAllowedActions 不依赖实例字段）
        var ctor = DispatchServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object[] nulls = new Object[ctor.getParameterCount()];
        service = (DispatchServiceImpl) ctor.newInstance(nulls);
        Method m = DispatchServiceImpl.class.getDeclaredMethod("buildAllowedActions", DispatchVO.class, DispatchNodeVO.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(service, vo, cur);
    }

    @Test
    void noActiveAssigneeWithPermissionCanAssign() throws Exception {
        DispatchVO vo = new DispatchVO();
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(true);
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            List<String> actions = allowedActions(vo, null);
            assertTrue(actions.contains("ASSIGN"));
            assertFalse(actions.contains("DELEGATE"));
        }
    }

    @Test
    void noActiveNoPermissionCannotAssign() throws Exception {
        DispatchVO vo = new DispatchVO();
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(false);
            mocked.when(SecurityUtils::getUserId).thenReturn(1L);
            assertTrue(allowedActions(vo, null).isEmpty());
        }
    }

    @Test
    void activeAssigneeHimselfCanDelegateReassignReturn() throws Exception {
        DispatchVO vo = new DispatchVO();
        DispatchNodeVO cur = node(5L, 3L, 104L); // parent 非空
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(false);
            mocked.when(SecurityUtils::getUserId).thenReturn(104L); // 本人
            List<String> actions = allowedActions(vo, cur);
            assertTrue(actions.contains("DELEGATE"));
            assertTrue(actions.contains("REASSIGN"));
            assertTrue(actions.contains("RETURN"));
            assertFalse(actions.contains("ASSIGN"));
        }
    }

    @Test
    void unrelatedUserCannotActOnActiveNode() throws Exception {
        DispatchVO vo = new DispatchVO();
        DispatchNodeVO cur = node(5L, 3L, 104L);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(false);
            mocked.when(SecurityUtils::getUserId).thenReturn(99L); // 无关用户
            assertTrue(allowedActions(vo, cur).isEmpty());
        }
    }

    @Test
    void rootActiveCannotReturnButCanDelegate() throws Exception {
        DispatchVO vo = new DispatchVO();
        DispatchNodeVO cur = node(1L, null, 104L); // root（parent=null）
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(false);
            mocked.when(SecurityUtils::getUserId).thenReturn(104L);
            List<String> actions = allowedActions(vo, cur);
            assertTrue(actions.contains("DELEGATE"));
            assertTrue(actions.contains("REASSIGN"));
            assertFalse(actions.contains("RETURN"), "root 节点不可 RETURN");
        }
    }

    @Test
    void adminWithAssignPermissionCanDelegateReassign() throws Exception {
        DispatchVO vo = new DispatchVO();
        DispatchNodeVO cur = node(5L, 3L, 104L);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(true);
            mocked.when(SecurityUtils::getUserId).thenReturn(94L); // 管理员（非 ACTIVE）
            List<String> actions = allowedActions(vo, cur);
            assertTrue(actions.contains("DELEGATE"));
            assertTrue(actions.contains("REASSIGN"));
            // 管理员代操作不可 RETURN（RETURN 仅本人/超管，与后端一致）
            assertFalse(actions.contains("RETURN"));
        }
    }
}
