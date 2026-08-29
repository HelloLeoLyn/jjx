package com.jjx.system.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.converter.SysRoleConverter;
import com.jjx.system.converter.SysUserConverter;
import com.jjx.system.domain.dto.RoleUserQueryDTO;
import com.jjx.system.domain.dto.SysRoleDTO;
import com.jjx.system.domain.entity.SysMenu;
import com.jjx.system.domain.entity.SysRole;
import com.jjx.system.domain.entity.SysRoleMenu;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.domain.entity.SysUserRole;
import com.jjx.system.domain.vo.SysRoleVO;
import com.jjx.system.domain.vo.SysUserVO;
import com.jjx.system.mapper.*;
import com.jjx.system.service.ISysRoleService;
import com.jjx.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 角色 服务实现
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements ISysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper userRoleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserConverter sysUserConverter;
    private final SysRoleConverter sysRoleConverter;
    private final SysConfigService sysConfigService;

    @Override
    public PageResult<SysRole> selectRoleList(SysRole role, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(role.getRoleName())) {
            queryWrapper.like(SysRole::getRoleName, role.getRoleName());
        }
        if (StringUtils.isNotBlank(role.getRoleKey())) {
            queryWrapper.like(SysRole::getRoleKey, role.getRoleKey());
        }
        if (StringUtils.isNotBlank(role.getStatus())) {
            queryWrapper.eq(SysRole::getStatus, role.getStatus());
        }
        queryWrapper.orderByAsc(SysRole::getRoleSort);

        Page<SysRole> page = new Page<>(pageNum, pageSize);
        IPage<SysRole> result = roleMapper.selectPage(page, queryWrapper);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public List<SysRoleVO> selectRoleVOList(SysRole role) {
        LambdaQueryWrapper<SysRole> queryWrapper = buildQueryWrapper(role);
        List<SysRole> list = roleMapper.selectList(queryWrapper);
        return sysRoleConverter.toVOList(list);
    }

    @Override
    public PageResult<SysRoleVO> selectRoleVOList(SysRole role, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SysRole> queryWrapper = buildQueryWrapper(role);
        Page<SysRole> page = new Page<>(pageNum, pageSize);
        IPage<SysRole> result = roleMapper.selectPage(page, queryWrapper);
        List<SysRoleVO> voList = sysRoleConverter.toVOList(result.getRecords());
        return PageResult.build(voList, result.getTotal());
    }

    /**
     * 构建角色查询条件
     */
    private LambdaQueryWrapper<SysRole> buildQueryWrapper(SysRole role) {
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        if (role != null) {
            if (StringUtils.isNotBlank(role.getRoleName())) {
                queryWrapper.like(SysRole::getRoleName, role.getRoleName());
            }
            if (StringUtils.isNotBlank(role.getRoleKey())) {
                queryWrapper.like(SysRole::getRoleKey, role.getRoleKey());
            }
            if (StringUtils.isNotBlank(role.getStatus())) {
                queryWrapper.eq(SysRole::getStatus, role.getStatus());
            }
        }
        queryWrapper.orderByAsc(SysRole::getRoleSort);
        return queryWrapper;
    }

    @Override
    public List<SysRole> selectRolesByUserId(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }

        // 调用Mapper查询用户的角色
        return roleMapper.selectRolePermissionByUserId(userId);
    }

    @Override
    public Set<String> selectRolePermissionByUserId(Long userId) {
        Set<String> permissions = new HashSet<>();

        // 如果是超级管理员（用户ID为1），返回所有权限
        if (userId != null && userId == 1L) {
            permissions.add("*:*:*"); // 通配符权限，拥有所有权限
        } else if (userId != null) {
            // 普通用户从数据库查询实际权限
            return Collections.emptySet();
        }
        // 如果userId为null，返回空权限集合

        return permissions;
    }

    @Override
    public List<SysRole> selectRoleAll() {
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysRole::getStatus, "0");
        return roleMapper.selectList(queryWrapper);
    }

    @Override
    public List<Long> selectRoleListByUserId(Long userId) {
        // 暂时返回空列表
        return new ArrayList<>();
    }

    @Override
    public SysRole selectRoleById(Long roleId) {
        return roleMapper.selectById(roleId);
    }

    @Override
    public boolean checkRoleNameUnique(SysRole role) {
        Long roleId = role.getRoleId() == null ? -1L : role.getRoleId();
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysRole::getRoleName, role.getRoleName());
        SysRole info = roleMapper.selectOne(queryWrapper);
        return info == null || info.getRoleId().equals(roleId);
    }

    @Override
    public boolean checkRoleKeyUnique(SysRole role) {
        Long roleId = role.getRoleId() == null ? -1L : role.getRoleId();
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysRole::getRoleKey, role.getRoleKey());
        SysRole info = roleMapper.selectOne(queryWrapper);
        return info == null || info.getRoleId().equals(roleId);
    }

    @Override
    public void checkRoleAllowed(SysRole role) {
        if (role.getRoleId() != null && role.getRoleId() == 1L) {
            throw new BusinessException("不允许操作超级管理员角色");
        }
    }

    @Override
    public void checkRoleDataScope(Long roleId) {
        // 数据权限检查，暂时不实现
    }

    @Override
    public int countUserRoleByRoleId(Long roleId) {
        // 使用userRoleMapper查询角色使用数量
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getRoleId, roleId);
        Long count = userRoleMapper.selectCount(queryWrapper);
        return count != null ? count.intValue() : 0;
    }

    @Override
    public boolean insertRole(SysRoleDTO dto) {
        SysRole role = sysRoleConverter.toEntity(dto);
        return save(role);
    }

    @Override
    public boolean updateRole(SysRoleDTO dto) {
        SysRole role = sysRoleConverter.toEntity(dto);
        return updateById(role);
    }

    @Override
    public boolean updateRoleStatus(SysRole role) {
        return updateById(role);
    }

    @Override
    public boolean authDataScope(SysRole role) {
        // 数据权限授权，暂时返回true
        return true;
    }

    @Override
    public boolean deleteRoleById(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role != null && isReferencedByProductionConfig(role.getRoleKey())) {
            return false;
        }
        // 检查角色是否被用户使用
        if (countUserRoleByRoleId(roleId) > 0) {
            // 有用户使用，不能删除
            return false;
        }
        return removeById(roleId);
    }

    private boolean isReferencedByProductionConfig(String roleKey) {
        if (StringUtils.isBlank(roleKey)) {
            return false;
        }
        Map<String, String> configs = sysConfigService.listActiveMapByGroup("production_config");
        return configs.values().stream()
                .filter(StringUtils::isNotBlank)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .anyMatch(roleKey::equals);
    }

    @Override
    public boolean deleteRoleByIds(List<Long> roleIds) {
        for (Long roleId : roleIds) {
            deleteRoleById(roleId);
        }
        return true;
    }

    @Override
    public boolean deleteAuthUser(SysUserRole userRole) {
        if (userRole == null || userRole.getRoleId() == null || userRole.getUserId() == null) {
            return false;
        }

        // 检查角色是否存在
        SysRole role = roleMapper.selectById(userRole.getRoleId());
        if (role == null) {
            throw new BusinessException("角色不存在");
        }

        // 删除用户角色关联
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getRoleId, userRole.getRoleId());
        queryWrapper.eq(SysUserRole::getUserId, userRole.getUserId());
        int result = userRoleMapper.delete(queryWrapper);
        return result > 0;
    }

    @Override
    public boolean deleteAuthUsers(Long roleId, Long[] userIds) {
        if (roleId == null || userIds == null || userIds.length == 0) {
            return false;
        }

        // 检查角色是否存在
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }

        // 批量删除用户角色关联
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getRoleId, roleId);
        queryWrapper.in(SysUserRole::getUserId, Arrays.asList(userIds));
        int result = userRoleMapper.delete(queryWrapper);
        return result > 0;
    }

    @Override
    public boolean insertAuthUsers(Long roleId, Long[] userIds) {
        if (roleId == null || userIds == null || userIds.length == 0) {
            return false;
        }

        // 检查角色是否存在
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }

        // 检查用户是否存在
        for (Long userId : userIds) {
            SysUser user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException("用户ID " + userId + " 不存在");
            }
        }

        // 创建用户角色关联列表
        List<SysUserRole> userRoleList = new ArrayList<>();
        for (Long userId : userIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setRoleId(roleId);
            userRole.setUserId(userId);
            userRoleList.add(userRole);
        }

        // 批量插入用户角色关联
        for (SysUserRole userRole : userRoleList) {
            userRoleMapper.insert(userRole);
        }
        return true;
    }

    @Override
    public PageResult<SysUserVO> selectAllocatedList(RoleUserQueryDTO queryDTO) {
        // 查询已分配用户列表
        // 这里需要查询已经分配了指定角色的用户
        // 注意：这里使用roleId来过滤已分配的用户
        Page<SysUser> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getDelFlag, "0"); // 未删除的用户
        // 如果有角色ID，则查询已分配该角色的用户
        if (queryDTO.getRoleId() != null) {
            // 查询sys_user_role表获取已分配该角色的用户ID列表
            LambdaQueryWrapper<SysUserRole> userRoleQueryWrapper = new LambdaQueryWrapper<>();
            userRoleQueryWrapper.eq(SysUserRole::getRoleId, queryDTO.getRoleId());
            List<SysUserRole> userRoleList = userRoleMapper.selectList(userRoleQueryWrapper);

            if (!userRoleList.isEmpty()) {
                // 提取用户ID列表
                List<Long> userIds = userRoleList.stream()
                    .map(SysUserRole::getUserId)
                    .toList();

                // 在用户查询中添加用户ID过滤
                queryWrapper.in(SysUser::getUserId, userIds);
            } else {
                // 如果没有用户分配了该角色，返回空列表
                queryWrapper.eq(SysUser::getUserId, -1L); // 确保返回空结果
            }
        }

        // 支持按用户名称搜索
        if (CharSequenceUtil.isNotBlank(queryDTO.getRoleName())) {
            queryWrapper.like(SysUser::getUserName, queryDTO.getRoleName());
        }

        queryWrapper.orderByDesc(SysUser::getCreateTime);
        IPage<SysUser> result = userMapper.selectPage(page, queryWrapper);
        List<SysUserVO> list = sysUserConverter.toVOList(result.getRecords());
        return PageResult.build(list, result.getTotal());
    }

    @Override
    public PageResult<SysUserVO> selectUnallocatedList(RoleUserQueryDTO query) {
        // 查询未分配用户列表
        // 这里需要查询没有分配指定角色的用户
        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getDelFlag, "0"); // 未删除的用户

        // 如果有角色ID，则查询未分配该角色的用户
        if (query.getRoleId() != null) {
            // 查询sys_user_role表获取已分配该角色的用户ID列表
            LambdaQueryWrapper<SysUserRole> userRoleQueryWrapper = new LambdaQueryWrapper<>();
            userRoleQueryWrapper.eq(SysUserRole::getRoleId, query.getRoleId());
            List<SysUserRole> userRoleList = userRoleMapper.selectList(userRoleQueryWrapper);

            if (!userRoleList.isEmpty()) {
                // 提取已分配的用户ID列表
                List<Long> allocatedUserIds = userRoleList.stream()
                    .map(SysUserRole::getUserId)
                    .toList();

                // 在用户查询中排除已分配的用户
                queryWrapper.notIn(SysUser::getUserId, allocatedUserIds);
            }
            // 如果没有用户分配了该角色，则查询所有用户（不添加排除条件）
        }

        // 支持按用户名称搜索
        if (StringUtils.isNotBlank(query.getRoleName())) {
            queryWrapper.like(SysUser::getUserName, query.getRoleName());
        }

        queryWrapper.orderByDesc(SysUser::getCreateTime);
        IPage<SysUser> result = userMapper.selectPage(page, queryWrapper);

        List<SysUser> records = result.getRecords();
        List<SysUserVO> list = sysUserConverter.toVOList(records);
        return PageResult.build(list, result.getTotal());
    }

    @Override
    public List<Long> selectMenuListByRoleId(Long roleId) {
        if (roleId == null) {
            return new ArrayList<>();
        }

        // 查询角色菜单关联表，获取菜单ID列表
        LambdaQueryWrapper<SysRoleMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysRoleMenu::getRoleId, roleId);
        List<SysRoleMenu> roleMenuList = userRoleMenuMapper.selectList(queryWrapper);

        // 提取菜单ID列表
        return roleMenuList.stream()
            .map(SysRoleMenu::getMenuId)
            .toList();
    }

    @Override
    public boolean insertAuthMenus(Long roleId, Long[] menuIds) {
        if (roleId == null || menuIds == null || menuIds.length == 0) {
            return false;
        }

        // 检查角色是否存在
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }

        // 先删除该角色原有的菜单权限
        LambdaQueryWrapper<SysRoleMenu> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(SysRoleMenu::getRoleId, roleId);
        userRoleMenuMapper.delete(deleteWrapper);

        // 一次性加载菜单父子关系，在内存中补齐授权菜单的全部祖先。
        QueryWrapper<SysMenu> menuWrapper = new QueryWrapper<>();
        menuWrapper.select("menu_id", "parent_id");
        List<SysMenu> menus = menuMapper.selectList(menuWrapper);
        Map<Long, Long> parentByMenuId = new HashMap<>();
        for (SysMenu menu : menus) {
            if (menu.getMenuId() != null) {
                parentByMenuId.put(menu.getMenuId(), menu.getParentId());
            }
        }

        Set<Long> expandedMenuIds = new LinkedHashSet<>(Arrays.asList(menuIds));
        for (Long menuId : menuIds) {
            Long currentId = menuId;
            Set<Long> visited = new HashSet<>();
            while (currentId != null && currentId != 0L && visited.add(currentId)) {
                Long parentId = parentByMenuId.get(currentId);
                if (parentId == null || parentId == 0L || !parentByMenuId.containsKey(parentId)) {
                    break;
                }
                expandedMenuIds.add(parentId);
                currentId = parentId;
            }
        }

        // 创建角色菜单关联列表
        List<SysRoleMenu> roleMenuList = new ArrayList<>();
        for (Long menuId : expandedMenuIds) {
            SysRoleMenu roleMenu = new SysRoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            roleMenuList.add(roleMenu);
        }

        // 批量插入角色菜单关联
        for (SysRoleMenu roleMenu : roleMenuList) {
            userRoleMenuMapper.insert(roleMenu);
        }
        return true;
    }

    @Override
    public List<String> selectRoleNameByUsrId(Long userId) {
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(SysRole::getRoleName)
                .inSql(SysRole::getRoleId,
                        "select role_id from sys_user_role where user_id = " + userId);
        List<SysRole> sysRoles = baseMapper.selectList(queryWrapper);
        return sysRoles.stream().map(SysRole::getRoleName).toList();
    }
}
