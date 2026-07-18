package com.jjx.system.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户状态修改DTO
 */
@Getter
@Setter
public class SysUserStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 状态（0：正常 1：停用）
     */
    @Min(value = 0, message = "状态参数不正确（只能为0或1）")
    @Max(value = 1, message = "状态参数不正确（只能为0或1）")
    private Integer status;

    /**
     * 密码
     */
    private String password;
}
