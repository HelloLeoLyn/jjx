package com.jjx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.system.domain.dto.UserRoleQueryDTO;
import com.jjx.system.domain.entity.SysUserRole;
import com.jjx.system.domain.vo.UserRoleVO;

import java.util.List;

public interface ISysUserRoleService  extends IService<SysUserRole> {
    /**
     * 根据条件分页查询角色数据
     *
     * @param userId 用户ID
     * @return 用户角色数据集合信息
     */
    PageResult<SysUserRole> selectUserRoleListByUserId(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 根据条件分页查询角色数据
     *
     * @param roleId 角色ID
     * @return 用户角色数据集合信息
     */
    PageResult<SysUserRole> selectUserRoleListByRoleId(Long roleId, Integer pageNum, Integer pageSize);


    PageResult<UserRoleVO> selectUserRolePage(UserRoleQueryDTO userRoleQuery);

    List<SysUserRole> selectByUserId(Long usrId);
}
