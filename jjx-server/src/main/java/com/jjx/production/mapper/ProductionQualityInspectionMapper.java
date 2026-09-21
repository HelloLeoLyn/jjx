package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionQualityInspection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductionQualityInspectionMapper extends BaseMapper<ProductionQualityInspection> {

    /**
     * 当前读：锁住 IQC/质检记录行（2026-09-21 dev-20260921-003）。
     * 复核判定（待审核 → 已审核）必须基于最新已提交状态，普通一致性读会读到事务开始时的旧快照，
     * 并发复核时会互相看不到对方刚提交的结果。
     */
    @Select("SELECT * FROM production_quality_inspection WHERE inspection_id = #{inspectionId} FOR UPDATE")
    ProductionQualityInspection selectByIdForUpdate(@Param("inspectionId") Long inspectionId);
}
