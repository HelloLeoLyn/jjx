package com.jjx.hr.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 由员工档案生成系统账号的入参（人事管理 P0）。
 *
 * <p>一键生成时全部可空：登录名默认取员工工号、初始密码取 sys_config
 * {@code hr.user.default_password}（缺省 123456）、角色可不选（后续在系统管理→用户管理分配）。</p>
 */
@Data
public class HrCreateUserDTO {

    /** 登录名；留空取员工工号 */
    private String userName;

    /** 初始密码；留空取配置 hr.user.default_password */
    private String password;

    /** 角色ID列表（可空） */
    private List<Long> roleIds;

    /** 备注 */
    private String remark;
}
