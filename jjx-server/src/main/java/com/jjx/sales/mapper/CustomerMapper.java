package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesCustomer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;


/**
 * 客户管理Mapper接口
 * 提供客户数据的数据库操作
 */
@Mapper
public interface CustomerMapper extends BaseMapper<SalesCustomer> {

    /** 存量客户最大流水号（CUS + 5 位）；清库后兜底，避免新增从 1 撞存量（dev-20261010） */
    @Select("SELECT COALESCE(MAX(CAST(RIGHT(customer_code, 5) AS UNSIGNED)), 0) FROM sales_customer WHERE customer_code REGEXP '^CUS[0-9]{5}$'")
    Long selectMaxCodeSequence();
}
