package com.jjx.system.domain.vo;

import lombok.Data;

@Data
public class UserRoleVO {
    private Long userId;
    /** 角色ID */
    private Long roleId;

    /** 角色名称 */
    private String roleName;

    /** 角色状态（0正常 1停用） */
    private String roleStatus;
}
