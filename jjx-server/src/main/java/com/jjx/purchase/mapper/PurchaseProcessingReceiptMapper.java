package com.jjx.purchase.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;

/** 原始收货凭证为实际交付与母卷分摊的唯一事实；不另存加工收货余额。 */
@Mapper
public interface PurchaseProcessingReceiptMapper {
    @Select("""
        SELECT purchase_processing_json FROM inventory_inbound_item
        WHERE source_purchase_item_id=#{itemId} AND purchase_processing_json IS NOT NULL
          AND reversal_of_item_id IS NULL AND quantity>0
        ORDER BY item_id
        """)
    List<String> history(@Param("itemId") Long itemId);
}
