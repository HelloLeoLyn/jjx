package com.jjx.system.domain.vo;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 异步路由配置VO
 *
 * @author system
 * @date 2026-04-13
 */
@Setter
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AsyncRouteConfigVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 路由地址
     */
    private String path;

    /**
     * 路由名称（用于keep-alive和权限判断）
     */
    private String name;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 重定向地址
     */
    private String redirect;

    /**
     * 子路由
     */
    private List<AsyncRouteConfigVO> children;

    /**
     * 路由元信息
     */
    private MetaVO meta;

    // ========== Getter and Setter ==========


    @Override
    public String toString() {
        return "AsyncRouteConfigVO{" +
                "path='" + path + '\'' +
                ", name='" + name + '\'' +
                ", component='" + component + '\'' +
                ", redirect='" + redirect + '\'' +
                ", children=" + children +
                ", meta=" + meta +
                '}';
    }
}
