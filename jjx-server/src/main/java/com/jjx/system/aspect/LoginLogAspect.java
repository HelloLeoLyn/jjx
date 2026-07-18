package com.jjx.system.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.core.result.Result;
import com.jjx.system.annotation.LoginLog;
import com.jjx.system.domain.dto.LoginDTO;
import com.jjx.system.domain.dto.SmsLoginDTO;
import com.jjx.system.domain.entity.SysLoginLog;
import com.jjx.system.domain.vo.LoginVO;
import com.jjx.system.service.LogSaveService;
import com.jjx.system.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LoginLogAspect {

    private final LogSaveService logSaveService;

    @Around("@annotation(loginLog)")
    public Object around(ProceedingJoinPoint point, LoginLog loginLog) throws Throwable {
        SysLoginLog logEntity = new SysLoginLog();
        logEntity.setLoginTime(LocalDateTime.now());
        logEntity.setLoginType(loginLog.loginType());

        fillRequestInfo(logEntity);
        extractUsername(point, logEntity);

        try {
            Result<LoginVO> result = (Result<LoginVO>)point.proceed();
            if (loginLog.recordOnSuccess()) {
                logEntity.setStatus(1);
                logEntity.setUserId(result.getData().getUserId());
                // 从 Sa-Token 获取登录后的用户信息
                extractUserFromSaToken(logEntity);
                logSaveService.saveLoginLog(logEntity);
            }
            return result;

        } catch (Exception e) {
            if (loginLog.recordOnFailure()) {
                logEntity.setStatus(0);
                logEntity.setFailReason(truncate(e.getMessage(), 200));
                logSaveService.saveLoginLog(logEntity);
            }
            throw e;
        }
    }

    private static void fillRequestInfo(SysLoginLog logEntity) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            logEntity.setLoginIp(IpUtils.getClientIp(request));
            logEntity.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
            logEntity.setLoginLocation("未知");
        }
    }

    private static void extractUsername(ProceedingJoinPoint point, SysLoginLog logEntity) {
        Object[] args = point.getArgs();
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }

            // 从 LoginDTO 获取用户名
            if (arg instanceof LoginDTO ) {
                try {
                    String username = (String) arg.getClass().getMethod("getUsername").invoke(arg);
                    logEntity.setUsername(username);
                } catch (Exception exception) {
                    log.info(exception.getMessage());
                }
            }

            // 从 SmsLoginDTO 获取手机号
            if (arg instanceof SmsLoginDTO) {
                try {
                    String phone = (String) arg.getClass().getMethod("getPhone").invoke(arg);
                    logEntity.setUsername(phone);
                } catch (Exception exception) {
                    log.info(exception.getMessage());
                }
            }
        }
    }

    /**
     * 从 Sa-Token 获取登录用户信息
     */
    private static void extractUserFromSaToken(SysLoginLog logEntity) {
        try {
            if (StpUtil.isLogin()) {
                LoginVO loginUser = (LoginVO) StpUtil.getSession().get("loginUser");
                if (loginUser != null) {
                    logEntity.setUserId(loginUser.getUserId());
                    logEntity.setTenantId(loginUser.getUserInfo().getTenantId());
                    // 如果之前没有用户名，补充用户名
                    if (logEntity.getUsername() == null) {
                        logEntity.setUsername(loginUser.getUserInfo().getUserName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从 Sa-Token 获取用户信息失败", e);
        }
    }

    private static String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }
}
