package com.jjx.system.domain.dto;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserRoleQueryDTO extends PageQuery {
    private Long userId;
    private Long roleId;
    private String roleName;
    private String roleStatus;
}
