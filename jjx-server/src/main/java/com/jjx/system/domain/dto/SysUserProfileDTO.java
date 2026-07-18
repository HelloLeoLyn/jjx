package com.jjx.system.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户个人信息修改DTO
 */
@Getter
@Setter
public class SysUserProfileDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 昵称
     */
    @Size(min = 2, max = 30, message = "昵称长度必须在2-30位之间")
    private String nickName;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过50位")
    private String email;

    /**
     * 手机号码
     */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    @Size(min = 11, max = 11, message = "手机号码必须为11位")
    private String phone;

    /**
     * 性别（0：男 1：女 2：未知）
     */
    @Pattern(regexp = "^([012])$", message = "性别参数不正确")
    private String sex;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500位")
    private String remark;
}
