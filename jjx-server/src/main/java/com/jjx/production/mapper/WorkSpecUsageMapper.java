package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.WorkSpecUsage;
import com.jjx.production.domain.vo.WorkSpecUsageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工单换版执行区间 Mapper（dev-20261011-013）
 */
@Mapper
public interface WorkSpecUsageMapper extends BaseMapper<WorkSpecUsage> {

    /** 某工单的执行区间列表（含版本号，老→新） */
    @Select("SELECT u.id, u.work_order_id, u.spec_version_id, v.version_no, v.status AS version_status, "
            + "u.qty_from, u.qty_to, u.start_time, u.end_time, u.change_reason, "
            + "u.approved_by, u.approved_at, u.create_by, u.create_time "
            + "FROM work_spec_usage u "
            + "LEFT JOIN product_work_spec_version v ON v.id = u.spec_version_id "
            + "WHERE u.work_order_id = #{workOrderId} AND u.deleted = 0 "
            + "ORDER BY u.id ASC")
    List<WorkSpecUsageVO> selectVoByWorkOrderId(@Param("workOrderId") Long workOrderId);
}
