package com.jjx.system.service.impl;

import com.jjx.system.domain.entity.SysRole;
import com.jjx.system.service.IPermissionService;
import com.jjx.system.service.ISysMenuService;
import com.jjx.system.service.ISysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements IPermissionService {

    private final ISysMenuService sysMenuService;
    private final ISysRoleService sysRoleService;

    @Override
    public List<String> getPermissionsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }

        // 获取用户的菜单权限
        Set<String> menuPerms = sysMenuService.selectMenuPermsByUserId(userId);

        // 获取用户的角色权限
        Set<String> rolePerms = sysRoleService.selectRolePermissionByUserId(userId);

        // 合并权限并去重
        Set<String> allPerms = menuPerms;
        allPerms.addAll(rolePerms);

        // 过滤掉空权限
        return allPerms.stream()
                .filter(perm -> perm != null && !perm.trim().isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getRolesByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }

        // 获取用户的角色列表
        List<SysRole> roles = sysRoleService.selectRolesByUserId(userId);

        // 提取角色key
        return roles.stream()
                .map(SysRole::getRoleKey)
                .filter(roleKey -> roleKey != null && !roleKey.trim().isEmpty())
                .collect(Collectors.toList());
    }
}
