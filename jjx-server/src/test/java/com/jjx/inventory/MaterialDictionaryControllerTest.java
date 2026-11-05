package com.jjx.inventory;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.master.MaterialMasterController;
import com.jjx.inventory.master.MaterialMasterController.DictionaryAction;
import com.jjx.inventory.master.MaterialMasterController.DictionaryMutation;
import com.jjx.system.config.GlobalExceptionHandler;
import com.jjx.system.domain.converter.SysDictConverter;
import com.jjx.system.domain.converter.SysDictItemConverter;
import com.jjx.system.domain.dto.SysDictItemDTO;
import com.jjx.system.domain.entity.SysDictItem;
import com.jjx.system.mapper.SysDictItemMapper;
import com.jjx.system.mapper.SysDictMapper;
import com.jjx.system.service.LogSaveService;
import com.jjx.system.service.SysDictService;
import org.junit.jupiter.api.Test;
import jakarta.servlet.ServletException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaterialDictionaryControllerTest {
    private static final String BASE = "/inventory/material/master/dictionaries/";
    private final SysDictService dictionaries = mock(SysDictService.class);
    private final MaterialMasterController controller = new MaterialMasterController(null, null, null, null, null, dictionaries);

    @Test void modulePermissionsAndSuperAdminCanReadAllFourAndWriteWithoutSystemPermissions() throws Exception {
        StpLogic original = StpUtil.getStpLogic();
        try {
            for (var permissions : List.of(List.of("*"), List.of("inventory:material:dict:view", "inventory:material:dict:edit"))) {
                StpLogic logic = spy(new StpLogic("login"));
                doReturn(42L).when(logic).getLoginId();
                doReturn(permissions).when(logic).getPermissionList(any());
                StpUtil.setStpLogic(logic);
                var mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new SaInterceptor()).build();
                when(dictionaries.insertDictItem(any())).thenReturn(1);
                for (String code : List.of("brand", "color", "form", "unit")) {
                    var row = row(code);
                    row.setIsActive(0);
                    when(dictionaries.selectItemsByDictCode(code)).thenReturn(List.of(row));
                    mvc.perform(get(BASE + code + "/items")).andExpect(status().isOk())
                            .andExpect(jsonPath("$.data[0].isActive").value(0));
                    mvc.perform(post(BASE + code + "/items").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"CREATE\",\"item\":{\"dictCode\":\"" + code
                                    + "\",\"itemKey\":\"CHL\",\"itemValue\":\"CHL\",\"isActive\":1}}"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
                }
            }
        } finally { StpUtil.setStpLogic(original); }
    }

    @Test void systemOnlyPermissionCannotAccessAndViewOnlyCannotWrite() throws Exception {
        StpLogic original = StpUtil.getStpLogic();
        try {
            StpLogic logic = spy(new StpLogic("login"));
            doReturn(42L).when(logic).getLoginId();
            doReturn(List.of("system:dict:view", "system:dict:add")).when(logic).getPermissionList(any());
            StpUtil.setStpLogic(logic);
            var mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new SaInterceptor()).build();
            var readDenied = assertThrows(ServletException.class, () -> mvc.perform(get(BASE + "brand/items")));
            assertEquals("inventory:material:dict:view",
                    assertInstanceOf(NotPermissionException.class, readDenied.getCause()).getPermission());
            doReturn(List.of("inventory:material:dict:view")).when(logic).getPermissionList(any());
            var writeDenied = assertThrows(ServletException.class, () -> mvc.perform(post(BASE + "brand/items")
                    .contentType(MediaType.APPLICATION_JSON).content("{}")));
            assertEquals("inventory:material:dict:edit",
                    assertInstanceOf(NotPermissionException.class, writeDenied.getCause()).getPermission());
            verifyNoInteractions(dictionaries);
        } finally { StpUtil.setStpLogic(original); }
    }

    @Test void rejectsOtherDictionariesAndCrossDictionaryIdsWithoutWriting() {
        for (String code : List.of("material_meta", "order_status", "BRAND", "other")) {
            assertTrue(assertThrows(BusinessException.class, () -> controller.dictionaryItems(code)).getMessage().contains("仅允许"));
            assertThrows(BusinessException.class, () -> controller.mutateDictionaryItem(code,
                    new DictionaryMutation(DictionaryAction.DELETE, null, 1L, null)));
        }
        verifyNoInteractions(dictionaries);
        when(dictionaries.selectItemsByDictCode("brand")).thenReturn(List.of());
        for (var action : List.of(DictionaryAction.UPDATE, DictionaryAction.DELETE, DictionaryAction.STATUS)) {
            var item = dto(); item.setItemId(999L);
            assertTrue(assertThrows(BusinessException.class, () -> controller.mutateDictionaryItem("brand",
                    new DictionaryMutation(action, item, 999L, 1))).getMessage().contains("不属于"));
        }
        verify(dictionaries, never()).updateDictItem(any());
        verify(dictionaries, never()).deleteDictItemByIds(any());
        verify(dictionaries, never()).changeDictItemStatus(any(), any());
    }

    @Test void validatesHttpBodyAndReturnsExplicitInvalidDictionaryError() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(mock(LogSaveService.class))).build();
        mvc.perform(get(BASE + "order_status/items")).andExpect(jsonPath("$.msg").value("物料字典仅允许 brand/color/form/unit"));
        mvc.perform(post(BASE + "brand/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CREATE\",\"item\":{\"dictCode\":\"brand\",\"isActive\":1}}"))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("字典键不能为空")));
        mvc.perform(post(BASE + "brand/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
        verifyNoInteractions(dictionaries);
    }

    @Test void writesOnlyOwnedItemsAndRejectsDictionaryMismatchAndKeyChanges() {
        var item = dto();
        item.setDictCode("order_status");
        assertThrows(BusinessException.class, () -> controller.mutateDictionaryItem("brand", new DictionaryMutation(DictionaryAction.CREATE, item, null, null)));
        item.setDictCode("brand"); item.setItemId(5L);
        assertThrows(BusinessException.class, () -> controller.mutateDictionaryItem("brand", new DictionaryMutation(DictionaryAction.CREATE, item, null, null)));
        var existing = row("brand"); existing.setItemId(5L);
        when(dictionaries.selectItemsByDictCode("brand")).thenReturn(List.of(existing));
        when(dictionaries.updateDictItem(item)).thenReturn(1);
        assertTrue(controller.mutateDictionaryItem("brand", new DictionaryMutation(DictionaryAction.UPDATE, item, null, null)).isSuccess());
        controller.mutateDictionaryItem("brand", new DictionaryMutation(DictionaryAction.STATUS, null, 5L, 0));
        verify(dictionaries).changeDictItemStatus(5L, 0);
        controller.mutateDictionaryItem("brand", new DictionaryMutation(DictionaryAction.DELETE, null, 5L, null));
        verify(dictionaries).deleteDictItemByIds(new Long[]{5L});
        item.setItemKey("RENAMED");
        assertThrows(BusinessException.class, () -> controller.mutateDictionaryItem("brand", new DictionaryMutation(DictionaryAction.UPDATE, item, null, null)));
        assertThrows(BusinessException.class, () -> controller.mutateDictionaryItem("brand", new DictionaryMutation(DictionaryAction.STATUS, null, 5L, 99)));
    }

    @Test void duplicateCHLUsesExistingServiceUniquenessCheckAndNeverInsertsSecondItem() {
        var types = mock(SysDictMapper.class);
        var items = mock(SysDictItemMapper.class);
        var converter = mock(SysDictItemConverter.class);
        var redis = mock(RedisTemplate.class);
        var service = new SysDictService(types, items, mock(SysDictConverter.class), converter, redis);
        var actualController = new MaterialMasterController(null, null, null, null, null, service);
        when(types.selectCount(any())).thenReturn(1L);
        when(items.selectCount(any())).thenReturn(0L, 1L);
        when(converter.toEntity(any())).thenReturn(row("brand"));
        when(items.insert(any(SysDictItem.class))).thenReturn(1);
        var request = new DictionaryMutation(DictionaryAction.CREATE, dto(), null, null);
        assertTrue(actualController.mutateDictionaryItem("brand", request).isSuccess());
        assertEquals("字典项键已存在：CHL", assertThrows(BusinessException.class,
                () -> actualController.mutateDictionaryItem("brand", request)).getMessage());
        verify(items, times(1)).insert(any(SysDictItem.class));
        verify(redis).delete("system:dict:brand");
    }

    private static SysDictItemDTO dto() {
        var item = new SysDictItemDTO();
        item.setDictCode("brand"); item.setItemKey("CHL"); item.setItemValue("CHL"); item.setIsActive(1);
        return item;
    }
    private static SysDictItem row(String code) {
        var item = new SysDictItem();
        item.setDictCode(code); item.setItemId(5L); item.setItemKey("CHL"); item.setItemValue("CHL"); item.setIsActive(1);
        return item;
    }
}
