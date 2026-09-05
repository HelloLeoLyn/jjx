package com.jjx.engineering.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 工艺路线明细Mapper（原生SQL，engineering_routing_item 无实体映射）
 */
@Mapper
public interface RoutingItemMapper {

    @Insert("INSERT INTO engineering_routing_item (routing_id, process_id, major_category, process_name, process_order, " +
            "custom_labor_hours, custom_machine_hours, custom_process_params, description, process_category, group_id, group_name, group_order, index_number, parent_id) " +
            "VALUES (#{routingId}, #{processId}, #{majorCategory}, #{processName}, #{processOrder}, #{laborHours}, #{machineHours}, " +
            "#{processParams}, #{description}, #{processCategory}, #{groupId}, #{groupName}, #{groupOrder}, #{indexNumber}, #{parentId})")
    int insertItem(@Param("routingId") Long routingId,
                   @Param("processId") Long processId,
                   @Param("majorCategory") String majorCategory,
                   @Param("processName") String processName,
                   @Param("processOrder") Integer processOrder,
                   @Param("laborHours") BigDecimal laborHours,
                   @Param("machineHours") BigDecimal machineHours,
                   @Param("processParams") String processParams,
                   @Param("description") String description,
                   @Param("processCategory") String processCategory,
                   @Param("groupId") Long groupId,
                   @Param("groupName") String groupName,
                   @Param("groupOrder") Integer groupOrder,
                   @Param("indexNumber") Integer indexNumber,
                   @Param("parentId") Long parentId);

    /** 2026-09-05 父子结构：取刚插入行的自增 id（同连接事务内安全） */
    @org.apache.ibatis.annotations.Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();
}
