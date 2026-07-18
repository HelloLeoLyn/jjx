package com.jjx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.system.domain.dto.UserRoleQueryDTO;
import com.jjx.system.domain.entity.SysUserRole;
import com.jjx.system.domain.vo.UserRoleVO;
import com.jjx.system.mapper.SysUserRoleMapper;
import com.jjx.system.service.ISysUserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements ISysUserRoleService {
    @Override
    public PageResult<SysUserRole> selectUserRoleListByUserId(Long userId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        Page<SysUserRole> page = new Page<>(pageNum, pageSize);
        queryWrapper.eq(SysUserRole::getUserId, userId);
        Page<SysUserRole> sysUserRolePage = getBaseMapper().selectPage(page, queryWrapper);
        return PageResult.build(sysUserRolePage.getRecords(), sysUserRolePage.getTotal());
    }

    @Override
    public PageResult<SysUserRole> selectUserRoleListByRoleId(Long roleId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        Page<SysUserRole> page = new Page<>(pageNum, pageSize);
        queryWrapper.eq(SysUserRole::getRoleId, roleId);
        Page<SysUserRole> sysUserRolePage = getBaseMapper().selectPage(page, queryWrapper);
        return PageResult.build(sysUserRolePage.getRecords(), sysUserRolePage.getTotal());
    }

    @Override
    public PageResult<UserRoleVO> selectUserRolePage(UserRoleQueryDTO userRoleQuery) {
        Page<UserRoleVO> page = new Page<>(userRoleQuery.getPageNum(), userRoleQuery.getPageSize());
        IPage<UserRoleVO> userRoleVOIPage = getBaseMapper().selectUserRoleVOPage(page, userRoleQuery);
        return PageResult.build(userRoleVOIPage.getRecords(), userRoleVOIPage.getTotal());
    }

    @Override
    public List<SysUserRole> selectByUserId(Long usrId) {
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getUserId, usrId);
        return getBaseMapper().selectList(queryWrapper);
    }
}
