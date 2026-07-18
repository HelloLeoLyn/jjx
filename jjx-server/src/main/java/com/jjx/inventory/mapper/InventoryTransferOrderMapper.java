package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryTransferOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 调拨单Mapper接口
 */
@Mapper
public interface InventoryTransferOrderMapper extends BaseMapper<InventoryTransferOrder> {

    /**
     * 查询待审批的调拨单
     */
    @Select("SELECT * FROM inventory_transfer_order WHERE approve_status = 'pending'")
    List<InventoryTransferOrder> selectPendingApproval();

    /**
     * 查询进行中的调拨单
     */
    @Select("SELECT * FROM inventory_transfer_order WHERE order_status IN ('approved', 'out_confirm')")
    List<InventoryTransferOrder> selectProcessing();

    /**
     * 更新调拨单状态
     */
    @Update("UPDATE inventory_transfer_order SET order_status = #{status}, update_time = NOW() " +
            "WHERE transfer_id = #{transferId}")
    int updateStatus(@Param("transferId") Long transferId, @Param("status") String status);

    /**
     * 确认出库
     */
    @Update("UPDATE inventory_transfer_order SET order_status = 'out_confirm', " +
            "out_operator = #{operator}, out_time = NOW() " +
            "WHERE transfer_id = #{transferId} AND order_status = 'approved'")
    int confirmOut(@Param("transferId") Long transferId, @Param("operator") String operator);

    /**
     * 确认入库
     */
    @Update("UPDATE inventory_transfer_order SET order_status = 'in_confirm', " +
            "in_operator = #{operator}, in_time = NOW(), actual_date = CURDATE() " +
            "WHERE transfer_id = #{transferId} AND order_status = 'out_confirm'")
    int confirmIn(@Param("transferId") Long transferId, @Param("operator") String operator);

}
