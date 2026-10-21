package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.product.domain.entity.StandardProcessUsage;
import com.jjx.product.domain.vo.StandardProcessCommonVO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StandardProcessUsageMapper extends BaseMapper<StandardProcessUsage> {
    @Delete("DELETE FROM engineering_standard_process_usage WHERE period_date >= #{start} AND period_date < #{end}")
    int clearPeriod(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // 按主表创建日分桶，一个 BOM 仅落在一天；跨日 SUM 仍是 DISTINCT bom_id 口径。
    @Insert("""
        INSERT INTO engineering_standard_process_usage (process_id, period_date, use_count, update_time)
        SELECT i.process_id, DATE(b.create_time), COUNT(DISTINCT i.bom_id), NOW()
        FROM engineering_bom_item i
        JOIN engineering_bom b ON b.bom_id = i.bom_id
        JOIN engineering_standard_process p ON p.process_id = i.process_id
        WHERE b.create_time >= #{start} AND b.create_time < #{end}
          AND p.process_status != #{rejected} AND p.is_enabled = 1
        GROUP BY i.process_id, DATE(b.create_time)
        ON DUPLICATE KEY UPDATE use_count = VALUES(use_count), update_time = NOW()
        """)
    int aggregatePeriod(@Param("start") LocalDate start, @Param("end") LocalDate end,
                        @Param("rejected") int rejected);

    // 请求只读 usage，不扫描 BOM；同次数按 process_id 排序，保证 top N 稳定。
    @Select("""
        SELECT p.process_id, p.process_name, p.process_category, p.icon, p.process_status,
               SUM(u.use_count) AS use_count
        FROM engineering_standard_process_usage u
        JOIN engineering_standard_process p ON p.process_id = u.process_id
        WHERE u.period_date >= #{start} AND u.period_date < #{end}
          AND p.process_status != #{rejected} AND p.is_enabled = 1
        GROUP BY p.process_id, p.process_name, p.process_category, p.icon, p.process_status
        HAVING SUM(u.use_count) > 0
        ORDER BY use_count DESC, p.process_id ASC
        LIMIT #{limit}
        """)
    List<StandardProcessCommonVO> selectCommon(@Param("start") LocalDate start,
            @Param("end") LocalDate end, @Param("rejected") int rejected, @Param("limit") int limit);
}
