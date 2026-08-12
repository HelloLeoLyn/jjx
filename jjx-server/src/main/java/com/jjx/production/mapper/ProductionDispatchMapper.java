package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionDispatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工序派工单 Mapper
 */
@Mapper
public interface ProductionDispatchMapper extends BaseMapper<ProductionDispatch> {
}
