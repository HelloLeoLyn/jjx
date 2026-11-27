package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesSampleTransfer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SalesSampleTransferMapper extends BaseMapper<SalesSampleTransfer> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM sales_sample_transfer WHERE order_id=#{orderId} AND source_round_id=#{roundId} FOR UPDATE")
    SalesSampleTransfer lockByRound(@org.apache.ibatis.annotations.Param("orderId") Long orderId,
                                   @org.apache.ibatis.annotations.Param("roundId") Long roundId);
}
