package com.jjx.system.controller.auth;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.core.result.Result;
import com.jjx.system.annotation.LoginLog;
import com.jjx.system.domain.dto.LoginDTO;
import com.jjx.system.domain.dto.SmsLoginDTO;
import com.jjx.system.domain.vo.LoginVO;
import com.jjx.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证管理控制器
 * RESTful 风格 API
 */
@Tag(name = "认证管理", description = "用户登录、登出、Token管理接口")
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ==================== 会话管理 ====================

    /**
     * 账号密码登录
     */
    @PostMapping("/auth")
    @Operation(summary = "账号密码登录")
    @LoginLog(loginType = "PASSWORD")
    public Result<LoginVO> createSessionByPassword(@RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    /**
     * 短信验证码登录
     */
    @PostMapping("/sms")
    @Operation(summary = "短信验证码登录")
    @LoginLog(loginType = "SMS", recordOnSuccess = true, recordOnFailure = true)
    public Result<LoginVO> createSessionBySms(@RequestBody SmsLoginDTO dto) {
        return Result.success(authService.smsLogin(dto));
    }

    /**
     * 登出（删除当前会话）
     */
    @DeleteMapping("/current/out")
    @Operation(summary = "用户登出")
    public Result<Void> deleteCurrentSession() {
        StpUtil.logout();
        return Result.success();
    }

    /**
     * 获取当前会话信息
     */
    @GetMapping("/current")
    @Operation(summary = "获取当前登录用户信息")
    public Result<Object> getCurrentSession() {
        return Result.success(StpUtil.getSession().get("loginVO"));
    }

    /**
     * 获取当前会话状态
     */
    @GetMapping("/current/status")
    @Operation(summary = "获取当前登录状态")
    public Result<Boolean> getSessionStatus() {
        return Result.success(StpUtil.isLogin());
    }

    /**
     * 刷新当前会话 Token
     */
    @PutMapping("/current/token")
    @Operation(summary = "刷新Token")
    public Result<String> refreshSessionToken() {
        return Result.success(StpUtil.getTokenValue());
    }

    /**
     * 获取当前会话 Token 详情
     */
    @GetMapping("/current/token-info")
    @Operation(summary = "获取Token信息")
    public Result<SaTokenInfo> getSessionTokenInfo() {
        return Result.success(StpUtil.getTokenInfo());
    }

    // ==================== 管理员操作 ====================

    /**
     * 踢人下线（管理员功能）
     */
    @DeleteMapping("/admin/{userId}")
    @Operation(summary = "踢人下线")
    public Result<Void> deleteSessionByUserId(@PathVariable Long userId) {
        StpUtil.kickout(userId);
        return Result.success();
    }

    /**
     * 顶号下线（管理员功能）
     */
    @PutMapping("/admin/{userId}/replace")
    @Operation(summary = "顶号下线")
    public Result<Void> replaceSessionByUserId(@PathVariable Long userId, @RequestParam(defaultValue = "PC") String device) {
        StpUtil.replaced(userId, device);
        return Result.success();
    }

    /**
     * 用户权限（管理员功能）
     */
    @GetMapping("/permission")
    @Operation(summary = "用户权限")
    public Result<List<String>> permission() {
        List<String> permissionList = StpUtil.getPermissionList();
        return Result.success(permissionList);
    }
}