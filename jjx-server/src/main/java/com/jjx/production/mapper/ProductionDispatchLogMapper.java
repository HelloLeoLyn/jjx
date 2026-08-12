package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionDispatchLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 派工操作流水 Mapper
 */
@Mapper
public interface ProductionDispatchLogMapper extends BaseMapper<ProductionDispatchLog> {
}
