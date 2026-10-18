package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesSampleOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SalesSampleOrderMapper extends BaseMapper<SalesSampleOrder> {
    /** 写事务使用当前读，避免等待来源订单锁前建立的快照漏掉新建样品单。 */
    @Select("<script>SELECT o.order_id, o.order_no, s.sample_status FROM sales_order o " +
            "JOIN sales_sample_order s ON s.order_id=o.order_id " +
            "WHERE o.deleted=0 AND s.deleted=0 AND o.order_type=#{sampleType} AND o.customer_id=#{customerId} " +
            "AND s.source_type=#{sourceType} AND s.source_no=#{sourceNo} " +
            "AND (s.product_id=#{productId} OR UPPER(TRIM(s.product_code))=UPPER(TRIM(#{productCode}))) " +
            "AND s.sample_status IN <foreach collection='statuses' item='status' open='(' separator=',' close=')'>#{status}</foreach> " +
            "<if test='lock'>FOR UPDATE</if></script>")
    List<com.jjx.sales.domain.vo.SalesOrderSampleOptionsVO.ActiveSample> selectActiveOrderSamples(
            @Param("sourceType") String sourceType, @Param("sourceNo") String sourceNo,
            @Param("customerId") Long customerId, @Param("productId") Long productId,
            @Param("productCode") String productCode, @Param("sampleType") Integer sampleType,
            @Param("statuses") List<Integer> statuses, @Param("lock") boolean lock);

    @Select("SELECT * FROM sales_sample_order WHERE order_id = #{orderId} AND deleted = 0 LIMIT 1")
    SalesSampleOrder selectByOrderId(Long orderId);
}
