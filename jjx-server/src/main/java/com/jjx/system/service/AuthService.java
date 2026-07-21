package com.jjx.system.service;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.converter.SysUserConverter;
import com.jjx.system.domain.dto.LoginDTO;
import com.jjx.system.domain.dto.SmsLoginDTO;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.domain.vo.LoginUser;
import com.jjx.system.domain.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService{
    private final ISysUserService userService;
    private final ISysRoleService roleService;
    private final ISysMenuService menuService;
    private final SmsService smsService;
    private final SysUserConverter userConverter;
    public LoginVO login(LoginDTO dto) {

        log.info("=== 登录接口开始 ===");

        // 根据用户名查询用户
        SysUser user = userService.selectUserByUserName(dto.getUsername());
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 校验信息
        validate(dto, user);

        // 先 Sa-Token 登录（生成 token）
        StpUtil.login(user.getUserId());

        // 构建登录响应（此时 token 已存在）
        LoginVO loginVO = buildLoginVO(user);
        loginVO.setToken(StpUtil.getTokenValue());

        // 设置 session
        SaLoginModel loginModel = new SaLoginModel()
                .setIsWriteHeader(false);
        StpUtil.getSession().set("loginVO", loginVO);
        StpUtil.getSession().setId(loginVO.getUserInfo().getUserName());

        log.info("登录成功: userId={}", user.getUserId());
        log.info("=== 登录接口结束 ===");
        return loginVO;
    }



    public LoginVO smsLogin(SmsLoginDTO dto) {
        // todo
        boolean valid = smsService.validateCode(dto.getPhone(), dto.getSmsCode());
        if (!valid) {
            throw new RuntimeException("短信验证码错误或已过期");
        }

        SysUser sysUser = userService.findByPhone(dto.getPhone(), dto.getTenantId());
        if (sysUser == null) {
            if (dto.getNeedBindAccount()) {
                sysUser = userService.registerByPhone(dto.getPhone(), dto.getTenantId());
            } else {
                throw new RuntimeException("手机号未注册");
            }
        }
        LoginVO loginVO = buildLoginVO(sysUser);

        doSaTokenLogin(loginVO);

        return loginVO;
    }


    private static void doSaTokenLogin(LoginVO loginVO) {
        SaLoginModel loginModel = new SaLoginModel()
                .setDevice("PC")
                .setIsLastingCookie(false)
                .setTimeout(60 * 60 * 24);
        StpUtil.login(loginVO.getUserId(), loginModel);
        StpUtil.getSession().set("loginVO", loginVO);
        StpUtil.getSession().setId(loginVO.getUserInfo().getUserName());
        log.info("登录成功: userId={}", loginVO.getUserId());
    }
    private static void validate(LoginDTO dto, SysUser user) {

        // 检查用户状态
        if (user.getStatus()==1) {
            throw new BusinessException("用户已被停用");
        }

        // 验证密码（使用BCrypt加密比对）
        String storedPasswordHash = user.getPassword();
        String inputPassword = dto.getPassword();

        // 检查密码哈希是否有效（应该是BCrypt格式）
        if (storedPasswordHash == null || storedPasswordHash.trim().isEmpty()) {
            throw new BusinessException("登录失败，请联系管理员");
        }

        // 使用BCrypt验证密码
        boolean passwordMatches = BCrypt.checkpw(inputPassword, storedPasswordHash);
        if (!passwordMatches) {
            throw new BusinessException("用户名或密码错误");
        }
    }
    private List<String> getRoles(Long userId) {
        return roleService.selectRoleNameByUsrId(userId);
    }

    private LoginVO buildLoginVO(SysUser user) {
        // 获取用户角色
        List<String> roles = getRoles(user.getUserId());

        // 获取用户权限
        Set<String> permissions = menuService.selectMenuPermsByUserId(user.getUserId());

        LoginUser userInfo = userConverter.toVO(user);
        LoginVO vo = new LoginVO();
        vo.setIsLogin(true);
        vo.setUserId(user.getUserId());
        vo.setUserInfo(userInfo);
        vo.setToken(StpUtil.getTokenValue());
        vo.setRoles(roles);
        vo.setPermissions(permissions);
        vo.setLoginTime(System.currentTimeMillis());
        return vo;
    }
}
