package com.jjx.system.domain.dto;

import lombok.Data;

@Data
public class SysMenuQueryDTO {
        /** 菜单名称 */
        private String menuName;

        /** 父菜单ID */
        private Long parentId;

        /** 是否为外链（0是 1否） */
        private String isFrame;

        /** 是否缓存（0缓存 1不缓存） */
        private String isCache;

        /** 类型（M目录 C菜单 F按钮） */
        private String menuType;

        /** 显示状态（0显示 1隐藏） */
        private String visible;

        /** 菜单状态（0正常 1停用） */
        private String status;

        /** 权限字符串 */
        private String perms;

        /** 备注 */
        private String remark;

        /** 路由名称 */
        private String routeName;


    }

