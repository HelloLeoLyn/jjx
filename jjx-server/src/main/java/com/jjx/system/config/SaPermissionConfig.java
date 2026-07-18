package com.jjx.system.config;

import cn.dev33.satoken.stp.StpInterface;
import com.jjx.system.service.IPermissionService;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class SaPermissionConfig implements StpInterface {

    private final IPermissionService permissionService;  // 同模块的 Service，没问题

    public SaPermissionConfig(IPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = Long.parseLong(loginId.toString());
        return permissionService.getPermissionsByUserId(userId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 从数据库查询当前用户的角色列表
        Long userId = Long.parseLong(loginId.toString());
        return permissionService.getRolesByUserId(userId);
    }
}