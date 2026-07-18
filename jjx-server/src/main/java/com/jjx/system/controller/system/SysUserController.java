package com.jjx.system.controller.system;

import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import com.jjx.system.domain.dto.ResetPasswordDTO;
import com.jjx.system.domain.dto.SysUserDTO;
import com.jjx.system.domain.dto.SysUserProfileDTO;
import com.jjx.system.domain.dto.SysUserStatusDTO;
import com.jjx.system.domain.dto.UserRoleQueryDTO;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.domain.vo.SysUserVO;
import com.jjx.system.domain.vo.UserRoleVO;
import com.jjx.system.service.ISysUserRoleService;
import com.jjx.system.service.ISysUserService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户信息
 */
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController extends BaseController {

    private final ISysUserService userService;

    private final ISysUserRoleService userRoleService;

    /**
     * 获取用户列表
     */
    @GetMapping("/list")
    public Result<PageResult<SysUserVO>> list(SysUser user) {
        PageResult<SysUserVO> result = userService.selectUserVOList(user, getPageNum(), getPageSize());
        return Result.success(result);
    }

    /**
     * 根据用户编号获取详细信息
     */
    @GetMapping(value = "/{userId}")
    public Result<SysUserVO> getInfo(@PathVariable Long userId) {
        userService.checkUserDataScope(userId);
        return Result.success(userService.selectUserById(userId));
    }

    /**
     * 新增用户
     */
    @PostMapping
    @Log(module = "用户管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("system:user:add")
    public Result<Void> add(@Validated @RequestBody SysUserDTO userDTO) {
        return toAjax(userService.insertUser(userDTO));
    }

    /**
     * 修改用户
     */
    @PutMapping
    @Log(module = "用户管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:user:edit")
    public Result<Void> edit(@Validated @RequestBody SysUserDTO userDTO) {

        return toAjax(userService.updateUser(userDTO));
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{userIds}")
    @Log(module = "用户管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("system:user:delete")
    public Result<Void> remove(@PathVariable List<Long> userIds) {
        return toAjax(userService.deleteUserByIds(userIds));
    }

    /**
     * 重置密码
     */
    @PutMapping("/resetPwd")
    @Log(module = "用户管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:user:resetPwd")
    public Result<Void> resetPwd(@Validated @RequestBody SysUserStatusDTO statusDTO) {
        return toAjax(userService.resetPwd(statusDTO));
    }

    /**
     * 状态修改
     */
    @PutMapping("/changeStatus")
    @Log(module = "用户管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:user:edit")
    public Result<Void> changeStatus(@Validated @RequestBody SysUserStatusDTO statusDTO) {
        return toAjax(userService.updateUserStatus(statusDTO));
    }

    /**
     * 根据用户编号获取授权角色
     */
    @GetMapping("/role")
    public Result<PageResult<UserRoleVO>> authRole(UserRoleQueryDTO query) {
        PageResult<UserRoleVO> result = userRoleService.selectUserRolePage(query);
        return Result.success(result);
    }

    /**
     * 用户授权角色
     */
    @PutMapping("/authRole")
    @Log(module = "用户管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:user:edit")
    public Result<Void> insertAuthRole(Long userId, Long[] roleIds) {
        userService.checkUserDataScope(userId);
        userService.insertUserAuth(userId, roleIds);
        return Result.success();
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/current")
    public Result<SysUserVO> getCurrentInfo() {
        return Result.success(userService.selectUserById(getUserId()));
    }

    /**
     * 修改用户个人信息
     */
    @PutMapping("/profile")
    @Log(module = "用户管理", businessType = BusinessType.UPDATE)
    public Result<Void> profile(@Validated @RequestBody SysUserProfileDTO profileDTO) {
        profileDTO.setUserId(getUserId());
        return toAjax(userService.updateUserProfile(profileDTO));
    }

    /**
     * 重置密码
     */
    @PutMapping("/profile/updatePwd")
    @Log(module = "用户管理", businessType = BusinessType.UPDATE)
    public Result<Void> updatePwd(@Validated @RequestBody ResetPasswordDTO dto) {

        return toAjax(userService.resetUserPwd(dto));
    }

    /**
     * 修改头像
     */
    @PostMapping("/profile/avatar")
    @Log(module = "用户管理", businessType = BusinessType.UPDATE)
    public Result<Void> avatar(String avatar) {
        return toAjax(userService.updateUserAvatar(getUsername(), avatar));
    }


}
