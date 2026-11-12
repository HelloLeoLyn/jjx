package com.jjx.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.controller.BomEditSessionController;
import com.jjx.product.domain.dto.BomEditSessionRequest;
import com.jjx.product.domain.vo.BomEditSessionVO;
import com.jjx.product.service.BomEditSessionService;
import com.jjx.product.service.impl.BomEditSessionServiceImpl;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 强制解锁的真实权限契约（dev-20261111-018 修复卡 F3）。
 *
 * <p>用 Sa-Token 的 SaInterceptor + 真实权限列表判定（超管“*”与普通 edit 的差别是真实判定，
 * 不是只看注解字符串）；service 用替身，本类不代表真实 Redis/MySQL 已验。
 */
class BomEditSessionControllerTest {

    private static final String BASE = "/engineering/bom/1001/edit-session";
    private final BomEditSessionService service = mock(BomEditSessionService.class);
    private final BomEditSessionController controller = new BomEditSessionController(service);

    private void loginWith(List<String> permissions) {
        StpLogic logic = spy(new StpLogic("login"));
        doReturn(42L).when(logic).getLoginId();
        doReturn(permissions).when(logic).getPermissionList(any());
        StpUtil.setStpLogic(logic);
    }

    @Test
    void ordinaryEditPermissionCannotForceUnlockNorTakeover() throws Exception {
        StpLogic original = StpUtil.getStpLogic();
        try {
            loginWith(List.of("engineering:bom:edit", "engineering:bom:add"));
            var mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new SaInterceptor()).build();

            assertPermissionDenied(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post(BASE + "/unlock")));
            assertPermissionDenied(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post(BASE).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"takeover\":true,\"clientId\":\"tab-A\"}")));

            // 普通 edit 绝不能冒充强制解锁：服务层一次都不该被调用
            verifyNoInteractions(service);
        } finally {
            StpUtil.setStpLogic(original);
        }
    }

    @Test
    void dedicatedPermissionUnlocksAndReturnsRemovedOwner() throws Exception {
        StpLogic original = StpUtil.getStpLogic();
        try {
            loginWith(List.of(BomEditSessionServiceImpl.FORCE_UNLOCK_PERMISSION));
            var owner = new BomEditSessionVO();
            owner.setLeaseAvailable(true);
            owner.setOccupied(false);
            owner.setOwnerDisplayName("张三");
            owner.setMessage("已强制解除 bomId=1001 的编辑占用（原占用者：张三（userId=7））");
            when(service.forceUnlock(1001L)).thenReturn(owner);
            var mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new SaInterceptor()).build();

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(BASE + "/unlock"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.data.ownerDisplayName").value("张三"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.data.occupied").value(false))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.data.token").doesNotExist());
            verify(service).forceUnlock(1001L);
        } finally {
            StpUtil.setStpLogic(original);
        }
    }

    @Test
    void superAdminWildcardCanForceUnlock() throws Exception {
        StpLogic original = StpUtil.getStpLogic();
        try {
            // PermissionServiceImpl 对 user_id=1 / admin 角色返回 Sa-Token 通配“*”
            loginWith(List.of("*"));
            when(service.forceUnlock(anyLong())).thenReturn(new BomEditSessionVO());
            var mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new SaInterceptor()).build();

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(BASE + "/unlock"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200));
            verify(service).forceUnlock(1001L);
        } finally {
            StpUtil.setStpLogic(original);
        }
    }

    @Test
    void takeoverUnlocksThenReacquiresAndReturnsNewToken() throws Exception {
        StpLogic original = StpUtil.getStpLogic();
        try {
            loginWith(List.of("*"));
            var granted = new BomEditSessionVO();
            granted.setLeaseAvailable(true);
            granted.setAcquired(true);
            granted.setToken("new-token");
            when(service.acquire(1001L, "tab-A")).thenReturn(granted);
            var mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new SaInterceptor()).build();

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"takeover\":true,\"clientId\":\"tab-A\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.data.token").value("new-token"));

            var order = inOrder(service);
            order.verify(service).forceUnlock(1001L);
            order.verify(service).acquire(1001L, "tab-A");
        } finally {
            StpUtil.setStpLogic(original);
        }
    }

    @Test
    void auditFailureIsNeverReportedAsSuccess() {
        when(service.forceUnlock(1001L)).thenThrow(
                new BusinessException(503, BomEditSessionServiceImpl.MSG_AUDIT_FAILED));
        var ex = assertThrows(BusinessException.class, () -> controller.unlock(1001L));
        assertEquals(Integer.valueOf(503), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_EDIT_FORCE_UNLOCK_AUDIT_FAILED"), ex.getMessage());
    }

    @Test
    void unlockEndpointRequiresDedicatedPermissionCode() throws Exception {
        var unlock = BomEditSessionController.class.getMethod("unlock", Long.class);
        var annotation = unlock.getAnnotation(SaCheckPermission.class);
        assertNotNull(annotation, "unlock 必须有专用权限码");
        assertArrayEquals(new String[]{BomEditSessionServiceImpl.FORCE_UNLOCK_PERMISSION}, annotation.value());
        assertEquals(cn.dev33.satoken.annotation.SaMode.AND, annotation.mode(), "不能与 engineering:bom:edit 并列放宽");
    }

    /** Sa-Token 拦截器拒绝：要么被控制器本地异常处理成 403，要么以 NotPermissionException 抛出。 */
    private static void assertPermissionDenied(org.springframework.test.web.servlet.ResultActions actions)
            throws Exception {
        try {
            actions.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.code").value(not(200)));
        } catch (ServletException | AssertionError ex) {
            Throwable cause = ex instanceof ServletException servlet ? servlet.getCause() : ex;
            assertInstanceOf(NotPermissionException.class, cause,
                    "无专用权限码必须被 Sa-Token 拒绝: " + cause);
        }
    }
}
