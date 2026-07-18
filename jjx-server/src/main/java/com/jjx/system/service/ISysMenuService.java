package com.jjx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.system.domain.dto.SysMenuQueryDTO;
import com.jjx.system.domain.entity.SysMenu;
import com.jjx.system.domain.vo.SysMenuVO;

import java.util.List;
import java.util.Set;

/**
 * 菜单 业务层
 */
public interface ISysMenuService extends IService<SysMenu> {

    /**
     * 查询系统菜单列表
     *
     * @param menu   菜单信息
     * @return 菜单列表
     */
    List<SysMenu> selectMenuList(SysMenuQueryDTO menu);

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    Set<String> selectMenuPermsByUserId(Long userId);

    /**
     * 根据用户ID查询菜单树信息
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<SysMenuVO> selectMenuTreeByUserId(Long userId);

    /**
     * 根据角色ID查询菜单树信息
     *
     * @param roleId 角色ID
     * @return 选中菜单列表
     */
    List<SysMenuVO> selectMenuListByRoleId(Long roleId);

    /**
     * 根据菜单ID查询信息
     *
     * @param menuId 菜单ID
     * @return 菜单信息
     */
    SysMenu selectMenuById(Long menuId);

    /**
     * 是否存在菜单子节点
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    boolean hasChildByMenuId(Long menuId);

    /**
     * 查询菜单是否存在角色
     *
     * @param menuId 菜单ID
     * @return 结果 true 存在 false 不存在
     */
    boolean checkMenuExistRole(Long menuId);

    /**
     * 新增保存菜单信息
     *
     * @param menu 菜单信息
     * @return 结果
     */
    boolean insertMenu(SysMenu menu);

    /**
     * 修改保存菜单信息
     *
     * @param menu 菜单信息
     * @return 结果
     */
    boolean updateMenu(SysMenu menu);

    /**
     * 删除菜单管理信息
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    boolean deleteMenuById(Long menuId);

    /**
     * 校验菜单名称是否唯一
     *
     * @param menu 菜单信息
     * @return 结果
     */
    boolean checkMenuNameUnique(SysMenu menu);

    /**
     * 根据菜单ID查询角色列表
     *
     * @param menuId 菜单ID
     * @return 角色ID列表
     */
    List<Long> selectRoleListByMenuId(Long menuId);

    /**
     * 为菜单分配角色
     *
     * @param menuId 菜单ID
     * @param roleIds 角色ID数组
     * @return 结果
     */
    boolean insertAuthRoles(Long menuId, Long[] roleIds);
}
