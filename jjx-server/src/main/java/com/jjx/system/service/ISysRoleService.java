package com.jjx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.system.domain.dto.RoleUserQueryDTO;
import com.jjx.system.domain.dto.SysRoleDTO;
import com.jjx.system.domain.entity.SysRole;
import com.jjx.system.domain.entity.SysUserRole;
import com.jjx.system.domain.vo.SysRoleVO;
import com.jjx.system.domain.vo.SysUserVO;

import java.util.List;
import java.util.Set;

/**
 * 角色 业务层
 */
public interface ISysRoleService extends IService<SysRole> {

    /**
     * 根据条件分页查询角色数据
     *
     * @param role 角色信息
     * @return 角色数据集合信息
     */
    PageResult<SysRole> selectRoleList(SysRole role, Integer pageNum, Integer pageSize);

    /**
     * 根据用户ID查询角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    List<SysRole> selectRolesByUserId(Long userId);

    /**
     * 根据用户ID查询角色权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    Set<String> selectRolePermissionByUserId(Long userId);

    /**
     * 查询所有角色
     *
     * @return 角色列表
     */
    List<SysRole> selectRoleAll();

    /**
     * 根据用户ID获取角色选择框列表
     *
     * @param userId 用户ID
     * @return 选中角色ID列表
     */
    List<Long> selectRoleListByUserId(Long userId);

    /**
     * 通过角色ID查询角色
     *
     * @param roleId 角色ID
     * @return 角色对象信息
     */
    SysRole selectRoleById(Long roleId);

    /**
     * 校验角色名称是否唯一
     *
     * @param role 角色信息
     * @return 结果
     */
    boolean checkRoleNameUnique(SysRole role);

    /**
     * 校验角色权限是否唯一
     *
     * @param role 角色信息
     * @return 结果
     */
    boolean checkRoleKeyUnique(SysRole role);

    /**
     * 校验角色是否允许操作
     *
     * @param role 角色信息
     */
    void checkRoleAllowed(SysRole role);

    /**
     * 校验角色是否有数据权限
     *
     * @param roleId 角色id
     */
    void checkRoleDataScope(Long roleId);

    /**
     * 通过角色ID查询角色使用数量
     *
     * @param roleId 角色ID
     * @return 结果
     */
    int countUserRoleByRoleId(Long roleId);

    /**
     * 新增保存角色信息
     *
     * @param dto 角色信息
     * @return 结果
     */
    boolean insertRole(SysRoleDTO dto);

    /**
     * 修改保存角色信息
     *
     * @param dto 角色信息
     * @return 结果
     */
    boolean updateRole(SysRoleDTO dto);

    /**
     * 修改角色状态
     *
     * @param role 角色信息
     * @return 结果
     */
    boolean updateRoleStatus(SysRole role);

    /**
     * 修改数据权限信息
     *
     * @param role 角色信息
     * @return 结果
     */
    boolean authDataScope(SysRole role);

    /**
     * 通过角色ID删除角色
     *
     * @param roleId 角色ID
     * @return 结果
     */
    boolean deleteRoleById(Long roleId);

    /**
     * 批量删除角色信息
     *
     * @param roleIds 需要删除的角色ID
     * @return 结果
     */
    boolean deleteRoleByIds(List<Long> roleIds);

    /**
     * 取消授权用户角色
     *
     * @param userRole 用户和角色关联信息
     * @return 结果
     */
    boolean deleteAuthUser(SysUserRole userRole);

    /**
     * 批量取消授权用户角色
     *
     * @param roleId 角色ID
     * @param userIds 需要取消授权的用户数据ID
     * @return 结果
     */
    boolean deleteAuthUsers(Long roleId, Long[] userIds);

    /**
     * 批量选择授权用户角色
     *
     * @param roleId 角色ID
     * @param userIds 需要删除的用户数据ID
     * @return 结果
     */
    boolean insertAuthUsers(Long roleId, Long[] userIds);

    /**
     * 查询已分配用户列表
     *
     */
    PageResult<SysUserVO> selectAllocatedList(RoleUserQueryDTO query);

    /**
     * 查询未分配用户列表
     *
     */
    PageResult<SysUserVO> selectUnallocatedList(RoleUserQueryDTO query);

    /**
     * 根据角色ID查询菜单ID列表
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     */
    List<Long> selectMenuListByRoleId(Long roleId);

    /**
     * 批量选择授权菜单角色
     *
     * @param roleId 角色ID
     * @param menuIds 需要授权的菜单数据ID
     * @return 结果
     */
    boolean insertAuthMenus(Long roleId, Long[] menuIds);

    /**
     * 根据用户id获取角色
     */
    List<String> selectRoleNameByUsrId(Long userId);

    /**
     * 查询角色列表（全量，返回VO）
     *
     * @param role 查询条件
     * @return 角色VO列表
     */
    List<SysRoleVO> selectRoleVOList(SysRole role);

    /**
     * 查询角色列表（分页，返回VO）
     *
     * @param role 查询条件
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 角色VO分页结果
     */
    PageResult<SysRoleVO> selectRoleVOList(SysRole role, Integer pageNum, Integer pageSize);
}
