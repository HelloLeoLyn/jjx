package com.jjx.engineering.controller;

import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.service.EngineeringResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegacyPrintResourceRetirementTest {
    @Test
    void retiredScreenWritesAndImportHaveNoHandlerWhileLedgerAndDieAreReadable() throws Exception {
        var resources = mock(EngineeringResourceService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new EngineeringResourceController(resources)).build();
        String base = "/engineering/resources/";
        mvc.perform(post(base + "screen-frames")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post(base + "screen-plates")).andExpect(status().isNotFound());
        mvc.perform(post(base + "screen-frames/import")).andExpect(status().isNotFound());
        mvc.perform(post(base + "screen-frames/12/wash")).andExpect(status().isNotFound());
        verifyNoInteractions(resources);
        mvc.perform(get(base + "screen-frames")).andExpect(status().isOk());
        mvc.perform(get(base + "dies")).andExpect(status().isOk());
        verify(resources).pageFrames(null,null,null,null,null,null,null,null,null,null,null);
        verify(resources).pageDies(null,null,null,null);
    }

    @Test
    void legacyFilmAndScreenProductLinksCannotBeRewrittenWhileDieStillUsesItsExistingPath() {
        var jdbc = mock(JdbcTemplate.class);
        var resources = new EngineeringResourceService(jdbc);
        Map<String,Object> body = Map.of("productIds", List.of());
        for (String type : List.of("FILM","SCREEN_FRAME","SCREEN_PLATE")) {
            assertThrows(BusinessException.class, () -> resources.replaceProducts(type, 12L, body));
        }
        verifyNoInteractions(jdbc);
        resources.replaceProducts("DIE",12L,body);
        verify(jdbc).update("UPDATE engineering_resource_product_rel SET is_active=0 WHERE resource_type=? AND resource_id=?","DIE",12L);
    }
}
