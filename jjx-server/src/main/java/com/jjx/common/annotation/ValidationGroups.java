package com.jjx.common.annotation;


import jakarta.validation.groups.Default;

/**
 * 校验分组
 */
public interface ValidationGroups {
    
    /**
     * 新增分组
     */
    interface Add extends Default {}
    
    /**
     * 修改分组
     */
    interface Update extends Default {}
    
    /**
     * 删除分组
     */
    interface Delete {}
    
    /**
     * 审核分组
     */
    interface Review {}
    
    /**
     * 查询分组
     */
    interface Query {}
    
    /**
     * 导出分组
     */
    interface Export {}
}