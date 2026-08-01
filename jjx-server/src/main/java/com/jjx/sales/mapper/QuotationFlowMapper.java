package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesQuotationFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 报价单状态流转记录Mapper
 */
@Mapper
public interface QuotationFlowMapper extends BaseMapper<SalesQuotationFlow> {

    /**
     * 根据报价单ID查询流转记录（时间倒序）
     */
    @Select("SELECT * FROM sales_quotation_flow WHERE quotation_id = #{quotationId} ORDER BY flow_id DESC")
    List<SalesQuotationFlow> selectByQuotationId(@Param("quotationId") Long quotationId);
}
