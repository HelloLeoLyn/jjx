package com.jjx.production;

import com.jjx.production.mapper.ProductionDispatchLogMapper;
import com.jjx.production.mapper.ProductionDispatchMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.impl.DispatchServiceImpl;
import com.jjx.production.service.DispatchNodeReadService;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.mapper.SysUserMapper;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * P0-04 回归测试：初始派工权限
 * - 不再依赖 deptId=5（isProductionManager 已删除）
 * - 拥有 production:dispatch:assign 权限的用户可初始派工
 * - 无权限且未被派工过的用户不能初始派工
 * 说明：JdbcTemplate 无法被 Mockito mock（Java 25 不兼容），故注入 null 依赖；
 *       nodeReadService 注入 mock，hasUserParticipated 默认 false 覆盖"未派工过"场景。
 */
class DispatchPermissionTest {

    private DispatchServiceImpl service;
    private DispatchNodeReadService nodeReadService;

    @BeforeEach
    void setUp() throws Exception {
        // 通过无参构造反射实例化（Lombok @RequiredArgsConstructor 会生成全参构造，
        // 但 DispatchServiceImpl 直接 new 需要 8 个依赖；这里用反射绕过）
        var ctor = DispatchServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object[] nulls = new Object[ctor.getParameterCount()];
        service = (DispatchServiceImpl) ctor.newInstance(nulls);
        // 明确把 jdbcTemplate 置 null（P1-B 后 isDispatched 委托 nodeReadService，不再直接碰 jdbcTemplate）
        Field f = DispatchServiceImpl.class.getDeclaredField("jdbcTemplate");
        f.setAccessible(true);
        f.set(service, null);
        // P1-B：注入 mock nodeReadService（hasUserParticipated 默认 false = 未派工过）
        nodeReadService = mock(DispatchNodeReadService.class);
        Field nf = DispatchServiceImpl.class.getDeclaredField("nodeReadService");
        nf.setAccessible(true);
        nf.set(service, nodeReadService);
    }

    @Test
    void hasAssignPermissionCanInitialDispatch() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(true);
            assertTrue(service.canAssign(999L));
        }
    }

    @Test
    void superAdminCanAlwaysDispatch() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            assertTrue(service.canAssign(999L));
        }
    }

    @Test
    void noPermissionAndNotDispatchedCannotDispatch() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:assign")).thenReturn(false);
            // jdbcTemplate 为 null → isDispatched 返回 false → canAssign false
            assertFalse(service.canAssign(999L));
        }
    }
}
