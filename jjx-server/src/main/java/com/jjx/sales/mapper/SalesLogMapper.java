package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SalesLogMapper extends BaseMapper<SalesLog> {
    
    /**
     * 查询订单的最新操作日志
     */
    @Select("SELECT * FROM sales_log WHERE order_id = #{orderId} ORDER BY operation_time DESC LIMIT 1")
    SalesLog selectLatestByOrderId(@Param("orderId") Long orderId);
    
    /**
     * 查询订单的指定类型操作日志
     */
    @Select("SELECT * FROM sales_log WHERE order_id = #{orderId} AND operation_type = #{operationType} ORDER BY operation_time DESC")
    List<SalesLog> selectByOrderIdAndType(@Param("orderId") Long orderId, @Param("operationType") String operationType);
    
    /**
     * 统计指定时间范围内的操作次数
     */
    @Select("SELECT COUNT(*) FROM sales_log WHERE operator_id = #{operatorId} " +
            "AND operation_time BETWEEN #{startTime} AND #{endTime}")
    Long countByOperatorAndTime(@Param("operatorId") Long operatorId,
                                 @Param("startTime") String startTime,
                                 @Param("endTime") String endTime);
}