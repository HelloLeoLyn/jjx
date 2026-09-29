package com.jjx.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.quality.domain.entity.QualityNcrAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

/**
 * 不良处置单 Mapper —— dev-20260917-003
 */
@Mapper
public interface QualityNcrActionMapper extends BaseMapper<QualityNcrAction> {

    /** 处置事实唯一派生口径：DONE/PROCESSING 有效，VOID/待审批不计。 */
    @Select("SELECT COALESCE(SUM(a.quantity), 0) FROM quality_ncr_action a "
            + "JOIN quality_ncr n ON n.ncr_id = a.ncr_id AND n.del_flag = 0 "
            + "WHERE a.ncr_id = #{ncrId} AND a.del_flag = 0 "
            + "AND a.status IN ('DONE', 'PROCESSING')")
    BigDecimal sumEffectiveQuantityByNcrId(@Param("ncrId") Long ncrId);

    /** 按检验批派生有效处置量，供 quality_lot 兼容汇总缓存刷新。 */
    @Select("SELECT COALESCE(SUM(a.quantity), 0) FROM quality_ncr_action a "
            + "JOIN quality_ncr n ON n.ncr_id = a.ncr_id AND n.del_flag = 0 "
            + "WHERE n.lot_id = #{lotId} AND a.del_flag = 0 "
            + "AND a.status IN ('DONE', 'PROCESSING')")
    BigDecimal sumEffectiveQuantityByLotId(@Param("lotId") Long lotId);

    /**
     * 已终结处置量（DONE）—— **关闭判据的唯一真源**（dev-20260929-004）。
     *
     * <p>与 {@link #sumEffectiveQuantityByLotId}（DONE + PROCESSING，"已安排处置量"占用口径）区分：
     * 在途（返工处理中 / 报废待审批）不得当成"已处置"，否则批会在返工未闭环时提前关闭。
     */
    @Select("SELECT COALESCE(SUM(a.quantity), 0) FROM quality_ncr_action a "
            + "JOIN quality_ncr n ON n.ncr_id = a.ncr_id AND n.del_flag = 0 "
            + "WHERE n.lot_id = #{lotId} AND a.del_flag = 0 AND a.status = 'DONE'")
    BigDecimal sumSettledQuantityByLotId(@Param("lotId") Long lotId);

    /** 在途处置量（待审批 / 处理中）—— 不为 0 时该批不得关闭（dev-20260929-004）。 */
    @Select("SELECT COALESCE(SUM(a.quantity), 0) FROM quality_ncr_action a "
            + "JOIN quality_ncr n ON n.ncr_id = a.ncr_id AND n.del_flag = 0 "
            + "WHERE n.lot_id = #{lotId} AND a.del_flag = 0 "
            + "AND a.status IN ('PENDING_APPROVAL', 'PROCESSING')")
    BigDecimal sumInFlightQuantityByLotId(@Param("lotId") Long lotId);

    /**
     * 按批链汇总「已生效(DONE)」的处置量（dev-20260923-021 一期判定护栏用）。
     *
     * <p>返回每行：actionType（REWORK/CONCESSION/SCRAP）、qty（DONE 合计）、confirmedQty（其中客户已确认的量）。
     * 调用方据此算：已报废未回收 = SCRAP.qty；让步未确认 = CONCESSION.qty − CONCESSION.confirmedQty；
     * REWORK 视为可回收（不扣减，回收走返工后的复检批）。
     */
    @Select("<script>" +
            "SELECT a.action_type AS actionType, " +
            "       COALESCE(SUM(a.quantity), 0) AS qty, " +
            "       COALESCE(SUM(CASE WHEN a.customer_confirmed = 1 THEN a.quantity ELSE 0 END), 0) AS confirmedQty " +
            "  FROM quality_ncr_action a " +
            "  JOIN quality_ncr n ON n.ncr_id = a.ncr_id AND n.del_flag = 0 " +
            " WHERE a.del_flag = 0 AND a.status = 'DONE' " +
            "   AND n.lot_id IN " +
            "   <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            " GROUP BY a.action_type" +
            "</script>")
    List<Map<String, Object>> sumDoneActionsByLotIds(@Param("lotIds") List<Long> lotIds);
}
