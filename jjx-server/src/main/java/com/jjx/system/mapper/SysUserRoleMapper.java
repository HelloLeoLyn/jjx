package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.system.domain.dto.UserRoleQueryDTO;
import com.jjx.system.domain.entity.SysUserRole;
import com.jjx.system.domain.vo.UserRoleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户与角色关联表Mapper接口
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 分页查询用户角色（支持所有参数为空）
     * @param page 分页对象
     * @param query 查询参数
     * @return 分页结果
     */
    @Select("<script>" +
            "SELECT " +
            "   r.role_id, " +
            "   r.role_name, " +
            "   r.status as role_status, " +
            "   r.create_time, " +
            "   ur.user_id " +
            "FROM sys_role r " +
            "LEFT JOIN sys_user_role ur ON r.role_id = ur.role_id " +
            "   AND ur.user_id = #{query.userId} " +
            "   <if test='query.roleId != null'>" +
            "       AND ur.role_id = #{query.roleId} " +
            "   </if>" +
            "<where>" +
            "   <if test='query.roleName != null and query.roleName != \"\"'>" +
            "       AND r.role_name LIKE CONCAT('%', #{query.roleName}, '%')" +
            "   </if>" +
            "   <if test='query.roleStatus != null and query.roleStatus != \"\"'>" +
            "       AND r.status = #{query.roleStatus}" +
            "   </if>" +
            "   <if test='query.roleId != null'>" +
            "       AND r.role_id = #{query.roleId}" +
            "   </if>" +
            "</where>" +
            "ORDER BY r.create_time DESC" +
            "</script>")
    IPage<UserRoleVO> selectUserRoleVOPage(IPage<?> page, @Param("query") UserRoleQueryDTO query);
}
