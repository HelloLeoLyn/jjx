package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionWorkReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 生产报工 Mapper
 * 基础查询能力（BaseMapper + Wrapper 足够）；Task 数量投影走 SQL SUM。
 * P3：sumTaskQuantityByStatus = 当前 Task 自己的 pending（PENDING）/ completed（APPROVED）占用量。
 */
@Mapper
public interface ProductionWorkReportMapper extends BaseMapper<ProductionWorkReport> {

    /** 当前 Task 自己指定状态报工数量 = SUM(qualified + defective)（P3 Task 投影用） */
    @Select("SELECT COALESCE(SUM(qualified_quantity + defective_quantity), 0) "
            + "FROM production_work_report WHERE task_id = #{taskId} AND report_status = #{status}")
    BigDecimal sumTaskQuantityByStatus(@Param("taskId") Long taskId, @Param("status") String status);
}
