package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesSampleDefectRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 打样不良原因及改善记录 Mapper（dev-20261010-028）
 */
@Mapper
public interface SalesSampleDefectRecordMapper extends BaseMapper<SalesSampleDefectRecord> {

    /** 查询某样品单的全部不良记录（按轮次/倒入顺序排序） */
    @Select("SELECT * FROM sales_sample_defect_record WHERE sample_order_id = #{sampleOrderId} AND deleted = 0 "
            + "ORDER BY round_no ASC, id ASC")
    List<SalesSampleDefectRecord> selectByOrderId(@Param("sampleOrderId") Long sampleOrderId);
}
