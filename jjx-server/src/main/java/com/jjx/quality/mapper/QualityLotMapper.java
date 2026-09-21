package com.jjx.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.quality.domain.entity.QualityLot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 检验批 Mapper —— dev-20260917-001
 */
@Mapper
public interface QualityLotMapper extends BaseMapper<QualityLot> {

    /** 取某前缀下已存在的数量（用于生成批号流水；避免依赖额外序列服务） */
    @Select("SELECT COUNT(*) FROM quality_lot WHERE lot_no LIKE CONCAT(#{prefix}, '%')")
    Long countByLotNoPrefix(@Param("prefix") String prefix);

    /** 判断批号是否已被占用 */
    @Select("SELECT COUNT(*) FROM quality_lot WHERE lot_no = #{lotNo}")
    Long countByLotNo(@Param("lotNo") String lotNo);

    /**
     * 行锁读取检验批（SELECT ... FOR UPDATE）。
     * 2026-09-21（dev-20260921-030）：判定/录入/复检 这三类写操作先取行锁，
     * 把同一批的并发请求（双击、多开页签）串行化，避免"读-判-写"竞态。
     * 必须在事务内调用。
     */
    @Select("SELECT * FROM quality_lot WHERE lot_id = #{lotId} AND del_flag = 0 FOR UPDATE")
    QualityLot selectForUpdate(@Param("lotId") Long lotId);
}
