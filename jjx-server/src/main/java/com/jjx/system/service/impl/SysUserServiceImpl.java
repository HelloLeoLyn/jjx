package com.jjx.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.system.converter.SysUserConverter;
import com.jjx.system.domain.dto.ResetPasswordDTO;
import com.jjx.system.domain.dto.SysUserDTO;
import com.jjx.system.domain.dto.SysUserProfileDTO;
import com.jjx.system.domain.dto.SysUserStatusDTO;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.domain.entity.SysUserRole;
import com.jjx.system.domain.vo.SysUserVO;
import com.jjx.system.mapper.SysUserMapper;
import com.jjx.system.mapper.SysUserRoleMapper;
import com.jjx.system.service.ISysUserRoleService;
import com.jjx.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户 服务实现
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final ISysUserRoleService userRoleService;
    private final SysUserConverter sysUserConverter;

    @Override
    public PageResult<SysUser> selectUserList(SysUser user, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SysUser> queryWrapper = buildQueryWrapper(user);
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        IPage<SysUser> result = userMapper.selectPage(page, queryWrapper);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public PageResult<SysUserVO> selectUserVOList(SysUser user, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SysUser> queryWrapper = buildQueryWrapper(user);
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        IPage<SysUser> result = userMapper.selectPage(page, queryWrapper);
        List<SysUserVO> voList = sysUserConverter.toVOList(result.getRecords());
        return PageResult.build(voList, result.getTotal());
    }

    @Override
    public PageResult<SysUser> selectAllocatedList(SysUser user, Integer pageNum, Integer pageSize) {
        return selectUserList(user, pageNum, pageSize);
    }

    @Override
    public PageResult<SysUser> selectUnallocatedList(SysUser user, Integer pageNum, Integer pageSize) {
        return selectUserList(user, pageNum, pageSize);
    }

    private static LambdaQueryWrapper<SysUser> buildQueryWrapper(SysUser user) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(user.getUserName())) {
            queryWrapper.like(SysUser::getUserName, user.getUserName());
        }
        if (StringUtils.isNotBlank(user.getNickName())) {
            queryWrapper.like(SysUser::getNickName, user.getNickName());
        }
        if (StringUtils.isNotBlank(user.getPhone())) {
            queryWrapper.like(SysUser::getPhone, user.getPhone());
        }
        if (ObjectUtil.isNotEmpty(user.getStatus())) {
            queryWrapper.eq(SysUser::getStatus, user.getStatus());
        }
        if (user.getDeptId() != null) {
            queryWrapper.eq(SysUser::getDeptId, user.getDeptId());
        }
        queryWrapper.eq(SysUser::getDelFlag, "0");
        queryWrapper.orderByDesc(SysUser::getCreateTime);
        return queryWrapper;
    }

    @Override
    public SysUser selectUserByUserName(String userName) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUserName, userName);
        queryWrapper.eq(SysUser::getDelFlag, "0");
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public SysUserVO selectUserById(Long userId) {
        SysUser sysUser = userMapper.selectById(userId);
        SysUserVO userVO = sysUserConverter.toUserVO(sysUser);
        List<SysUserRole> sysUserRoles = userRoleService.selectByUserId(userId);
        userVO.setRoleIds(sysUserRoles.stream().map(SysUserRole::getRoleId).toList());
        return userVO;
    }

    @Override
    public String selectUserRoleGroup(String userName) {
        return "";
    }

    @Override
    public String selectUserPostGroup(String userName) {
        return "";
    }

    @Override
    public boolean checkUserNameUnique(SysUser user) {
        Long userId = user.getUserId() == null ? -1L : user.getUserId();
        SysUser info = selectUserByUserName(user.getUserName());
        if (info != null && !info.getUserId().equals(userId)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean checkPhoneUnique(SysUser user) {
        Long userId = user.getUserId() == null ? -1L : user.getUserId();
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getPhone, user.getPhone());
        queryWrapper.ne(SysUser::getUserId, userId);
        queryWrapper.eq(SysUser::getDelFlag, "0");
        return userMapper.selectCount(queryWrapper) == 0;
    }

    @Override
    public boolean checkEmailUnique(SysUser user) {
        Long userId = user.getUserId() == null ? -1L : user.getUserId();
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getEmail, user.getEmail());
        queryWrapper.ne(SysUser::getUserId, userId);
        queryWrapper.eq(SysUser::getDelFlag, "0");
        return userMapper.selectCount(queryWrapper) == 0;
    }

    @Override
    public void checkUserAllowed(SysUser user) {
        if (user.getUserId() != null && user.getUserId() == 1L) {
            throw new BusinessException("不允许操作超级管理员用户");
        }
    }

    @Override
    public void checkUserDataScope(Long userId) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insertUser(SysUser user) {
        user.setDelFlag("0");
//        save(user);
//        insertUserRole(user);
        return save(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insertUser(SysUserDTO dto) {
        SysUser user = new SysUser();
        user.setUserName(dto.getUserName());
        user.setNickName(dto.getNickName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setSex(dto.getSex());
        user.setAvatar(dto.getAvatar());
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setStatus(dto.getStatus());
        user.setDeptId(dto.getDeptId());
        user.setRemark(dto.getRemark());
        user.setDelFlag("0");

        if (!checkUserNameUnique(user)) {
            throw new BusinessException("新增用户'" + user.getUserName() + "'失败，登录账号已存在");
        } else if (!checkPhoneUnique(user)) {
            throw new BusinessException("新增用户'" + user.getUserName() + "'失败，手机号码已存在");
        } else if (!checkEmailUnique(user)) {
            throw new BusinessException("新增用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        boolean ok = save(user);
        // 写入用户-角色关联
        if (ok && dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            List<SysUserRole> userRoles = dto.getRoleIds().stream().map(rid -> {
                SysUserRole userRole = new SysUserRole();
                userRole.setRoleId(rid);
                userRole.setUserId(user.getUserId());
                return userRole;
            }).toList();
            userRoleService.getBaseMapper().insert(userRoles);
        }
        return ok;
    }

    @Override
    public boolean registerUser(SysUser user) {
        return save(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(SysUserDTO dto) {
        SysUser user = userMapper.selectById(dto.getUserId());
        checkUserAllowed(user);
        checkUserDataScope(user.getUserId());
        // 将 DTO 的新值应用到 user，再基于新值做唯一性校验
        user.setUserName(dto.getUserName());
        user.setNickName(dto.getNickName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setSex(dto.getSex());
        user.setStatus(dto.getStatus());
        user.setDeptId(dto.getDeptId());
        user.setRemark(dto.getRemark());

        if (!checkUserNameUnique(user)) {
            throw new BusinessException(BusinessExceptionEnum.FAIL,"登录账号已存在");
        } else if (!checkPhoneUnique(user)) {
            throw new BusinessException(BusinessExceptionEnum.FAIL,"手机号码已存在");
        } else if (!checkEmailUnique(user)) {
            throw new BusinessException(BusinessExceptionEnum.FAIL,"邮箱账号已存在");
        }
        // 角色关联由“分配角色”功能（authRole）统一维护，编辑用户不再重写角色
        return updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertUserAuth(Long userId, Long[] roleIds) {
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getUserId, userId);
        userRoleMapper.delete(queryWrapper);
        if (roleIds != null && roleIds.length > 0) {
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    @Override
    public boolean updateUserStatus(SysUser user) {
        return updateById(user);
    }

    @Override
    public boolean updateUserStatus(SysUserStatusDTO dto) {
        SysUser user = new SysUser();
        user.setUserId(dto.getUserId());
        user.setStatus(dto.getStatus());
        checkUserAllowed(user);
        checkUserDataScope(user.getUserId());
        return updateById(user);
    }

    @Override
    public boolean updateUserProfile(SysUser user) {
        return updateById(user);
    }

    @Override
    public boolean updateUserProfile(SysUserProfileDTO dto) {
        SysUser user = new SysUser();
        user.setUserId(dto.getUserId());
        user.setNickName(dto.getNickName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setSex(dto.getSex());
        user.setRemark(dto.getRemark());

        if (!checkPhoneUnique(user)) {
            throw new BusinessException("修改个人信息失败，手机号码已存在");
        }
        if (!checkEmailUnique(user)) {
            throw new BusinessException("修改个人信息失败，邮箱账号已存在");
        }
        return updateById(user);
    }

    @Override
    public boolean updateUserAvatar(String userName, String avatar) {
        SysUser user = selectUserByUserName(userName);
        if (user != null) {
            user.setAvatar(avatar);
            return updateById(user);
        }
        return false;
    }

    @Override
    public boolean resetPwd(SysUser user) {
        // 如果用户对象中已经设置了密码，则使用BCrypt加密
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            String encryptedPassword = BCrypt.hashpw(user.getPassword());
            user.setPassword(encryptedPassword);
        }
        return updateById(user);
    }

    @Override
    public boolean resetPwd(SysUserStatusDTO dto) {
        SysUser user = new SysUser();
        user.setUserId(dto.getUserId());
        user.setPassword(dto.getPassword());
        checkUserAllowed(user);
        checkUserDataScope(user.getUserId());
        // 使用BCrypt加密密码
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            String encryptedPassword = BCrypt.hashpw(user.getPassword());
            user.setPassword(encryptedPassword);
        }
        return updateById(user);
    }

    @Override
    public boolean resetUserPwd(ResetPasswordDTO dto) {
        long usrId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(usrId);
        if (user != null) {
            // 验证旧密码
            if (!BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
                throw new BusinessException(BusinessExceptionEnum.FAIL,"密码错误");
            }
            if (BCrypt.checkpw(dto.getNewPassword(), user.getPassword())) {
                throw new BusinessException(BusinessExceptionEnum.FAIL,"新密码不能与旧密码相同");
            }
            // 使用BCrypt加密新密码
            String encryptedPassword = BCrypt.hashpw(dto.getNewPassword());
            user.setPassword(encryptedPassword);
            return updateById(user);
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUserById(Long userId) {
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getUserId, userId);
        userRoleMapper.delete(queryWrapper);

        // Get the existing user first
        SysUser user = getById(userId);
        if (user == null) {
            return false;
        }
        return removeById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUserByIds(List<Long> userIds) {
        for (Long userId : userIds) {
            deleteUserById(userId);
        }
        return true;
    }

    @Override
    public String importUser(List<SysUser> userList, Boolean isUpdateSupport, String operName) {
        return "导入成功";
    }

    @Override
    public SysUser findByPhone(String phone, Long tenantId) {
        return null;
    }

    @Override
    public SysUser registerByPhone(String phone, Long tenantId) {
        return null;
    }


}
