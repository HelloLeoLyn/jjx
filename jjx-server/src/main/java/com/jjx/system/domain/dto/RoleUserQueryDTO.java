package com.jjx.system.domain.dto;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色用户查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class RoleUserQueryDTO extends PageQuery {
    /** 角色ID */
    private Long roleId;

    /** 用户名称 */
    private String userName;

    /** 角色名称 */
    private String roleName;

    /** 手机号码 */
    private String phone;

    /** 状态（0正常 1停用） */
    private String status;
}
