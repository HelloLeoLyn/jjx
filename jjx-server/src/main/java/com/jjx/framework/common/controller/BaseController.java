package com.jjx.framework.common.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import com.jjx.common.core.page.PageQuery;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Slf4j
public class BaseController{
    protected BaseController() {
        log.debug("BaseController initialized for: {}", getClass().getSimpleName());
    }


    /** 分页参数：当前页码 */
    private static final String PAGE_NUM = "pageNum";

    /** 分页参数：每页大小 */
    private static final String PAGE_SIZE = "pageSize";

    /** 默认每页大小 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 最大每页大小 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 存储分页信息的ThreadLocal */
    private static final ThreadLocal<PageQuery> PAGE_QUERY_HOLDER = new ThreadLocal<>();

    /**
     * 获取当前请求对象
     */
    protected static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * 获取请求参数
     */
    protected static String getParameter(String name) {
        HttpServletRequest request = getRequest();
        if (request != null) {
            return request.getParameter(name);
        }
        return null;
    }


    /**
     * 获取当前页码
     */
    protected static Integer getPageNum() {
        String pageNumStr = getParameter(PAGE_NUM);
        if (CharSequenceUtil.isNotBlank(pageNumStr)) {
            Integer pageNum = Convert.toInt(pageNumStr, DEFAULT_PAGE_NUM);
            return pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        }
        return DEFAULT_PAGE_NUM;
    }

    /**
     * 获取每页大小
     */
    protected static Integer getPageSize() {
        String pageSizeStr = getParameter(PAGE_SIZE);
        if (CharSequenceUtil.isNotBlank(pageSizeStr)) {
            Integer pageSize = Convert.toInt(pageSizeStr, DEFAULT_PAGE_SIZE);
            if (pageSize < 1) {
                return DEFAULT_PAGE_SIZE;
            }
            return pageSize > MAX_PAGE_SIZE ? MAX_PAGE_SIZE : pageSize;
        }
        return DEFAULT_PAGE_SIZE;
    }

    /**
     * 获取分页查询参数
     */
    protected static PageQuery getPageQuery() {
        PageQuery pageQuery = PAGE_QUERY_HOLDER.get();
        if (pageQuery == null) {
            pageQuery = new PageQuery();
            pageQuery.setPageNum(getPageNum());
            pageQuery.setPageSize(getPageSize());
        }
        return pageQuery;
    }

    /**
     * 清理分页ThreadLocal
     */
    protected static void clearPage() {
        PAGE_QUERY_HOLDER.remove();
    }



    /**
     * 获取分页数据
     *
     * @param list 数据列表
     * @param total 总记录数
     * @param <T> 数据类型
     * @return 分页数据信息
     */
    protected static <T> PageResult<T> getDataTable(List<T> list, long total) {
        try {
            return PageResult.build(list, total);
        } finally {
            clearPage();
        }
    }

    /**
     * 响应返回结果
     *
     * @param rows 影响行数
     * @return 操作结果
     */
    protected static Result<Void> toAjax(int rows) {
        return rows > 0 ? Result.success() : Result.error();
    }

    /**
     * 响应返回结果
     *
     * @param result 结果
     * @return 操作结果
     */
    protected static Result<Void> toAjax(boolean result) {
        return result ? Result.success() : Result.error();
    }

    /**
     * 获取当前登录用户名
     * 子类可以重写此方法以提供具体的实现
     */
    protected static String getUsername() {
        // 默认实现，子类应该重写此方法
        // 在实际项目中，可以从Session、Token或SecurityContext中获取
        return StpUtil.getSession().getId();
    }

    /**
     * 获取当前登录用户ID
     * 子类可以重写此方法以提供具体的实现
     */
    protected static Long getUserId() {
        // 默认实现，子类应该重写此方法
        // 在实际项目中，可以从Session、Token或SecurityContext中获取
        return StpUtil.getLoginIdAsLong();
    }

}
