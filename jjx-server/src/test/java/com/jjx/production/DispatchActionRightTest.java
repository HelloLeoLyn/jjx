package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import com.jjx.production.service.impl.DispatchActionServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * WP-C 回归测试：后端 ActionService 动作权限同步收口（防直接调 API 绕过）
 * <p>
 * 覆盖：
 * - checkNodeOperatorRight：delegate 须本人/delegate 权限；assign 权限不再放行普通用户
 * - checkReassignRight：本人禁止自改派；reassign 权限放行
 * - checkReturnRight：本人/return 权限
 * 与 allowedActions 规则一致（防前端投影与后端动作不一致）。
 */
class DispatchActionRightTest {

    private DispatchActionServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<?> ctor = DispatchActionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object[] nulls = new Object[ctor.getParameterCount()];
        service = (DispatchActionServiceImpl) ctor.newInstance(nulls);
    }

    private ProductionDispatchNode active(Long assigneeId) {
        ProductionDispatchNode n = new ProductionDispatchNode();
        n.setNodeId(10L);
        n.setDispatchId(1L);
        n.setAssigneeId(assigneeId);
        n.setAssigneeName("组长");
        n.setNodeStatus("ACTIVE");
        return n;
    }

    private void invoke(String method, ProductionDispatchNode active, Long operatorId) throws Exception {
        Method m = DispatchActionServiceImpl.class.getDeclaredMethod(method,
                ProductionDispatchNode.class, Long.class);
        m.setAccessible(true);
        try {
            m.invoke(service, active, operatorId);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    // 1. delegate：本人 + delegate 权限 → 放行（读法 A 双条件）
    @Test
    void delegate_assigneeWithPerm_ok() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:delegate")).thenReturn(true);
            assertDoesNotThrow(() -> invoke("checkNodeOperatorRight", active(1L), 1L));
        }
    }

    // 2. delegate：本人但无 delegate 权限（如操作工当责任节点）→ 拒绝
    @Test
    void delegate_assigneeWithoutPerm_rejected() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:delegate")).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> invoke("checkNodeOperatorRight", active(1L), 1L));
            assertTrue(ex.getMessage().contains("下派"), ex.getMessage());
        }
    }

    // 3. delegate：非本人 + 有 delegate 权限 → 拒绝（BUG-02C：权限只是附加条件，不能仅凭权限代操作）
    @Test
    void delegate_notAssigneeEvenWithPerm_rejected() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:delegate")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> invoke("checkNodeOperatorRight", active(1L), 99L));
            assertTrue(ex.getMessage().contains("下派"), ex.getMessage());
        }
    }

    // 4. reassign：本人禁止自改派
    @Test
    void reassign_selfForbidden() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:reassign")).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> invoke("checkReassignRight", active(1L), 1L));
            assertTrue(ex.getMessage().contains("不能改派自己"), ex.getMessage());
        }
    }

    // 5. reassign：调度权限放行（非本人）
    @Test
    void reassign_withPerm_ok() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:reassign")).thenReturn(true);
            assertDoesNotThrow(() -> invoke("checkReassignRight", active(1L), 99L));
        }
    }

    // 6. return：本人 + return 权限 → 放行（读法 A 双条件）
    @Test
    void return_assigneeWithPerm_ok() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:return")).thenReturn(true);
            assertDoesNotThrow(() -> invoke("checkReturnRight", active(1L), 1L));
        }
    }

    // 7. return：本人但无 return 权限 → 拒绝
    @Test
    void return_assigneeWithoutPerm_rejected() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:return")).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> invoke("checkReturnRight", active(1L), 1L));
            assertTrue(ex.getMessage().contains("退回"), ex.getMessage());
        }
    }

    // 8. return：非本人 + 有 return 权限 → 拒绝（不能仅凭权限代退回）
    @Test
    void return_notAssigneeEvenWithPerm_rejected() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:return")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> invoke("checkReturnRight", active(1L), 99L));
            assertTrue(ex.getMessage().contains("退回"), ex.getMessage());
        }
    }
}
