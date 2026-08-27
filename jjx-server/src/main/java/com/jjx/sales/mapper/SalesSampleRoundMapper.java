package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesSampleRound;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 样品打样轮次快照Mapper
 */
@Mapper
public interface SalesSampleRoundMapper extends BaseMapper<SalesSampleRound> {

    /**
     * 查询样品单所有轮次快照（轮次正序）
     */
    @Select("SELECT * FROM sales_sample_round WHERE order_id = #{orderId} ORDER BY round_no ASC")
    List<SalesSampleRound> selectByOrderId(@Param("orderId") Long orderId);
}
