package com.jjx.system.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class SysUserVO {
    private Long userId;

    /** 部门ID */
    private Long deptId;

    /** 部门名称（2026-08-19：原缺失导致前端只能显示“部门{id}”） */
    private String deptName;

    /** 用户账号 */
    private String userName;

    /** 用户昵称 */
    private String nickName;

    /** 用户类型（00系统用户） */
    private String userType;

    /** 用户邮箱 */
    private String email;

    /** 手机号码 */
    private String phone;

    /** 用户性别（0男 1女 2未知） */
    private String sex;

    /** 头像地址 */
    private String avatar;

    /** 帐号状态（0正常 1停用） */
    private Integer status;

    private List<Long> roleIds;
}
