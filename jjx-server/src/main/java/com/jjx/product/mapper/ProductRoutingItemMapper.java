package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.product.domain.entity.ProductRoutingItem;
import com.jjx.product.domain.vo.ProductRoutingItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductRoutingItemMapper extends BaseMapper<ProductRoutingItem> {

    /**
     * 根据路线ID查询明细（按工序顺序排序）
     */
    @Select("SELECT * FROM engineering_routing_item WHERE routing_id = #{routingId} ORDER BY process_order")
    List<ProductRoutingItem> selectByRoutingId(@Param("routingId") Long routingId);

    @Select("SELECT " +
            "i.detail_id, i.routing_id, i.process_order, " +
            "i.custom_labor_hours, i.custom_machine_hours, " +
            "i.custom_process_params, i.description, " +
            "i.create_time, i.update_time, " +
            "i.group_id, i.group_order, i.group_name, " +
            "p.process_id, p.process_code, p.process_name, " +
            "p.process_type, p.process_category, " +
            "p.standard_labor_hours, p.standard_machine_hours, " +
            "p.equipment_type, p.skill_requirement, " +
            "p.is_enabled, p.display_order, p.icon " +
            "FROM engineering_routing_item i " +
            "LEFT JOIN product_standard_process p ON i.process_id = p.process_id " +
            "WHERE i.routing_id = #{routingId} " +
            "ORDER BY i.group_order, i.process_order")
    List<ProductRoutingItemVO> selectVOsByRoutingId(@Param("routingId") Long routingId);

    /**
     * 删除路线的所有明细
     */
    @Select("DELETE FROM engineering_routing_item WHERE routing_id = #{routingId}")
    void deleteByRoutingId(@Param("routingId") Long routingId);
}
