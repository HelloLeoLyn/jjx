package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.product.domain.entity.ProductInstance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品实例Mapper接口
 */
@Mapper
public interface ProductInstanceMapper extends BaseMapper<ProductInstance> {
}
