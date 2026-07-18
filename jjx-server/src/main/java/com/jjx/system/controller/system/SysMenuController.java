package com.jjx.system.controller.system;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.tree.TreeUtils;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.system.annotation.Log;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.dto.SysMenuDTO;
import com.jjx.system.domain.dto.SysMenuQueryDTO;
import com.jjx.system.domain.entity.SysMenu;
import com.jjx.system.domain.vo.AsyncRouteConfigVO;
import com.jjx.system.domain.vo.SysMenuVO;
import com.jjx.system.service.ISysMenuService;
import com.jjx.system.utils.RouterHelper;
import com.jjx.system.utils.SysMenuConverter;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 菜单信息
 */
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController extends BaseController {

    private final ISysMenuService menuService;

    private final RouterHelper routerHelper;

    private final SysMenuConverter menuConverter;
    /**
     * 获取菜单列表
     */
    @GetMapping("/list")
    public Result<List<SysMenuVO>> list(SysMenuQueryDTO menu) {
        List<SysMenu> menus = menuService.selectMenuList(menu);
        List<SysMenuVO> voList = menuConverter.toVOList(menus);
        return Result.success(voList);
    }

    /**
     * 根据菜单编号获取详细信息
     */
    @GetMapping(value = "/{menuId}")
    public Result<SysMenu> getInfo(@PathVariable Long menuId) {
        return Result.success(menuService.selectMenuById(menuId));
    }

    /**
     * 获取菜单下拉树列表
     */
    @GetMapping("/treeselect")
    public Result<List<SysMenuVO>> treeSelect(SysMenuQueryDTO menu) {
        List<SysMenu> menus = menuService.selectMenuList(menu);
        List<SysMenuVO> sysMenuVOS = menuConverter.toVOList(menus);
        List<SysMenuVO> build = TreeUtils.build(sysMenuVOS);
        return Result.success(build);
    }

    /**
     * 新增菜单
     */
    @PostMapping
    @Log(module = "菜单管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("system:menu:add")
    public Result<Void> add(@Validated @RequestBody SysMenuDTO menuDTO) {
        SysMenu entity = menuConverter.toEntity(menuDTO);
        if (!menuService.checkMenuNameUnique(entity)) {
            throw new BusinessException("新增菜单'" + entity.getMenuName() + "'失败，菜单名称已存在");
        }
        return toAjax(menuService.insertMenu(entity));
    }

    /**
     * 修改菜单
     */
    @PutMapping
    @Log(module = "菜单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:menu:edit")
    public Result<Void> edit(@Validated @RequestBody SysMenuDTO dto) {
        SysMenu entity = menuConverter.toEntity(dto);
        if (!menuService.checkMenuNameUnique(entity)) {
            throw new BusinessException("修改菜单'" + entity.getMenuName() + "'失败，菜单名称已存在");
        }
        return toAjax(menuService.updateMenu(entity));
    }

    /**
     * 删除菜单
     */
    @DeleteMapping("/{menuId}")
    @Log(module = "菜单管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("system:menu:delete")
    public Result<Void> remove(@PathVariable Long menuId) {
        if (menuService.hasChildByMenuId(menuId)) {
            throw new BusinessException("存在子菜单,不允许删除");
        }
        if (menuService.checkMenuExistRole(menuId)) {
            throw new BusinessException("菜单已分配,不允许删除");
        }
        return toAjax(menuService.deleteMenuById(menuId));
    }

    /**
     * 获取路由信息
     */
    @GetMapping("/getRouters")
    public Result<List<AsyncRouteConfigVO>> getRouters() {
        long userId = StpUtil.getLoginIdAsLong();
        List<SysMenuVO> sysMenuVOS = menuService.selectMenuTreeByUserId(userId);
        // 转换为路由配置
        return Result.success(RouterHelper.buildRoutes(sysMenuVOS));
    }

    /**
     * 获取当前用户权限列表
     */
    @GetMapping("/permissions")
    public Result<Set<String>> getPermissions() {
        long userId = StpUtil.getLoginIdAsLong();
        Set<String> permissions = menuService.selectMenuPermsByUserId(userId);
        return Result.success(permissions);
    }
    /**
     * 获取菜单的角色权限
     */
    @GetMapping({"/authRole/{menuId}","menuId/{menuId}"})
    public Result<List<Long>> getAuthRoles(@PathVariable Long menuId) {
        return Result.success(menuService.selectRoleListByMenuId(menuId));
    }

    /**
     * 为菜单分配角色
     */
    @PutMapping("/authRole/selectAll")
    @Log(module = "菜单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:menu:edit")
    public Result<Void> addAuthRoles(@Validated @RequestParam("menuId") Long menuId,
                                     @RequestParam(value = "roleIds", required = false) Long[] roleIds) {
        return toAjax(menuService.insertAuthRoles(menuId, roleIds));
    }
}
