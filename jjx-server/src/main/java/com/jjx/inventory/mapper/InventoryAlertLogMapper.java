package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryAlertLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 库存预警日志Mapper接口
 */
@Mapper
public interface InventoryAlertLogMapper extends BaseMapper<InventoryAlertLog> {

    /**
     * 查询未处理的预警
     */
    @Select("SELECT * FROM inventory_alert_log WHERE status IN ('new', 'read') ORDER BY alert_time DESC")
    List<InventoryAlertLog> selectUnprocessed();

    /**
     * 查询指定物料是否存在未处理的预警
     */
    @Select("SELECT COUNT(*) FROM inventory_alert_log WHERE alert_type = #{alertType} " +
            "AND material_id = #{materialId} AND status IN ('new', 'read')")
    int existsUnprocessed(@Param("alertType") String alertType,
                          @Param("materialId") Long materialId);

    /**
     * 标记预警为已处理
     */
    @Update("UPDATE inventory_alert_log SET status = 'processed', " +
            "processed_by = #{processedBy}, processed_time = NOW(), " +
            "process_remark = #{processRemark} " +
            "WHERE alert_id = #{alertId}")
    int markProcessed(@Param("alertId") Long alertId,
                      @Param("processedBy") String processedBy,
                      @Param("processRemark") String processRemark);

    /**
     * 批量标记已读
     */
    @Update("UPDATE inventory_alert_log SET status = 'read' WHERE alert_id IN (${alertIds})")
    int batchMarkRead(@Param("alertIds") String alertIds);

}
