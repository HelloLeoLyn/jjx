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

    @Insert("INSERT INTO engineering_routing_item (routing_id, process_id, process_order, " +
            "custom_labor_hours, custom_machine_hours, custom_process_params, description, process_category, group_id, group_name, index_number) " +
            "VALUES (#{routingId}, #{processId}, #{processOrder}, #{laborHours}, #{machineHours}, " +
            "#{processParams}, #{description}, #{processCategory}, #{groupId}, #{groupName}, #{indexNumber})")
    int insertItem(@Param("routingId") Long routingId,
                   @Param("processId") Long processId,
                   @Param("processOrder") Integer processOrder,
                   @Param("laborHours") BigDecimal laborHours,
                   @Param("machineHours") BigDecimal machineHours,
                   @Param("processParams") String processParams,
                   @Param("description") String description,
                   @Param("processCategory") String processCategory,
                   @Param("groupId") Long groupId,
                   @Param("groupName") String groupName,
                   @Param("indexNumber") Integer indexNumber);
}
