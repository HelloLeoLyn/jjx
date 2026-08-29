package com.jjx.system.service.impl;

import com.jjx.system.domain.entity.SysMenu;
import com.jjx.system.domain.entity.SysRole;
import com.jjx.system.domain.entity.SysRoleMenu;
import com.jjx.system.mapper.SysMenuMapper;
import com.jjx.system.mapper.SysRoleMapper;
import com.jjx.system.mapper.SysRoleMenuMapper;
import com.jjx.system.mapper.SysConfigMapper;
import com.jjx.system.service.SysConfigService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SysRoleServiceImplTest {

    @Test
    void insertAuthMenusAddsParentAndGrandparent() {
        List<SysRoleMenu> inserted = new ArrayList<>();
        SysRoleServiceImpl service = service(
                List.of(menu(1L, 0L), menu(2L, 1L), menu(3L, 2L)), inserted);

        assertTrue(service.insertAuthMenus(15L, new Long[]{3L}));

        assertEquals(Set.of(1L, 2L, 3L), insertedMenuIds(inserted));
        assertEquals(3, inserted.size());
    }

    @Test
    void insertAuthMenusDoesNotDuplicateExistingParent() {
        List<SysRoleMenu> inserted = new ArrayList<>();
        SysRoleServiceImpl service = service(
                List.of(menu(1L, 0L), menu(2L, 1L), menu(3L, 2L)), inserted);

        assertTrue(service.insertAuthMenus(16L, new Long[]{3L, 2L}));

        assertEquals(Set.of(1L, 2L, 3L), insertedMenuIds(inserted));
        assertEquals(3, inserted.size());
    }

    @Test
    void insertAuthMenusIgnoresMissingParent() {
        List<SysRoleMenu> inserted = new ArrayList<>();
        SysRoleServiceImpl service = service(List.of(menu(3L, 99L)), inserted);

        assertDoesNotThrow(() -> service.insertAuthMenus(17L, new Long[]{3L}));

        assertEquals(Set.of(3L), insertedMenuIds(inserted));
        assertEquals(1, inserted.size());
    }

    @Test
    void deleteRoleReferencedByProductionConfigReturnsFalse() {
        SysRole role = new SysRole();
        role.setRoleId(20L);
        role.setRoleKey("production:manager");
        SysRoleMapper roleMapper = mapper(SysRoleMapper.class, Map.of("selectById", role));
        com.jjx.system.domain.entity.SysConfig config = new com.jjx.system.domain.entity.SysConfig();
        config.setConfigKey("production_admin");
        config.setConfigValue("production:all, production:manager");
        SysConfigMapper configMapper = mapper(SysConfigMapper.class, Map.of("selectList", List.of(config)));
        SysConfigService configService = new SysConfigService(configMapper, event -> { });
        SysRoleServiceImpl service = new SysRoleServiceImpl(
                roleMapper, null, null, null, null, null, null, configService);

        assertFalse(service.deleteRoleById(20L));
    }

    private SysRoleServiceImpl service(List<SysMenu> menus, List<SysRoleMenu> inserted) {
        SysRole role = new SysRole();
        role.setRoleId(15L);
        SysRoleMapper roleMapper = mapper(SysRoleMapper.class, Map.of("selectById", role));
        SysMenuMapper menuMapper = mapper(SysMenuMapper.class, Map.of("selectList", menus));
        SysRoleMenuMapper roleMenuMapper = (SysRoleMenuMapper) Proxy.newProxyInstance(
                SysRoleMenuMapper.class.getClassLoader(), new Class<?>[]{SysRoleMenuMapper.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("insert")) {
                        inserted.add((SysRoleMenu) args[0]);
                        return 1;
                    }
                    if (method.getName().equals("delete")) {
                        return 1;
                    }
                    return null;
                });
        return new SysRoleServiceImpl(roleMapper, null, null, roleMenuMapper, menuMapper, null, null, null);
    }

    private SysMenu menu(Long menuId, Long parentId) {
        SysMenu menu = new SysMenu();
        menu.setMenuId(menuId);
        menu.setParentId(parentId);
        return menu;
    }

    private Set<Long> insertedMenuIds(List<SysRoleMenu> inserted) {
        Set<Long> ids = new LinkedHashSet<>();
        for (SysRoleMenu roleMenu : inserted) {
            ids.add(roleMenu.getMenuId());
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private <T> T mapper(Class<T> type, Map<String, Object> results) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> results.get(method.getName()));
    }
}
