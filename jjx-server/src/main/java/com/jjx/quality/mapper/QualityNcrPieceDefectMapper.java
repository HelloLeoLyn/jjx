package com.jjx.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.quality.domain.entity.QualityNcrPieceDefect;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 不良件缺陷记录 mapper —— dev-20260924-004（批量插入同件表口径）。
 */
@Mapper
public interface QualityNcrPieceDefectMapper extends BaseMapper<QualityNcrPieceDefect> {

    @Insert("<script>"
            + "INSERT INTO quality_ncr_piece_defect (piece_id, ncr_id, lot_id, check_item, defect_level, is_main,"
            + " sort_order, remark)"
            + " VALUES "
            + "<foreach collection='list' item='it' separator=','>"
            + "(#{it.pieceId}, #{it.ncrId}, #{it.lotId}, #{it.checkItem}, #{it.defectLevel}, #{it.isMain},"
            + " #{it.sortOrder}, #{it.remark})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("list") List<QualityNcrPieceDefect> list);
}
