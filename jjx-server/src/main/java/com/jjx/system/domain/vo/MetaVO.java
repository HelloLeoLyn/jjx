package com.jjx.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


/**
 * 路由元信息VO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class MetaVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 菜单标题
     */
    private String title;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 是否隐藏
     */
    private Boolean hidden;

    /**
     * 权限标识
     */
    private String permission;

    /**
     * 排序值
     */
    private Integer sort;

    /**
     * 是否缓存
     */
    private Boolean keepAlive;

    /**
     * 是否固定在标签栏
     */
    private Boolean affix;

    // ========== Getter and Setter ==========

    @Override
    public String toString() {
        return "MetaVO{" +
                "title='" + title + '\'' +
                ", icon='" + icon + '\'' +
                ", hidden=" + hidden +
                ", permission='" + permission + '\'' +
                ", sort=" + sort +
                ", keepAlive=" + keepAlive +
                ", affix=" + affix +
                '}';
    }
}
