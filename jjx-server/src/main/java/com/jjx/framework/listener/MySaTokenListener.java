// ==================== SaTokenListener.java ====================
package com.jjx.framework.listener;

import cn.dev33.satoken.listener.SaTokenListener;
import cn.dev33.satoken.stp.SaLoginModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 事件监听器
 * 可以监听登录、登出、踢人下线等事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MySaTokenListener implements SaTokenListener {
    
    /** 每次登录时触发 */
    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginModel loginModel) {
        log.info("用户登录: loginType={}, loginId={}, device={}", 
                 loginType, loginId, loginModel.getDevice());
        // 这里可以额外记录登录日志
    }
    
    /** 每次注销时触发 */
    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        log.info("用户登出: loginType={}, loginId={}", loginType, loginId);
    }
    
    /** 每次被踢下线时触发 */
    @Override
    public void doKickout(String loginType, Object loginId, String tokenValue) {
        log.info("用户被踢下线: loginType={}, loginId={}", loginType, loginId);
    }
    
    /** 每次被顶下线时触发 */
    @Override
    public void doReplaced(String loginType, Object loginId, String tokenValue) {
        log.info("用户被顶下线: loginType={}, loginId={}", loginType, loginId);
    }
    
    /** 每次被封禁时触发 */
    @Override
    public void doDisable(String loginType, Object loginId, String service, int level, long disableTime) {
        log.info("用户被封禁: loginType={}, loginId={}, service={}, level={}", 
                 loginType, loginId, service, level);
    }
    
    /** 每次被解封时触发 */
    @Override
    public void doUntieDisable(String loginType, Object loginId, String service) {
        log.info("用户解封: loginType={}, loginId={}, service={}", loginType, loginId, service);
    }
    
    /** 每次二级认证打开时触发 */
    @Override
    public void doOpenSafe(String loginType, String tokenValue, String service, long safeTime) {
        log.info("二级认证打开: loginType={}, service={}", loginType, service);
    }
    
    /** 每次二级认证关闭时触发 */
    @Override
    public void doCloseSafe(String loginType, String tokenValue, String service) {
        log.info("二级认证关闭: loginType={}, service={}", loginType, service);
    }
    
    /** 每次创建 Session 时触发 */
    @Override
    public void doCreateSession(String id) {
        // 不记录，避免日志过多
    }
    
    /** 每次注销 Session 时触发 */
    @Override
    public void doLogoutSession(String id) {
        // 不记录
    }
    
    /** 每次 Token 续期时触发 */
    @Override
    public void doRenewTimeout(String tokenValue, Object loginId, long timeout) {
        // 不记录
    }
}