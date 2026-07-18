package com.jjx.common.constant;

/**
 * 用户常量
 */
public class UserConstants {

    /** 系统用户类型 */
    public static final String USER_TYPE_SYS = "00";

    /** 管理员用户类型 */
    public static final String USER_TYPE_ADMIN = "01";

    /** 普通用户类型 */
    public static final String USER_TYPE_NORMAL = "02";

    /** 默认密码 */
    public static final String DEFAULT_PASSWORD = "123456";

    /** 用户名长度限制 */
    public static final int USERNAME_MIN_LENGTH = 2;
    public static final int USERNAME_MAX_LENGTH = 20;

    /** 密码长度限制 */
    public static final int PASSWORD_MIN_LENGTH = 5;
    public static final int PASSWORD_MAX_LENGTH = 20;

    /** 手机号码长度限制 */
    public static final int PHONE_NUMBER_LENGTH = 11;

    /** 邮箱长度限制 */
    public static final int EMAIL_MAX_LENGTH = 50;

    /** 部门名称长度限制 */
    public static final int DEPT_NAME_MAX_LENGTH = 30;

    /** 角色名称长度限制 */
    public static final int ROLE_NAME_MAX_LENGTH = 30;

    /** 菜单名称长度限制 */
    public static final int MENU_NAME_MAX_LENGTH = 50;

    /** 字典名称长度限制 */
    public static final int DICT_NAME_MAX_LENGTH = 100;

    /** 字典类型长度限制 */
    public static final int DICT_TYPE_MAX_LENGTH = 100;

    /** 参数名称长度限制 */
    public static final int CONFIG_NAME_MAX_LENGTH = 100;

    /** 参数键名长度限制 */
    public static final int CONFIG_KEY_MAX_LENGTH = 100;

    /** 参数键值长度限制 */
    public static final int CONFIG_VALUE_MAX_LENGTH = 500;

    /** 备注长度限制 */
    public static final int REMARK_MAX_LENGTH = 500;

    /** 用户状态：正常 */
    public static final String USER_STATUS_NORMAL = "0";

    /** 用户状态：停用 */
    public static final String USER_STATUS_DISABLE = "1";

    /** 用户状态：删除 */
    public static final String USER_STATUS_DELETED = "2";

    /** 部门状态：正常 */
    public static final String DEPT_STATUS_NORMAL = "0";

    /** 部门状态：停用 */
    public static final String DEPT_STATUS_DISABLE = "1";

    /** 角色状态：正常 */
    public static final String ROLE_STATUS_NORMAL = "0";

    /** 角色状态：停用 */
    public static final String ROLE_STATUS_DISABLE = "1";

    /** 菜单状态：正常 */
    public static final String MENU_STATUS_NORMAL = "0";

    /** 菜单状态：停用 */
    public static final String MENU_STATUS_DISABLE = "1";

    /** 字典状态：正常 */
    public static final String DICT_STATUS_NORMAL = "0";

    /** 字典状态：停用 */
    public static final String DICT_STATUS_DISABLE = "1";

    /** 菜单类型：目录 */
    public static final String MENU_TYPE_DIR = "M";

    /** 菜单类型：菜单 */
    public static final String MENU_TYPE_MENU = "C";

    /** 菜单类型：按钮 */
    public static final String MENU_TYPE_BUTTON = "F";

    /** 是否菜单外链：是 */
    public static final String MENU_FRAME_YES = "0";

    /** 是否菜单外链：否 */
    public static final String MENU_FRAME_NO = "1";

    /** 菜单是否缓存：是 */
    public static final String MENU_CACHE_YES = "0";

    /** 菜单是否缓存：否 */
    public static final String MENU_CACHE_NO = "1";

    /** 菜单是否显示：显示 */
    public static final String MENU_VISIBLE_SHOW = "0";

    /** 菜单是否显示：隐藏 */
    public static final String MENU_VISIBLE_HIDE = "1";
}
