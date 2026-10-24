package com.jjx.inventory;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.jjx.inventory.controller.InventoryMaterialController;
import com.jjx.inventory.master.MaterialMasterExcelService;
import com.jjx.inventory.classify.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaterialImport1AControllerTest {
    @Test void listImportUsesUnifiedServiceAndKeepsRowAndColumnErrorResponse() throws Exception {
        var excel=mock(MaterialMasterExcelService.class);
        when(excel.importExcel(any(),isNull(),eq(false),eq("codex"),eq(false),eq(true)))
                .thenReturn(new MaterialMasterExcelService.ImportResult(false,0,List.of("第3行：缺【厚度(mm)】列或值")));
        try(var token=mockStatic(StpUtil.class)) {
            token.when(StpUtil::getSession).thenReturn(new SaSession("codex"));
            var mvc=MockMvcBuilders.standaloneSetup(new InventoryMaterialController(null,null,null,null,null,excel)).build();
            mvc.perform(multipart("/inventory/material/import").file(new MockMultipartFile("file","x.xlsx","application/octet-stream",new byte[]{1})).param("autoClassify","true"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.successCount").value(0))
                    .andExpect(jsonPath("$.data.failCount").value(1)).andExpect(jsonPath("$.data.failDetails[0].rowIndex").value(3))
                    .andExpect(jsonPath("$.data.failDetails[0].column").value("厚度(mm)"));
        }
        verify(excel).importExcel(any(),isNull(),eq(false),eq("codex"),eq(false),eq(true));
    }
    @Test void listTemplateDownloadsUnifiedWorkbook() throws Exception {
        var excel=mock(MaterialMasterExcelService.class);when(excel.template(null)).thenReturn(new byte[]{1,2,3});
        var mvc=MockMvcBuilders.standaloneSetup(new InventoryMaterialController(null,null,null,null,null,excel)).build();
        mvc.perform(post("/inventory/material/importTemplate")).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1,2,3}));
        verify(excel).template(null);
    }
    @Test void applyRulesWithoutConfirmParameterUsesDryRun() throws Exception {
        var service=mock(MaterialClassifyService.class);
        when(service.applyRules(false,"codex")).thenReturn(new MaterialClassifyService.ApplyResult(true,List.of(),List.of()));
        try(var token=mockStatic(StpUtil.class)) {
            token.when(StpUtil::getSession).thenReturn(new SaSession("codex"));
            var mvc=MockMvcBuilders.standaloneSetup(new MaterialClassifyController(service)).build();
            mvc.perform(post("/inventory/material/classify/apply-rules")).andExpect(status().isOk()).andExpect(jsonPath("$.data.dryRun").value(true));
        }
        verify(service).applyRules(false,"codex");verifyNoMoreInteractions(service);
    }
}
