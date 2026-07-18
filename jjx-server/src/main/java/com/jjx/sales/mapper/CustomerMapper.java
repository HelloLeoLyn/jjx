package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesCustomer;
import org.apache.ibatis.annotations.Mapper;


/**
 * 客户管理Mapper接口
 * 提供客户数据的数据库操作
 */
@Mapper
public interface CustomerMapper extends BaseMapper<SalesCustomer> {
}
