package com.jjx.production.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.production.service.ProductionRoleResolver;
import com.jjx.system.domain.entity.SysRole;
import com.jjx.system.event.SysConfigChangedEvent;
import com.jjx.system.mapper.SysRoleMapper;
import com.jjx.system.service.SysConfigService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionRoleResolverImpl implements ProductionRoleResolver {

    static final String CONFIG_GROUP = "production_config";
    static final String PRODUCTION_ADMIN_KEY = "production_admin";
    static final String GLOBAL_SCOPE_KEY = "production_global_scope";
    static final String DEFAULT_ROLE_KEY = "production:all";
    private static final long CACHE_TTL_MILLIS = 30_000L;

    private final SysConfigService configService;
    private final SysRoleMapper roleMapper;
    private volatile RoleConfigSnapshot cachedSnapshot;

    @Override
    public boolean isProductionAdmin() {
        return hasLogicalRole(PRODUCTION_ADMIN_KEY);
    }

    @Override
    public boolean isGlobalProductionScope() {
        return hasLogicalRole(GLOBAL_SCOPE_KEY);
    }

    @EventListener
    public void onConfigChanged(SysConfigChangedEvent event) {
        if (CONFIG_GROUP.equals(event.getConfigGroup())) {
            cachedSnapshot = null;
        }
    }

    private boolean hasLogicalRole(String configKey) {
        try {
            if (hasWildcardPermission()) {
                return true;
            }
        } catch (Exception e) {
            log.warn("读取超级管理员权限失败，继续按角色解析生产身份: {}", e.getMessage());
        }
        try {
            List<String> userRoles = currentUserRoles();
            if (userRoles != null && userRoles.contains("admin")) {
                return true;
            }
            if (userRoles == null) {
                userRoles = Collections.emptyList();
            }
            return !Collections.disjoint(userRoles, rolesFor(configKey));
        } catch (Exception e) {
            log.warn("解析生产逻辑身份失败，按内置默认角色 {} 兜底: {}", DEFAULT_ROLE_KEY, e.getMessage());
            try {
                List<String> roles = currentUserRoles();
                return roles != null && roles.contains(DEFAULT_ROLE_KEY);
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    protected List<String> currentUserRoles() {
        return StpUtil.getRoleList();
    }

    protected boolean hasWildcardPermission() {
        return SecurityUtils.hasPermission("*:*:*");
    }

    private Set<String> rolesFor(String configKey) {
        RoleConfigSnapshot snapshot = cachedSnapshot;
        long now = System.currentTimeMillis();
        if (snapshot == null || now - snapshot.loadedAt >= CACHE_TTL_MILLIS) {
            snapshot = reload(now);
        }
        return PRODUCTION_ADMIN_KEY.equals(configKey) ? snapshot.productionAdmins : snapshot.globalScopes;
    }

    private synchronized RoleConfigSnapshot reload(long now) {
        RoleConfigSnapshot current = cachedSnapshot;
        if (current != null && now - current.loadedAt < CACHE_TTL_MILLIS) {
            return current;
        }
        try {
            Map<String, String> configs = configService.listActiveMapByGroup(CONFIG_GROUP);
            Set<String> configuredAdmins = parse(configs == null ? null : configs.get(PRODUCTION_ADMIN_KEY));
            Set<String> configuredScopes = parse(configs == null ? null : configs.get(GLOBAL_SCOPE_KEY));
            Set<String> requested = new HashSet<>(configuredAdmins);
            requested.addAll(configuredScopes);
            Set<String> existing = roleMapper.selectList(null).stream()
                    .map(SysRole::getRoleKey)
                    .filter(requested::contains)
                    .collect(Collectors.toSet());
            RoleConfigSnapshot loaded = new RoleConfigSnapshot(now,
                    validOrDefault(configuredAdmins, existing), validOrDefault(configuredScopes, existing));
            cachedSnapshot = loaded;
            return loaded;
        } catch (Exception e) {
            log.warn("读取生产角色配置失败，回落内置默认角色 {}: {}", DEFAULT_ROLE_KEY, e.getMessage());
            RoleConfigSnapshot fallback = new RoleConfigSnapshot(now, defaultRoles(), defaultRoles());
            cachedSnapshot = fallback;
            return fallback;
        }
    }

    private Set<String> parse(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private Set<String> validOrDefault(Set<String> configured, Set<String> existing) {
        Set<String> valid = configured.stream().filter(existing::contains).collect(Collectors.toSet());
        return valid.isEmpty() ? defaultRoles() : Collections.unmodifiableSet(valid);
    }

    private Set<String> defaultRoles() {
        return Collections.singleton(DEFAULT_ROLE_KEY);
    }

    private static final class RoleConfigSnapshot {
        private final long loadedAt;
        private final Set<String> productionAdmins;
        private final Set<String> globalScopes;

        private RoleConfigSnapshot(long loadedAt, Set<String> productionAdmins, Set<String> globalScopes) {
            this.loadedAt = loadedAt;
            this.productionAdmins = productionAdmins;
            this.globalScopes = globalScopes;
        }
    }
}
