package com.jjx.engineering.controller;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.jjx.product.service.IEngineeringBomService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 整套送审/审核沿用既有权限点：edit 不能批准，approve 不能送审。 */
class BomReviewPackagePermissionTest {

    private void run(String url, List<String> permissions) throws Exception {
        StpLogic original = StpUtil.getStpLogic();
        StpLogic logic = spy(new StpLogic("login"));
        doReturn(42L).when(logic).getLoginId();
        doReturn(permissions).when(logic).getPermissionList(any());
        StpUtil.setStpLogic(logic);
        try {
            var service = mock(IEngineeringBomService.class);
            var mvc = MockMvcBuilders.standaloneSetup(new BomController(service))
                    .addInterceptors(new SaInterceptor()).build();
            mvc.perform(post(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"APPROVE\",\"products\":[]}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
            verifyNoInteractions(service);
        } finally {
            StpUtil.setStpLogic(original);
        }
    }

    @Test
    void approveEndpointRequiresApprovePermission() throws Exception {
        run("/engineering/bom/review/package/approve", List.of("engineering:bom:edit"));
    }

    @Test
    void submitEndpointRequiresEditPermission() throws Exception {
        run("/engineering/bom/review/package/submit", List.of("engineering:bom:approve"));
    }

    @Test
    void rejectEndpointRequiresRejectPermission() throws Exception {
        run("/engineering/bom/review/package/reject", List.of("engineering:bom:edit", "engineering:bom:approve"));
    }
}
