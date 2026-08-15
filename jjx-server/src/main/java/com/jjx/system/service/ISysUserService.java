package com.jjx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.system.domain.dto.ResetPasswordDTO;
import com.jjx.system.domain.dto.SysUserDTO;
import com.jjx.system.domain.dto.SysUserProfileDTO;
import com.jjx.system.domain.dto.SysUserStatusDTO;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.domain.vo.SysUserVO;

import java.util.List;

/**
 * 用户 业务层
 */
public interface ISysUserService extends IService<SysUser> {

    /**
     * 根据条件分页查询用户列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    PageResult<SysUser> selectUserList(SysUser user, Integer pageNum, Integer pageSize);

    /**
     * 根据条件分页查询用户列表（返回VO）
     *
     * @param user 用户信息
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 用户VO分页结果
     */
    PageResult<SysUserVO> selectUserVOList(SysUser user, Integer pageNum, Integer pageSize);

    /**
     * 按条件查询全部用户（不分页，导出用）
     *
     * @param user 查询条件
     * @return 用户列表
     */
    java.util.List<SysUser> selectAllUserList(SysUser user);

    /**
     * 根据条件分页查询已分配用户角色列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    PageResult<SysUser> selectAllocatedList(SysUser user, Integer pageNum, Integer pageSize);

    /**
     * 根据条件分页查询未分配用户角色列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    PageResult<SysUser> selectUnallocatedList(SysUser user, Integer pageNum, Integer pageSize);

    /**
     * 通过用户名查询用户
     *
     * @param userName 用户名
     * @return 用户对象信息
     */
    SysUser selectUserByUserName(String userName);

    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    SysUserVO selectUserById(Long userId);

    /**
     * 根据用户ID查询用户所属角色组
     *
     * @param userName 用户名
     * @return 结果
     */
    String selectUserRoleGroup(String userName);

    /**
     * 根据用户ID查询用户所属岗位组
     *
     * @param userName 用户名
     * @return 结果
     */
    String selectUserPostGroup(String userName);

    /**
     * 校验用户名称是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean checkUserNameUnique(SysUser user);

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean checkPhoneUnique(SysUser user);

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean checkEmailUnique(SysUser user);

    /**
     * 校验用户是否允许操作
     *
     * @param user 用户信息
     */
    void checkUserAllowed(SysUser user);

    /**
     * 校验用户是否有数据权限
     *
     * @param userId 用户id
     */
    void checkUserDataScope(Long userId);

    /**
     * 新增用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean insertUser(SysUser user);

    /**
     * 新增用户信息（DTO）
     *
     * @param dto 用户DTO
     * @return 结果
     */
    boolean insertUser(SysUserDTO dto);

    /**
     * 注册用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean registerUser(SysUser user);

    /**
     * 修改用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean updateUser(SysUserDTO user);

    /**
     * 用户授权角色
     *
     * @param userId 用户ID
     * @param roleIds 角色组
     */
    void insertUserAuth(Long userId, Long[] roleIds);

    /**
     * 修改用户状态
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean updateUserStatus(SysUser user);

    /**
     * 修改用户状态（DTO）
     *
     * @param dto 用户状态DTO
     * @return 结果
     */
    boolean updateUserStatus(SysUserStatusDTO dto);

    /**
     * 修改用户基本信息
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean updateUserProfile(SysUser user);

    /**
     * 修改用户基本信息（DTO）
     *
     * @param dto 用户个人信息DTO
     * @return 结果
     */
    boolean updateUserProfile(SysUserProfileDTO dto);

    /**
     * 修改用户头像
     *
     * @param userName 用户名
     * @param avatar 头像地址
     * @return 结果
     */
    boolean updateUserAvatar(String userName, String avatar);

    /**
     * 重置用户密码
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean resetPwd(SysUser user);

    /**
     * 重置用户密码（DTO）
     *
     * @param dto 用户状态DTO（含userId和password）
     * @return 结果
     */
    boolean resetPwd(SysUserStatusDTO dto);

    /**
     * 重置用户密码
     */
    boolean resetUserPwd(ResetPasswordDTO dto);

    /**
     * 通过用户ID删除用户
     *
     * @param userId 用户ID
     * @return 结果
     */
    boolean deleteUserById(Long userId);

    /**
     * 批量删除用户信息
     *
     * @param userIds 需要删除的用户ID
     * @return 结果
     */
    boolean deleteUserByIds(List<Long> userIds);

    /**
     * 导入用户数据
     *
     * @param userList 用户数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    String importUser(List<SysUser> userList, Boolean isUpdateSupport, String operName);

    SysUser findByPhone(String phone, Long tenantId);

    SysUser registerByPhone(String phone, Long tenantId);

    /**
     * 按角色标识前缀查询用户列表（2026-08-11 销售负责人等下拉用，不依赖角色ID）
     *
     * @param roleKeyPrefix 角色标识前缀，如 "sales"
     * @return 用户列表
     */
    java.util.List<SysUser> selectUsersByRoleKeyPrefix(String roleKeyPrefix);

}
