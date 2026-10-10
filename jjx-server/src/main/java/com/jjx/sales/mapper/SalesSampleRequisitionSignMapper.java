package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesSampleRequisitionSign;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 样品需求单会签记录 Mapper（dev-20261010-028）
 */
@Mapper
public interface SalesSampleRequisitionSignMapper extends BaseMapper<SalesSampleRequisitionSign> {

    /** 查询某样品单的全部会签记录（按位次/轮次排序） */
    @Select("SELECT * FROM sales_sample_requisition_sign WHERE sample_order_id = #{sampleOrderId} "
            + "ORDER BY round_no ASC, id ASC")
    List<SalesSampleRequisitionSign> selectByOrderId(@Param("sampleOrderId") Long sampleOrderId);
}
