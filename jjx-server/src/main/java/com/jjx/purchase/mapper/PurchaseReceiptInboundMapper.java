package com.jjx.purchase.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 采购收货 → 入库单 只读查询（dev-20260923-004）
 *
 * <p>背景：采购收货会自动生成 PURCHASE 来源的入库单（DEV-624，收货≠入库，仓库确认后才加库存）。
 * 收货页需要把"这批货收成了哪张入库单"展示出来，并把用户引到 质量管理→来料检验（IQC 检验批就是按入库单建的）。
 *
 * <p>为什么放在 purchase 域：只读、单一用途，避免为查一个单号去依赖 inventory 的写服务；
 * 不引入任何写操作，也不改 inventory 模块。
 */
@Mapper
public interface PurchaseReceiptInboundMapper {

    /**
     * 按采购订单查收货生成的全部入库单（倒序，最新在前）
     */
    @Select("""
            SELECT inbound_id   AS inboundId,
                   inbound_no   AS inboundNo,
                   order_status AS orderStatus,
                   inbound_date AS inboundDate,
                   total_quantity AS totalQuantity,
                   inspection_result AS inspectionResult
              FROM inventory_inbound_order
             WHERE source_type = 'PURCHASE'
               AND source_id = #{orderId}
             ORDER BY inbound_id DESC
            """)
    List<Map<String, Object>> selectByPurchaseOrder(@Param("orderId") Long orderId);
}
