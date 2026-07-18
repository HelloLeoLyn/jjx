package com.jjx.system.utils;

import com.jjx.system.domain.vo.AsyncRouteConfigVO;
import com.jjx.system.domain.vo.MetaVO;
import com.jjx.system.domain.vo.SysMenuVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 路由转换工具类
 *
 * @author system
 */
@Component
public class RouterHelper {

    /**
     * 将菜单列表转换为路由配置
     *
     * @param menus 菜单列表
     * @return 路由配置列表
     */
    public static List<AsyncRouteConfigVO> buildRoutes(List<SysMenuVO> menus) {
        List<AsyncRouteConfigVO> routes = new ArrayList<>();

        for (SysMenuVO menu : menus) {
            // 过滤按钮
            if ("F".equals(menu.getMenuType())) {
                continue;
            }

            AsyncRouteConfigVO route = new AsyncRouteConfigVO();
            route.setPath(getRoutePath(menu));
            route.setName(menu.getRouteName());
            route.setComponent(getComponent(menu));
            route.setRedirect(menu.getRedirect());

            // 设置元信息
            MetaVO meta = new  MetaVO();
            meta.setTitle(menu.getMenuName());
            meta.setIcon(menu.getIcon());
            meta.setHidden("1".equals(menu.getVisible()));
            meta.setPermission(menu.getPerms());
            meta.setSort(menu.getOrderNum());
            meta.setKeepAlive("0".equals(menu.getIsCache()));
            route.setMeta(meta);

            // 递归处理子菜单
            if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
                route.setChildren(buildRoutes(menu.getChildren()));
            }

            routes.add(route);
        }
        return routes;
    }

    /**
     * 获取路由路径
     */
    private static String getRoutePath(SysMenuVO menu) {
        String path = menu.getPath();
        if (StringUtils.isEmpty(path)) {
            return "";
        }
        return path;
    }

    /**
     * 获取组件路径
     */
    private static String getComponent(SysMenuVO menu) {
        String component = menu.getComponent();
        if (StringUtils.isEmpty(component)) {
            return "";
        }
        return component;
    }

    /**
     * 构建菜单树
     */
    public static List<SysMenuVO> buildMenuTree(List<SysMenuVO> menus) {
        List<SysMenuVO> tree = new ArrayList<>();

        // 找出所有根节点
        List<SysMenuVO> rootMenus = menus.stream()
                .filter(menu -> menu.getParentId() == 0)
                .toList();

        for (SysMenuVO root : rootMenus) {
            buildChildren(root, menus);
            tree.add(root);
        }

        return tree;
    }

    /**
     * 构建子菜单
     */
    private static void buildChildren(SysMenuVO parent, List<SysMenuVO> allMenus) {
        List<SysMenuVO> children = allMenus.stream()
                .filter(menu -> menu.getParentId().equals(parent.getMenuId()))
                .toList();

        if (!children.isEmpty()) {
            parent.setChildren(children);
            for (SysMenuVO child : children) {
                buildChildren(child, allMenus);
            }
        }
    }
}
