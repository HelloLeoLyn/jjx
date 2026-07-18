package com.jjx.system.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.tree.TreeUtils;
import com.jjx.system.domain.dto.SysMenuQueryDTO;
import com.jjx.system.domain.entity.SysMenu;
import com.jjx.system.domain.entity.SysUserRole;
import com.jjx.system.domain.vo.SysMenuVO;
import com.jjx.system.mapper.SysMenuMapper;
import com.jjx.system.service.ISysMenuService;
import com.jjx.system.service.ISysUserRoleService;
import com.jjx.system.utils.SysMenuConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 菜单 服务实现
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements ISysMenuService {
    private final SysMenuMapper menuMapper;
    private final ISysUserRoleService userRoleService;
    private final SysMenuConverter menuConverter;


    @Override
    public List<SysMenu> selectMenuList(SysMenuQueryDTO menu) {
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        if (CharSequenceUtil.isNotBlank(menu.getMenuName())) {
            queryWrapper.like(SysMenu::getMenuName, menu.getMenuName());
        }
        if (CharSequenceUtil.isNotBlank(menu.getVisible())) {
            queryWrapper.eq(SysMenu::getVisible, menu.getVisible());
        }
        if (CharSequenceUtil.isNotBlank(menu.getStatus())) {
            queryWrapper.eq(SysMenu::getStatus, menu.getStatus());
        }
        if (CharSequenceUtil.isNotBlank(menu.getPerms())) {
            queryWrapper.like(SysMenu::getPerms, menu.getPerms());
        }
        queryWrapper.orderByAsc(SysMenu::getParentId, SysMenu::getOrderNum);
        return menuMapper.selectList(queryWrapper);
    }

    @Override
    public Set<String> selectMenuPermsByUserId(Long userId) {
        Set<String> permissions = new HashSet<>();
        if(ObjectUtil.isEmpty(userId)){
            return Collections.emptySet();
        }else{
            if (userId == 1L) {
                permissions.add("*:*:*"); // 通配符权限，拥有所有权限
                return permissions;
            } else  {
                // 普通用户从数据库查询实际权限
                List<SysUserRole> sysUserRoles = userRoleService.selectByUserId(userId);
                List<Long> list = sysUserRoles.stream().map(SysUserRole::getRoleId).toList();
                if(ObjectUtil.isEmpty(list)) {
                    return Collections.emptySet();
                }
                // 如果userId为null，返回空权限集合
                return menuMapper.selectMenusPermsByRoleIds(list);
            }
        }
    }

    @Override
    public List<SysMenuVO> selectMenuTreeByUserId(Long userId) {
        List<SysUserRole> sysUserRoles = userRoleService.selectByUserId(userId);
        List<Long> list = sysUserRoles.stream().map(SysUserRole::getRoleId).toList();
        if(ObjectUtil.isEmpty(list)) {
            return Collections.emptyList();
        }
        List<SysMenu> sysMenus = baseMapper.selectMenuTreeByRoles(list);
        List<SysMenuVO> sysMenuVOS = menuConverter.toVOList(sysMenus);
        return TreeUtils.build(sysMenuVOS);
    }

    @Override
    public List<SysMenuVO> selectMenuListByRoleId(Long roleId) {
        List<Long> list = new ArrayList<>();
        list.add(roleId);
        List<SysMenu> sysMenus = baseMapper.selectMenuTreeByRoles(list);
        return menuConverter.toVOList(sysMenus);
    }


    @Override
    public SysMenu selectMenuById(Long menuId) {
        return menuMapper.selectById(menuId);
    }

    @Override
    public boolean hasChildByMenuId(Long menuId) {
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysMenu::getParentId, menuId);
        return menuMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    public boolean checkMenuExistRole(Long menuId) {
        List<Long> roleIds = menuMapper.selectRoleListByMenuId(menuId);
        return roleIds != null && !roleIds.isEmpty();
    }

    @Override
    public boolean insertMenu(SysMenu menu) {
        return save(menu);
    }

    @Override
    public boolean updateMenu(SysMenu menu) {
        return updateById(menu);
    }

    @Override
    public boolean deleteMenuById(Long menuId) {
        // 检查是否有子菜单
        if (hasChildByMenuId(menuId)) {
            // 有子菜单，不能删除
            return false;
        }
        // 检查菜单下是否有角色
        if (checkMenuExistRole(menuId)) {
            // 有角色，不能删除
            return false;
        }
        return removeById(menuId);
    }

    @Override
    public boolean checkMenuNameUnique(SysMenu menu) {
        Long menuId = menu.getMenuId() == null ? -1L : menu.getMenuId();
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysMenu::getMenuName, menu.getMenuName());
        queryWrapper.eq(SysMenu::getParentId, menu.getParentId());
        SysMenu info = menuMapper.selectOne(queryWrapper);
        return info == null || info.getMenuId().equals(menuId);
    }

    @Override
    public List<Long> selectRoleListByMenuId(Long menuId) {
        return menuMapper.selectRoleListByMenuId(menuId);
    }

    @Override
    public boolean insertAuthRoles(Long menuId, Long[] roleIds) {
        if (menuId == null || roleIds == null || roleIds.length == 0) {
            return false;
        }

        try {
            // 1. 先删除该菜单现有的所有角色关联
            menuMapper.deleteMenuRoleByMenuId(menuId);

            // 2. 然后插入新的角色关联
            List<Long> roleIdList = Arrays.asList(roleIds);
            int result = menuMapper.batchMenuRole(menuId, roleIdList);

            return result > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
