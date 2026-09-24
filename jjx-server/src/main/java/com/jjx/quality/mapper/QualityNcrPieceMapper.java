package com.jjx.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.quality.domain.entity.QualityNcrPiece;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 不良件 mapper —— dev-20260924-004。
 * 批量插入用于大批量判定（方案 §11：500 条/批，不做逐件往返）。
 */
@Mapper
public interface QualityNcrPieceMapper extends BaseMapper<QualityNcrPiece> {

    @Insert("<script>"
            + "INSERT INTO quality_ncr_piece (piece_no, ncr_id, lot_id, seq_no, main_check_item, main_defect_level,"
            + " actual_value, sample_index, status, order_id, work_order_no, lot_no, remark, create_by, del_flag)"
            + " VALUES "
            + "<foreach collection='list' item='it' separator=','>"
            + "(#{it.pieceNo}, #{it.ncrId}, #{it.lotId}, #{it.seqNo}, #{it.mainCheckItem}, #{it.mainDefectLevel},"
            + " #{it.actualValue}, #{it.sampleIndex}, #{it.status}, #{it.orderId}, #{it.workOrderNo}, #{it.lotNo},"
            + " #{it.remark}, #{it.createBy}, 0)"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("list") List<QualityNcrPiece> list);
}
