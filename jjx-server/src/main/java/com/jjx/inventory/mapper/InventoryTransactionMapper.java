package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存流水Mapper接口
 */
@Mapper
public interface InventoryTransactionMapper extends BaseMapper<InventoryTransaction> {

    /**
     * 查询指定物料的流水记录
     */
    @Select("SELECT * FROM inventory_transaction WHERE material_id = #{materialId} " +
            "ORDER BY transaction_time DESC LIMIT #{limit}")
    List<InventoryTransaction> selectByMaterial(@Param("materialId") Long materialId,
                                                 @Param("limit") int limit);

    /**
     * 查询指定时间范围内的流水
     */
    @Select("SELECT * FROM inventory_transaction WHERE transaction_time BETWEEN #{startTime} AND #{endTime} " +
            "ORDER BY transaction_time DESC")
    List<InventoryTransaction> selectByTimeRange(@Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定物料的出入库数量
     */
    @Select("SELECT transaction_type, SUM(quantity) as total_quantity " +
            "FROM inventory_transaction WHERE material_id = #{materialId} " +
            "AND transaction_time >= #{startTime} GROUP BY transaction_type")
    List<InventoryTransaction> statByMaterial(@Param("materialId") Long materialId,
                                               @Param("startTime") LocalDateTime startTime);

}
