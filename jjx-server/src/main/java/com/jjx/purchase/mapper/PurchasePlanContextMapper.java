package com.jjx.purchase.mapper;

import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;

/** 计划只读信息：库存沿用汇总余额，在途限定单一物料并使用领域枚举参数。 */
@Mapper
public interface PurchasePlanContextMapper {
    @Select("SELECT COALESCE(SUM(total_quantity),0) FROM inventory_stock WHERE material_id=#{materialId}")
    BigDecimal stock(Long materialId);
    @Select("SELECT COALESCE(SUM(total_quantity-COALESCE(total_reserved,0)),0) FROM inventory_stock WHERE material_id=#{materialId}")
    BigDecimal available(Long materialId);
    @Select("<script>SELECT COALESCE(SUM(GREATEST(i.quantity-COALESCE(i.received_quantity,0),0)),0) " +
        "FROM purchase_order_item i JOIN purchase_order o ON o.order_id=i.order_id " +
        "WHERE i.material_id=#{materialId} AND o.approval_status IN " +
        "<foreach collection='approvals' item='s' open='(' separator=',' close=')'>#{s}</foreach> " +
        "AND o.receipt_status IN <foreach collection='receipts' item='s' open='(' separator=',' close=')'>#{s}</foreach></script>")
    BigDecimal inTransit(@Param("materialId") Long materialId, @Param("approvals") List<Integer> approvals, @Param("receipts") List<Integer> receipts);
}
