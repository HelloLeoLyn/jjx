package com.jjx.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
/**
 * 安全用户信息（密码相关）
 *
 * @author system
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecurityUserDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 密码
     */
    @Size(min = 6, max = 20, message = "密码长度必须在6-20位之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{6,20}$",
            message = "密码必须包含大小写字母和数字",groups = {AddGroup.class,ResetPwdGroup.class})
    private String password;

    /**
     * 新密码
     */
    @Size(min = 6, max = 20, message = "新密码长度必须在6-20位之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{6,20}$",
            message = "新密码必须包含大小写字母和数字", groups = {ResetPwdGroup.class})
    private String newPassword;


    // 分组校验接口
    public interface AddGroup {}
    public interface UpdateGroup {}
    public interface ResetPwdGroup {}
}
