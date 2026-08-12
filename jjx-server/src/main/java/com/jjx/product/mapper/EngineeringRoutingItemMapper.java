package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.engineering.domain.entity.EngineeringRoutingItem;
import com.jjx.product.domain.vo.EngineeringRoutingItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EngineeringRoutingItemMapper extends BaseMapper<EngineeringRoutingItem> {

    /**
     * 根据路线ID查询明细（按工序顺序排序）
     */
    @Select("SELECT * FROM engineering_routing_item WHERE routing_id = #{routingId} ORDER BY process_order")
    List<EngineeringRoutingItem> selectByRoutingId(@Param("routingId") Long routingId);

    @Select("SELECT " +
            "i.detail_id AS item_id, i.routing_id, i.process_order, " +
            "i.custom_labor_hours, i.custom_machine_hours, " +
            "i.custom_process_params, i.description, " +
            "i.create_time, i.update_time, " +
            "i.major_category, " +
            "i.group_id, i.group_order, i.group_name, " +
            "i.index_number, i.precondition, i.precondition_display, i.is_optional, " +
            // 2026-08-12：印刷等自定义工序名称冗余在 i.process_name（COALESCE 兑底）
            "p.process_id, p.process_code, COALESCE(p.process_name, i.process_name) AS process_name, " +
            "p.process_type, COALESCE(p.process_category, i.process_category) AS process_category, " +
            "p.standard_labor_hours, p.standard_machine_hours, " +
            "p.equipment_type, p.skill_requirement, " +
            "p.is_enabled, p.display_order, p.icon, p.has_index " +
            "FROM engineering_routing_item i " +
            "LEFT JOIN engineering_standard_process p ON i.process_id = p.process_id " +
            "WHERE i.routing_id = #{routingId} " +
            "ORDER BY i.group_order, i.process_order")
    List<EngineeringRoutingItemVO> selectVOsByRoutingId(@Param("routingId") Long routingId);

    /**
     * 删除路线的所有明细
     */
    @Select("DELETE FROM engineering_routing_item WHERE routing_id = #{routingId}")
    void deleteByRoutingId(@Param("routingId") Long routingId);
}
