package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryTransferItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 调拨单明细Mapper接口
 */
@Mapper
public interface InventoryTransferItemMapper extends BaseMapper<InventoryTransferItem> {

    /**
     * 根据调拨单ID查询明细列表
     */
    @Select("SELECT * FROM inventory_transfer_item WHERE transfer_id = #{transferId}")
    List<InventoryTransferItem> selectByTransferId(@Param("transferId") Long transferId);

    /**
     * 更新出库数量
     */
    @Update("UPDATE inventory_transfer_item SET out_quantity = out_quantity + #{quantity}, " +
            "from_location_id = #{locationId} " +
            "WHERE item_id = #{itemId}")
    int updateOutQuantity(@Param("itemId") Long itemId,
                          @Param("quantity") BigDecimal quantity,
                          @Param("locationId") Long locationId);

    /**
     * 更新入库数量
     */
    @Update("UPDATE inventory_transfer_item SET in_quantity = in_quantity + #{quantity}, " +
            "to_location_id = #{locationId} " +
            "WHERE item_id = #{itemId}")
    int updateInQuantity(@Param("itemId") Long itemId,
                         @Param("quantity") BigDecimal quantity,
                         @Param("locationId") Long locationId);

    /**
     * 更新明细状态
     */
    @Update("UPDATE inventory_transfer_item SET status = #{status} WHERE item_id = #{itemId}")
    int updateStatus(@Param("itemId") Long itemId, @Param("status") String status);

}
