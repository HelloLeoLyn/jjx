package com.jjx.common.constant;

/**
 * 通用常量
 */
public class Constants {

    /** UTF-8 字符集 */
    public static final String UTF8 = "UTF-8";

    /** 通用成功标识 */
    public static final String SUCCESS = "0";

    /** 通用失败标识 */
    public static final String FAIL = "1";

    /** 登录成功 */
    public static final String LOGIN_SUCCESS = "Success";

    /** 注销 */
    public static final String LOGOUT = "Logout";

    /** 登录失败 */
    public static final String LOGIN_FAIL = "Error";

    /** 验证码有效期（分钟） */
    public static final Integer CAPTCHA_EXPIRATION = 2;

    /** 令牌 */
    public static final String TOKEN = "token";

    /** 令牌前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 令牌前缀 */
    public static final String LOGIN_USER_KEY = "login_user_key";

    /** 用户ID */
    public static final String JWT_USERID = "userid";

    /** 用户名称 */
    public static final String JWT_USERNAME = "sub";

    /** 用户头像 */
    public static final String JWT_AVATAR = "avatar";

    /** 创建时间 */
    public static final String JWT_CREATED = "created";

    /** 用户权限 */
    public static final String JWT_AUTHORITIES = "authorities";

    /** http请求 */
    public static final String HTTP = "http://";

    /** https请求 */
    public static final String HTTPS = "https://";

    /** 自动识别json对象白名单配置（仅允许解析的包名） */
    public static final String[] JSON_WHITELIST_STR = { "org.springframework", "com.jjx" };

    /** 定时任务白名单配置（仅允许访问的包名） */
    public static final String[] JOB_WHITELIST_STR = { "com.jjx" };

}
