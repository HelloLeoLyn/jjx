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

    /** 查询指定日期前缀下的最大报工单号（固定四位序号可按字符串倒序）。 */
    @Select("SELECT report_no FROM production_work_report "
            + "WHERE report_no LIKE CONCAT(#{prefix}, '%') ORDER BY report_no DESC LIMIT 1")
    String selectMaxReportNo(@Param("prefix") String prefix);

    /** 当前 Task 自己指定状态报工数量 = SUM(qualified + defective)（P3 Task 投影用） */
    @Select("SELECT COALESCE(SUM(qualified_quantity + defective_quantity), 0) "
            + "FROM production_work_report WHERE task_id = #{taskId} AND report_status = #{status}")
    BigDecimal sumTaskQuantityByStatus(@Param("taskId") Long taskId, @Param("status") String status);

    /** 当前 Task 自己指定状态报工的合格数量（任务自动达标完成口径，不含不良数量）。 */
    @Select("SELECT COALESCE(SUM(qualified_quantity), 0) "
            + "FROM production_work_report WHERE task_id = #{taskId} AND report_status = #{status}")
    BigDecimal sumTaskQualifiedQuantityByStatus(@Param("taskId") Long taskId,
                                                @Param("status") String status);
}
