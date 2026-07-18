package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.system.domain.entity.SysMenu;
import org.apache.ibatis.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单权限Mapper接口
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 根据角色ID列表查询菜单
     */
    default Set<String> selectMenusPermsByRoleIds(List<Long> roleIds) {
        String roleIdsStr = roleIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(SysMenu::getPerms).apply("exists(select 1 from sys_role_menu rm where rm.menu_id = sys_menu.menu_id and rm.role_id in (" + roleIdsStr + "))")
                .orderByAsc(SysMenu::getPerms, SysMenu::getOrderNum);
        List<SysMenu> sysMenus = selectList(queryWrapper);
        return sysMenus.stream().map(SysMenu::getPerms).collect(Collectors.toSet());
    }

    /**
     * 根据角色ID列表查询菜单
     */
    default List<SysMenu> selectMenuTreeByRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 构建 role_id in (1,2,3) 的 SQL 片段
        String roleIdsStr = roleIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        return selectList(new LambdaQueryWrapper<SysMenu>()
                .apply("exists(select 1 from sys_role_menu rm where rm.menu_id = sys_menu.menu_id and rm.role_id in (" + roleIdsStr + "))")
                .orderByAsc(SysMenu::getParentId, SysMenu::getOrderNum));
    }

    /**
     * 根据菜单ID查询角色列表
     *
     * @param menuId 菜单ID
     * @return 角色ID列表
     */
    @Select("SELECT role_id FROM sys_role_menu WHERE menu_id = #{menuId}")
    List<Long> selectRoleListByMenuId(@Param("menuId") Long menuId);

    /**
     * 删除菜单的角色关联
     *
     * @param menuId 菜单ID
     * @return 删除数量
     */
    @Delete("DELETE FROM sys_role_menu WHERE menu_id = #{menuId}")
    int deleteMenuRoleByMenuId(@Param("menuId") Long menuId);

    /**
     * 批量插入菜单角色关联
     *
     * @param menuId 菜单ID
     * @param roleIds 角色ID列表
     * @return 插入数量
     */
    @Insert("<script>" +
            "INSERT INTO sys_role_menu (role_id, menu_id) VALUES " +
            "<foreach collection='roleIds' item='roleId' separator=','>" +
            "(#{roleId}, #{menuId})" +
            "</foreach>" +
            "</script>")
    int batchMenuRole(@Param("menuId") Long menuId, @Param("roleIds") List<Long> roleIds);
}
