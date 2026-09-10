package com.jjx.hr.domain.vo;

import lombok.Data;

/** 生成账号时可分配的角色选项。 */
@Data
public class HrRoleOptionVO {

    private Long roleId;

    private String roleName;

    private String roleKey;
}
