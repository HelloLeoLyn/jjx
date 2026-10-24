package com.jjx.inventory;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.jjx.inventory.controller.InventoryMaterialController;
import com.jjx.inventory.dto.vo.MaterialCategoryOptionVO;
import com.jjx.inventory.service.InventoryMaterialService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaterialCategoryOptionsPermissionTest {
    @Test void loggedInUserWithoutCategoryPermissionCanReadOptions() throws Exception {
        StpLogic original = StpUtil.getStpLogic();
        StpLogic logic = spy(new StpLogic("login"));
        doReturn(42L).when(logic).getLoginId();
        doReturn(List.of()).when(logic).getPermissionList(any());
        StpUtil.setStpLogic(logic);
        try {
            var service = mock(InventoryMaterialService.class);
            var category = new MaterialCategoryOptionVO();
            category.setCategoryId(10L); category.setCategoryCode("OTHER-R");
            category.setCategoryName("其他（原材料）"); category.setRootType("R"); category.setCategoryRootName("原材料");
            when(service.getCategoryOptions()).thenReturn(List.of(category));
            var mvc = MockMvcBuilders.standaloneSetup(new InventoryMaterialController(service, null, null, null, null))
                    .addInterceptors(new SaInterceptor(handle -> StpUtil.checkLogin())).build();
            mvc.perform(get("/inventory/material/category-options"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].categoryId").value(10))
                    .andExpect(jsonPath("$.data[0].categoryName").value("其他（原材料）"))
                    .andExpect(jsonPath("$.data[0].rootType").value("R"));
            verify(logic, atLeastOnce()).checkLogin();
            verify(logic, never()).getPermissionList(any());
        } finally {
            StpUtil.setStpLogic(original);
        }
    }
}
