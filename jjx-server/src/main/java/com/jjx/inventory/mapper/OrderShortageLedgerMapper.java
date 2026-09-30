package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.OrderShortageLedger;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单缺料欠交台账 Mapper（齐套 P4d，dev-20260930-026）
 */
@Mapper
public interface OrderShortageLedgerMapper extends BaseMapper<OrderShortageLedger> {
}
