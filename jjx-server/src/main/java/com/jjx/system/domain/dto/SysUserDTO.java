package com.jjx.system.domain.dto;


import com.jjx.system.enums.UserType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 系统用户DTO
 *
 * @author system
 */
@Getter
@Setter
public class SysUserDTO extends SecurityUserDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 30, message = "用户名长度必须在2-30位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String userName;

    /**
     * 昵称
     */
    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 30, message = "昵称长度必须在2-30位之间")
    private String nickName;

    /**
     * 用户类型
     */
//    @NotBlank(message = "用户类型不能为空")
//    @Pattern(regexp = "^(00|99)$", message = "用户类型必须是SYSTEM或NORMAL")
    private UserType userType;

    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过50位")
    private String email;

    /**
     * 手机号码
     */
    @NotBlank(message = "手机号码不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    @Size(min = 11, max = 11, message = "手机号码必须为11位")
    private String phone;

    /**
     * 性别（0：男 1：女 2：未知）
     */
    @Pattern(regexp = "^([012])$", message = "性别参数不正确")
    private String sex;

    /**
     * 头像
     */
    @Size(max = 255, message = "头像路径长度不能超过255位")
    private String avatar;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500位")
    private String remark;

    /**
     * 状态（0：正常 1：停用）
     */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态参数不正确（只能为0或1）")
    @Max(value = 1, message = "状态参数不正确（只能为0或1）")
    private Integer status;

    /**
     * 角色ID列表
     */
    private List<@NotNull Long> roleIds;

    /**
     * 权限列表
     */
    private List<String> permissions;




    @Override
    public String toString() {
        return "SysUserDTO{" +
                "deptId=" + deptId +
                ", userName='" + userName + '\'' +
                ", nickName='" + nickName + '\'' +
                ", userType='" + userType + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", sex='" + sex + '\'' +
                ", avatar='" + avatar + '\'' +
                ", remark='" + remark + '\'' +
                ", status='" + status + '\'' +
                ", roleIds=" + roleIds +
                ", permissions=" + permissions +
                '}';
    }
}
