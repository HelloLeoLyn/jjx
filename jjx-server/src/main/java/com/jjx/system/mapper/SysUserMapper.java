package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.system.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户信息Mapper接口
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按角色标识前缀（role_key）查询用户列表（2026-08-11 销售负责人专用）
     * 不依赖角色ID，角色重建/变更不会失效
     */
    @Select("SELECT u.user_id, u.user_name, u.nick_name FROM sys_user u " +
            "JOIN sys_user_role ur ON u.user_id = ur.user_id " +
            "JOIN sys_role r ON ur.role_id = r.role_id " +
            "WHERE r.role_key LIKE CONCAT(#{roleKeyPrefix}, '%') " +
            "AND u.status = 0 AND (u.del_flag IS NULL OR u.del_flag = '0') " +
            "ORDER BY u.user_id")
    List<SysUser> selectUsersByRoleKeyPrefix(@Param("roleKeyPrefix") String roleKeyPrefix);
}
