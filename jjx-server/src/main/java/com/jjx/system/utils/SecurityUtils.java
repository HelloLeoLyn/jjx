package com.jjx.system.utils;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.jjx.system.domain.vo.LoginVO;

import java.util.List;
import java.util.Set;

public class SecurityUtils {
    private SecurityUtils() {
        /* This utility class should not be instantiated */
    }


    private static final ThreadLocal<LoginVO> USER_HOLDER = new ThreadLocal<>();

    public static void setLoginUser(LoginVO user) {
        USER_HOLDER.set(user);
    }

    public static LoginVO getLoginUser() {
        SaSession session = StpUtil.getSession();
        Object loginVO = session.get("loginVO");
        if(loginVO==null){
            return null;
        }
        if(loginVO instanceof LoginVO){
            return (LoginVO) loginVO;
        }else{
            String jsonStr = JSON.toJSONString(loginVO);
            return JSON.parseObject(jsonStr, LoginVO.class);
        }
    }

    public static void clear() {
        USER_HOLDER.remove();
    }

    public static Long getUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    public static String getUsername() {
        LoginVO user = getLoginUser();
        return user != null && user.getUserInfo() != null ? user.getUserInfo().getUserName() : null;
    }

    public static String getRealName() {
        LoginVO user = getLoginUser();
        return user != null && user.getUserInfo() != null ? user.getUserInfo().getRealName() : null;
    }

    public static Long getTenantId() {
        return 1L;
    }

    public static Boolean hasPermission(String permission){
        LoginVO user = getLoginUser();
        Set<String> permissions = user.getPermissions();
        if(permissions.contains("*:*:*")){
            return true;
        }
        return permissions.contains(permission);
    }

    /**
     * 当前用户是否持有指定 role_key（来自 SaToken StpInterface，实时查询 sys_role_role_key）
     */
    public static boolean hasRole(String roleKey) {
        try {
            List<String> roles = StpUtil.getRoleList();
            return roles != null && roles.contains(roleKey);
        } catch (Exception e) {
            return false;
        }
    }

}
