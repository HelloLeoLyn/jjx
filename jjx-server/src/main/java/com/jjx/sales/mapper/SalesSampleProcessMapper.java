package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesSampleProcess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 打样工序历史记录Mapper
 */
@Mapper
public interface SalesSampleProcessMapper extends BaseMapper<SalesSampleProcess> {

    /**
     * 查询样品单所有工序记录（按时间正序）
     */
    @Select("SELECT * FROM sales_sample_process WHERE order_id = #{orderId} ORDER BY process_id ASC")
    List<SalesSampleProcess> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 查询样品单指定轮次工序记录（DEV-500 按轮次展示）
     */
    @Select("SELECT * FROM sales_sample_process WHERE order_id = #{orderId} AND round_no = #{roundNo} ORDER BY process_id ASC")
    List<SalesSampleProcess> selectByOrderAndRound(@Param("orderId") Long orderId, @Param("roundNo") Integer roundNo);
}
