package com.jjx.system.controller.system;

import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import com.jjx.system.domain.dto.RoleUserQueryDTO;
import com.jjx.system.domain.dto.SysRoleDTO;
import com.jjx.system.domain.dto.SysRoleDataScopeDTO;
import com.jjx.system.domain.dto.SysRoleStatusDTO;
import com.jjx.system.domain.dto.SysUserRoleDTO;
import com.jjx.system.domain.entity.SysRole;
import com.jjx.system.domain.entity.SysUserRole;
import com.jjx.system.domain.vo.SysRoleVO;
import com.jjx.system.domain.vo.SysUserVO;
import com.jjx.system.service.ISysRoleService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色信息
 */
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController extends BaseController {

    private final ISysRoleService roleService;

    /**
     * 获取角色列表（全量）
     */
    @GetMapping("/list")
    public Result<List<SysRoleVO>> list(SysRole role) {
        List<SysRoleVO> list = roleService.selectRoleVOList(role);
        return Result.success(list);
    }

    /**
     * 获取角色列表（分页）
     */
    @GetMapping("/page")
    public Result<PageResult<SysRoleVO>> page(SysRole role) {
        PageResult<SysRoleVO> result = roleService.selectRoleVOList(role, getPageNum(), getPageSize());
        return Result.success(result);
    }
    /**
     * 根据角色编号获取详细信息
     */
    @GetMapping(value = "/{roleId}")
    public Result<SysRole> getInfo(@PathVariable("roleId") Long roleId) {
        roleService.checkRoleDataScope(roleId);
        return Result.success(roleService.selectRoleById(roleId));
    }

    /**
     * 新增角色
     */
    @PostMapping
    @Log(module = "角色管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("system:role:add")
    public Result<Void> add(@Validated @RequestBody SysRoleDTO roleDTO) {
        SysRole role = new SysRole();
        role.setRoleName(roleDTO.getRoleName());
        role.setRoleKey(roleDTO.getRoleKey());
        role.setRoleSort(roleDTO.getRoleSort());
        role.setStatus(roleDTO.getStatus());
        role.setRemark(roleDTO.getRemark());
        if (!roleService.checkRoleNameUnique(role)) {
            throw new BusinessException("新增角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (!roleService.checkRoleKeyUnique(role)) {
            throw new BusinessException("新增角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        return toAjax(roleService.insertRole(roleDTO));
    }

    /**
     * 修改保存角色
     */
    @PutMapping
    @Log(module = "角色管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:role:edit")
    public Result<Void> edit(@Validated @RequestBody SysRoleDTO roleDTO) {
        SysRole role = new SysRole();
        role.setRoleId(roleDTO.getRoleId());
        role.setRoleName(roleDTO.getRoleName());
        role.setRoleKey(roleDTO.getRoleKey());
        role.setRoleSort(roleDTO.getRoleSort());
        role.setStatus(roleDTO.getStatus());
        role.setRemark(roleDTO.getRemark());
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        if (!roleService.checkRoleNameUnique(role)) {
            throw new BusinessException("修改角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (!roleService.checkRoleKeyUnique(role)) {
            throw new BusinessException("修改角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        return toAjax(roleService.updateRole(roleDTO));
    }

    /**
     * 修改保存数据权限
     */
    @PutMapping("/dataScope")
    @Log(module = "角色管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:role:edit")
    public Result<Void> dataScope(@Validated @RequestBody SysRoleDataScopeDTO dataScopeDTO) {
        SysRole role = new SysRole();
        role.setRoleId(dataScopeDTO.getRoleId());
        role.setDataScope(dataScopeDTO.getDataScope());
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        return toAjax(roleService.authDataScope(role));
    }

    /**
     * 状态修改
     */
    @PutMapping("/changeStatus")
    @Log(module = "角色管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:role:edit")
    public Result<Void> changeStatus(@Validated @RequestBody SysRoleStatusDTO statusDTO) {
        SysRole role = new SysRole();
        role.setRoleId(statusDTO.getRoleId());
        role.setStatus(statusDTO.getStatus());
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        return toAjax(roleService.updateRoleStatus(role));
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{roleIds}")
    @Log(module = "角色管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("system:role:delete")
    public Result<Void> remove(@PathVariable List<Long> roleIds) {
        return toAjax(roleService.deleteRoleByIds(roleIds));
    }

    /**
     * 获取角色选择框列表
     */
    @GetMapping("/optionselect")
    public Result<List<SysRole>> optionselect() {
        return Result.success(roleService.selectRoleAll());
    }

    /**
     * 查询已分配用户列表
     */
    @GetMapping("/authUser/allocatedList")
    public Result<PageResult<SysUserVO>> allocatedList(RoleUserQueryDTO query) {
        PageResult<SysUserVO> result = roleService.selectAllocatedList(query);
        return Result.success(result);
    }

    /**
     * 查询未分配用户列表
     */
    @GetMapping("/authUser/unallocatedList")
    public Result<PageResult<SysUserVO>> unallocatedList(RoleUserQueryDTO query) {
        PageResult<SysUserVO> result = roleService.selectUnallocatedList(query);
        return Result.success(result);
    }

    /**
     * 取消授权用户
     */
    @PutMapping("/authUser/cancel")
    @Log(module = "角色管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:role:edit")
    public Result<Void> cancelAuthUser(@Validated @RequestBody SysUserRoleDTO userRoleDTO) {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userRoleDTO.getUserId());
        userRole.setRoleId(userRoleDTO.getRoleId());
        return toAjax(roleService.deleteAuthUser(userRole));
    }

    /**
     * 批量取消授权用户
     */
    @PutMapping("/authUser/cancelAll")
    @Log(module = "角色管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:role:edit")
    public Result<Void> cancelAuthUserAll(@RequestParam("roleId") Long roleId, @RequestParam(value = "userIds", required = false) Long[] userIds) {
        return toAjax(roleService.deleteAuthUsers(roleId, userIds));
    }

    /**
     * 批量选择用户授权
     */
    @PutMapping("/authUser/selectAll")
    @Log(module = "角色管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:role:edit")
    public Result<Void> selectAuthUserAll(@Validated @RequestParam("roleId") Long roleId, @RequestParam(value = "userIds", required = false) Long[] userIds) {
        roleService.checkRoleDataScope(roleId);
        return toAjax(roleService.insertAuthUsers(roleId, userIds));
    }

    /**
     * 根据角色ID查询菜单ID列表
     */
    @GetMapping("/authMenu/{roleId}")
    public Result<List<Long>> getAuthMenu(@PathVariable("roleId") Long roleId) {
        roleService.checkRoleDataScope(roleId);
        return Result.success(roleService.selectMenuListByRoleId(roleId));
    }

    /**
     * 批量选择授权菜单角色
     */
    @PutMapping("/authMenu/selectAll")
    @Log(module = "角色管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:role:edit")
    public Result<Void> selectAuthMenuAll(@RequestParam("roleId") Long roleId, @RequestParam(value = "menuIds", required = false) Long[] menuIds) {
        roleService.checkRoleDataScope(roleId);
        return toAjax(roleService.insertAuthMenus(roleId, menuIds));
    }
}
