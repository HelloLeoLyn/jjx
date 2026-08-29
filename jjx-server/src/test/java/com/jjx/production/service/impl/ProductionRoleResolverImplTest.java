package com.jjx.production.service.impl;

import com.jjx.system.domain.entity.SysConfig;
import com.jjx.system.domain.entity.SysRole;
import com.jjx.system.mapper.SysConfigMapper;
import com.jjx.system.mapper.SysRoleMapper;
import com.jjx.system.service.impl.SysRoleServiceImpl;
import com.jjx.system.service.SysConfigService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionRoleResolverImplTest {

    @Test
    void configuredRoleHeldByUserIsProductionAdmin() {
        assertResolved(Map.of("production_admin", "production:manager"),
                List.of("production:manager"), List.of("production:manager"), true);
    }

    @Test
    void userRoleOutsideConfiguredListIsNotProductionAdmin() {
        assertResolved(Map.of("production_admin", "production:manager"),
                List.of("production:worker"), List.of("production:manager", "production:worker"), false);
    }

    @Test
    void blankOrMissingConfigFallsBackToDefaultRole() {
        assertResolved(Map.of("production_admin", "   "),
                List.of("production:all"), List.of("production:all"), true);
        assertResolved(Map.of(), List.of("production:all"), List.of("production:all"), true);
    }

    @Test
    void allMissingConfiguredRolesFallBackWithoutThrowing() {
        assertResolved(Map.of("production_admin", "deleted:one, deleted:two"),
                List.of("production:all"), List.of("production:all"), true);
    }

    @Test
    void wildcardPermissionOrAdminRoleAlwaysPasses() {
        assertTrue(resolver(Map.of("production_admin", "production:manager"),
                List.of("production:manager"), List.of(), true).isProductionAdmin());
        assertTrue(resolver(Map.of("production_admin", "production:manager"),
                List.of("production:manager"), List.of("admin"), false).isProductionAdmin());
    }

    @Test
    void commaSeparatedRolesAreTrimmedAndDeletedEntriesIgnored() {
        assertResolved(Map.of("production_admin", " deleted:key, production:manager , production:lead "),
                List.of("production:lead"), List.of("production:manager", "production:lead"), true);
    }

    @Test
    void roleReferencedByProductionConfigCannotBeDeleted() {
        SysRole role = new SysRole();
        role.setRoleId(20L);
        role.setRoleKey("production:manager");
        SysRoleMapper roleMapper = proxy(SysRoleMapper.class, Map.of("selectById", role));
        SysConfig config = new SysConfig();
        config.setConfigKey("production_admin");
        config.setConfigValue("production:all, production:manager");
        SysConfigMapper configMapper = proxy(SysConfigMapper.class, Map.of("selectList", List.of(config)));
        SysConfigService configService = new SysConfigService(configMapper, event -> { });
        SysRoleServiceImpl roleService = new SysRoleServiceImpl(
                roleMapper, null, null, null, null, null, null, configService);

        assertFalse(roleService.deleteRoleById(20L));
    }

    private void assertResolved(Map<String, String> configs, List<String> userRoles,
                                List<String> existingRoles, boolean expected) {
        ProductionRoleResolverImpl resolver = resolver(configs, existingRoles, userRoles, false);
        if (expected) {
            assertTrue(resolver.isProductionAdmin());
        } else {
            assertFalse(resolver.isProductionAdmin());
        }
    }

    private ProductionRoleResolverImpl resolver(Map<String, String> values, List<String> existingRoleKeys) {
        return resolver(values, existingRoleKeys, List.of(), false);
    }

    private ProductionRoleResolverImpl resolver(Map<String, String> values, List<String> existingRoleKeys,
                                                List<String> userRoles, boolean superAdmin) {
        List<SysConfig> configs = new ArrayList<>();
        values.forEach((key, value) -> {
            SysConfig config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            configs.add(config);
        });
        SysConfigMapper configMapper = proxy(SysConfigMapper.class, Map.of("selectList", configs));
        SysConfigService configService = new SysConfigService(configMapper, event -> { });
        List<SysRole> roles = existingRoleKeys.stream().map(key -> {
            SysRole role = new SysRole();
            role.setRoleKey(key);
            return role;
        }).toList();
        SysRoleMapper roleMapper = proxy(SysRoleMapper.class, Map.of("selectList", roles));
        return new ProductionRoleResolverImpl(configService, roleMapper) {
            @Override
            protected List<String> currentUserRoles() {
                return userRoles;
            }

            @Override
            protected boolean hasWildcardPermission() {
                return superAdmin;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Map<String, Object> results) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> results.get(method.getName()));
    }
}
