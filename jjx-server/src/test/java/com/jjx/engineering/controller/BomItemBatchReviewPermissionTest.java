package com.jjx.engineering.controller;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.jjx.product.service.IEngineeringBomService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BomItemBatchReviewPermissionTest {
    @Test void accountWithoutApprovePermissionGets403BeforeServiceCall() throws Exception {
        StpLogic original = StpUtil.getStpLogic();
        StpLogic logic = spy(new StpLogic("login"));
        doReturn(42L).when(logic).getLoginId();
        doReturn(List.of()).when(logic).getPermissionList(any());
        StpUtil.setStpLogic(logic);
        try {
            var service = mock(IEngineeringBomService.class);
            var mvc = MockMvcBuilders.standaloneSetup(new BomController(service))
                .addInterceptors(new SaInterceptor()).build();
            mvc.perform(post("/engineering/bom/item/review/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bomId\":1,\"itemIds\":[11],\"action\":\"APPROVE\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
            verifyNoInteractions(service);
        } finally { StpUtil.setStpLogic(original); }
    }
}
