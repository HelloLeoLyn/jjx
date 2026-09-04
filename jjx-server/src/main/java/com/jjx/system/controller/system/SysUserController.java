package com.jjx.system.controller.system;

import com.jjx.common.constant.LogActions;
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
import io.swagger.v3.oas.annotations.Operation;
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

    private final com.jjx.system.mapper.SysDeptMapper deptMapper;

    /**
     * 获取用户列表
     * （DEV-1028 回归修复：DEV-1014 加 export 时误删了本接口，恢复）
     */
    @GetMapping("/list")
    public Result<PageResult<SysUserVO>> list(SysUser user) {
        PageResult<SysUserVO> result = userService.selectUserVOList(user, getPageNum(), getPageSize());
        return Result.success(result);
    }

    /**
     * 导出用户列表Excel（DEV-1014）
     * 与 /list 同权限口径：登录可见即可导出，不额外加权限点
     */
    @Operation(summary = "导出用户列表Excel")
    @GetMapping("/export")
    public void export(SysUser user, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        java.util.List<SysUser> users = userService.selectAllUserList(user);
        java.util.Map<Long, String> deptMap = new java.util.HashMap<>();
        for (com.jjx.system.domain.entity.SysDept d : deptMapper.selectList(null)) {
            deptMap.put(d.getId(), d.getDeptName());
        }
        byte[] bytes = buildUserExcel(users, deptMap);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename="
                + java.net.URLEncoder.encode("用户列表.xlsx", java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().write(bytes);
    }

    private byte[] buildUserExcel(java.util.List<SysUser> users, java.util.Map<Long, String> deptMap) {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("用户列表");
            String[] headers = {"用户ID", "用户名", "昵称", "部门", "邮箱", "手机", "性别", "状态", "创建时间", "备注"};
            org.apache.poi.ss.usermodel.Row head = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                head.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (SysUser u : users) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(u.getUserId() == null ? 0 : u.getUserId().doubleValue());
                row.createCell(1).setCellValue(safe(u.getUserName()));
                row.createCell(2).setCellValue(safe(u.getNickName()));
                row.createCell(3).setCellValue(u.getDeptId() != null && deptMap.containsKey(u.getDeptId())
                        ? deptMap.get(u.getDeptId()) : "");
                row.createCell(4).setCellValue(safe(u.getEmail()));
                row.createCell(5).setCellValue(safe(u.getPhone()));
                row.createCell(6).setCellValue(sexText(u.getSex()));
                row.createCell(7).setCellValue(u.getStatus() != null && u.getStatus() == 0 ? "正常" : "停用");
                row.createCell(8).setCellValue(u.getCreateTime() == null ? "" : fmt.format(u.getCreateTime()));
                row.createCell(9).setCellValue(safe(u.getRemark()));
            }
            int[] widths = {10, 18, 18, 15, 22, 15, 8, 8, 20, 30};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            wb.write(os);
            return os.toByteArray();
        } catch (java.io.IOException e) {
            throw new RuntimeException("生成用户导出Excel失败", e);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String sexText(String sex) {
        if (sex == null) return "";
        switch (sex) {
            case "0": return "男";
            case "1": return "女";
            case "2": return "未知";
            default: return sex;
        }
    }

    /**
     * 获取销售负责人列表（2026-08-11 按 role_key 前缀 sales 匹配，不依赖角色ID）
     */
    @GetMapping("/sales-persons")
    @SaCheckPermission("sales:order:view")
    public Result<List<SysUser>> salesPersons() {
        return Result.success(userService.selectUsersByRoleKeyPrefix("sales"));
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
    @Log(module = "用户管理", businessType = BusinessType.INSERT, action = LogActions.USER_CREATE)
    @SaCheckPermission("system:user:add")
    public Result<Void> add(@Validated @RequestBody SysUserDTO userDTO) {
        return toAjax(userService.insertUser(userDTO));
    }

    /**
     * 修改用户
     */
    @PutMapping
    @Log(module = "用户管理", businessType = BusinessType.UPDATE, action = LogActions.USER_EDIT)
    @SaCheckPermission("system:user:edit")
    public Result<Void> edit(@Validated @RequestBody SysUserDTO userDTO) {

        return toAjax(userService.updateUser(userDTO));
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{userIds}")
    @Log(module = "用户管理", businessType = BusinessType.DELETE, action = LogActions.USER_DELETE)
    @SaCheckPermission("system:user:delete")
    public Result<Void> remove(@PathVariable List<Long> userIds) {
        return toAjax(userService.deleteUserByIds(userIds));
    }

    /**
     * 重置密码
     */
    @PutMapping("/resetPwd")
    @Log(module = "用户管理", businessType = BusinessType.UPDATE, action = LogActions.USER_RESET_PWD)
    @SaCheckPermission("system:user:resetPwd")
    public Result<Void> resetPwd(@Validated @RequestBody SysUserStatusDTO statusDTO) {
        return toAjax(userService.resetPwd(statusDTO));
    }

    /**
     * 状态修改
     */
    @PutMapping("/changeStatus")
    @Log(module = "用户管理", businessType = BusinessType.UPDATE, action = LogActions.USER_STATUS)
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
    @Log(module = "用户管理", businessType = BusinessType.UPDATE, action = LogActions.USER_AUTH_ROLE)
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
    @Log(module = "用户管理", businessType = BusinessType.UPDATE, action = LogActions.USER_PROFILE)
    public Result<Void> profile(@Validated @RequestBody SysUserProfileDTO profileDTO) {
        profileDTO.setUserId(getUserId());
        return toAjax(userService.updateUserProfile(profileDTO));
    }

    /**
     * 重置密码
     */
    @PutMapping("/profile/updatePwd")
    @Log(module = "用户管理", businessType = BusinessType.UPDATE, action = LogActions.USER_UPDATE_PWD)
    public Result<Void> updatePwd(@Validated @RequestBody ResetPasswordDTO dto) {

        return toAjax(userService.resetUserPwd(dto));
    }

    /**
     * 修改头像
     */
    @PostMapping("/profile/avatar")
    @Log(module = "用户管理", businessType = BusinessType.UPDATE, action = LogActions.USER_AVATAR)
    public Result<Void> avatar(String avatar) {
        return toAjax(userService.updateUserAvatar(getUsername(), avatar));
    }


}
