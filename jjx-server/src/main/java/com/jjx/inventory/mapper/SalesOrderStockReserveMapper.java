package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.SalesOrderStockReserve;
import org.apache.ibatis.annotations.Mapper;

/**
 * 销售订单成品库存预留 Mapper
 */
@Mapper
public interface SalesOrderStockReserveMapper extends BaseMapper<SalesOrderStockReserve> {
}
