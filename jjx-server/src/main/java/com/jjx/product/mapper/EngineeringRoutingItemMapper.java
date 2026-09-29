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
     * 根据路线ID查询明细（按组 + 工序顺序排序；子件跟随其父工序）
     * dev-20260929-027：process_order 是「组内序号」（历史档案按 面板/上线/下线 分组各自从 1），
     * 故排序键为 (workflow_seq, 组内序号)；子件 process_order 为 NULL，取其父行的序号落在父行之后，
     * 同一父行下再按 group_order 排子件。
     */
    @Select("SELECT i.* FROM engineering_routing_item i "
            + "LEFT JOIN engineering_routing_item pr ON pr.detail_id = i.parent_id "
            + "WHERE i.routing_id = #{routingId} "
            + "ORDER BY i.workflow_seq, COALESCE(pr.process_order, i.process_order), COALESCE(i.group_order, 0), i.detail_id")
    List<EngineeringRoutingItem> selectByRoutingId(@Param("routingId") Long routingId);

    @Select("SELECT " +
            "i.detail_id AS item_id, i.routing_id, i.process_order, " +
            "i.workflow_seq, " +
            "i.custom_labor_hours, i.custom_machine_hours, " +
            "i.custom_process_params, i.description, i.work_instruction, i.remark, " +
            "i.create_time, i.update_time, " +
            "i.major_category, " +
            "i.group_id, i.group_order, i.group_name, " +
            "i.parent_id, " +
            "i.index_number, i.precondition, i.precondition_display, i.is_optional, " +
            // 2026-08-12：印刷等自定义工序名称冗余在 i.process_name（COALESCE 兑底）
            "p.process_id, p.process_code, COALESCE(p.process_name, i.process_name) AS process_name, " +
            // 路线明细的 process_category 表示面板/上线/下线分组，不能被标准工序自身类别覆盖。
            "p.process_type, i.process_category AS process_category, " +
            "p.standard_labor_hours, p.standard_machine_hours, " +
            "p.equipment_type, p.skill_requirement, " +
            "p.is_enabled, p.display_order, p.icon, p.has_index, p.has_work_instruction " +
            "FROM engineering_routing_item i " +
            "LEFT JOIN engineering_standard_process p ON i.process_id = p.process_id " +
            "LEFT JOIN engineering_routing_item pr ON pr.detail_id = i.parent_id " +
            "WHERE i.routing_id = #{routingId} " +
            // dev-20260929-027：先按组（workflow_seq），组内按工序序号；子件取父行序号跟随父行（子件 group_order 排其后）
            "ORDER BY i.workflow_seq, COALESCE(pr.process_order, i.process_order), COALESCE(i.group_order, 0), i.detail_id")
    List<EngineeringRoutingItemVO> selectVOsByRoutingId(@Param("routingId") Long routingId);

    /**
     * 删除路线的所有明细
     */
    @Select("DELETE FROM engineering_routing_item WHERE routing_id = #{routingId}")
    void deleteByRoutingId(@Param("routingId") Long routingId);
}
