package com.jjx.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.quality.domain.entity.QualityNcrAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 不良处置单 Mapper —— dev-20260917-003
 */
@Mapper
public interface QualityNcrActionMapper extends BaseMapper<QualityNcrAction> {

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
