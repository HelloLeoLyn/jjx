package com.jjx.engineering.controller;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.jjx.product.domain.vo.EngineeringBomEditVO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.service.IEngineeringBomService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BomEditWorkflowPermissionTest {
    @Test void editOnlyAccountCanSaveButCannotCallApproval() throws Exception {
        StpLogic original = StpUtil.getStpLogic();
        StpLogic logic = spy(new StpLogic("login"));
        doReturn(42L).when(logic).getLoginId();
        doReturn(List.of("engineering:bom:edit")).when(logic).getPermissionList(any());
        StpUtil.setStpLogic(logic);
        try {
            var service = mock(IEngineeringBomService.class);
            var saved = new EngineeringBomEditVO();
            saved.setSuccess(true);
            saved.setBizStatus(ProductEnums.BomStatus.DRAFT.getLabel());
            when(service.updateBomWithDetail(any())).thenReturn(saved);
            var mvc = MockMvcBuilders.standaloneSetup(new BomController(service))
                    .addInterceptors(new SaInterceptor()).build();
            mvc.perform(put("/engineering/bom").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"bomId":1,"productId":1,"bomCode":"EDIT","bomName":"普通保存",
                             "items":[{"materialId":11,"baseQty":1,"moduleQty":1,"unit":"PCS"}]}
                            """))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
            verify(service).updateBomWithDetail(any());
            clearInvocations(service);
            mvc.perform(put("/engineering/bom/approve/1").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bomId\":1}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
            verifyNoInteractions(service);
        } finally {
            StpUtil.setStpLogic(original);
        }
    }
}
