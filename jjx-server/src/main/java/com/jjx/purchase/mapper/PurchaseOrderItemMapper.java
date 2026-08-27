package com.jjx.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购订单明细Mapper接口
 * 提供采购订单明细的数据访问操作
 */
@Mapper
public interface PurchaseOrderItemMapper extends BaseMapper<PurchaseOrderItem> {

    /**
     * 根据订单ID查询明细列表
     *
     * @param orderId 订单ID
     * @return 明细列表
     */
    @Select("SELECT * FROM purchase_order_item WHERE order_id = #{orderId} ORDER BY item_order ASC")
    List<PurchaseOrderItem> selectItemsByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据订单ID删除明细
     *
     * @param orderId 订单ID
     * @return 结果
     */
    @Update("DELETE FROM purchase_order_item WHERE order_id = #{orderId}")
    int deleteByOrderId(@Param("orderId") Long orderId);

    /**
     * 更新收货数量
     *
     * @param itemId 明细ID
     * @param receivedQuantity 收货数量
     * @return 结果
     */
    @Update("UPDATE purchase_order_item SET received_quantity = received_quantity + #{receivedQuantity}, update_time = NOW() WHERE item_id = #{itemId}")
    int updateReceivedQuantity(@Param("itemId") Long itemId, @Param("receivedQuantity") BigDecimal receivedQuantity);

    /**
     * 更新收货状态
     *
     * @param itemId 明细ID
     * @param receiptStatus 收货状态
     * @return 结果
     */
    @Update("UPDATE purchase_order_item SET receipt_status = #{receiptStatus}, update_time = NOW() WHERE item_id = #{itemId}")
    int updateReceiptStatus(@Param("itemId") Long itemId, @Param("receiptStatus") String receiptStatus);

    /**
     * 更新检验结果
     *
     * @param itemId 明细ID
     * @param inspectionResult 检验结果
     * @param inspectionRemark 检验备注
     * @return 结果
     */
    @Update("UPDATE purchase_order_item SET inspection_result = #{inspectionResult}, inspection_remark = #{inspectionRemark}, update_time = NOW() WHERE item_id = #{itemId}")
    int updateInspectionInfo(@Param("itemId") Long itemId,
                            @Param("inspectionResult") String inspectionResult,
                            @Param("inspectionRemark") String inspectionRemark);

    /**
     * 更新询价状态
     *
     * @param itemId 明细ID
     * @param inquiryStatus 询价状态
     * @param inquiryInfo 询价信息
     * @return 结果
     */
    @Update("UPDATE purchase_order_item SET inquiry_status = #{inquiryStatus}, inquiry_info = #{inquiryInfo}, update_time = NOW() WHERE item_id = #{itemId}")
    int updateInquiryInfo(@Param("itemId") Long itemId,
                         @Param("inquiryStatus") String inquiryStatus,
                         @Param("inquiryInfo") String inquiryInfo);

    /**
     * 根据物料ID查询订单明细
     *
     * @param materialId 物料ID
     * @return 订单明细列表
     */
    @Select("SELECT * FROM purchase_order_item WHERE material_id = #{materialId} ORDER BY create_time DESC")
    List<PurchaseOrderItem> selectItemsByMaterialId(@Param("materialId") Long materialId);

    /**
     * 根据订单ID和物料ID查询明细
     *
     * @param orderId 订单ID
     * @param materialId 物料ID
     * @return 明细
     */
    @Select("SELECT * FROM purchase_order_item WHERE order_id = #{orderId} AND material_id = #{materialId}")
    PurchaseOrderItem selectItemByOrderAndMaterial(@Param("orderId") Long orderId, @Param("materialId") Long materialId);

    /**
     * 查询待收货的明细列表
     *
     * @return 待收货明细列表
     */
    @Select("SELECT * FROM purchase_order_item WHERE receipt_status IN ('pending', 'partially_received') ORDER BY create_time ASC")
    List<PurchaseOrderItem> selectPendingReceiptItems();

    /**
     * 在途采购量按物料汇总（DEV-815：已下采购订单未收货部分，低库存建议扣除用）
     *
     * @return 每行 material_id + in_transit
     */
    @Select("SELECT i.material_id AS material_id, SUM(i.quantity - IFNULL(i.received_quantity, 0)) AS in_transit " +
            "FROM purchase_order_item i INNER JOIN purchase_order o ON o.order_id = i.order_id " +
            "WHERE o.receipt_status = 0 " +
            "AND o.approval_status IN (1, 3, 4) " + // 023/089：在途统计 草稿(1)/待审批(3)/已批准(4)；2026-08-18 纳入草稿，防刚下单仍反复建议；排除已取消(2)/已拒绝(5)
            "GROUP BY i.material_id")
    java.util.List<java.util.Map<String, Object>> selectInTransitByMaterial();

    /**
     * 查询待询价的明细列表
     *
     * @return 待询价明细列表
     */
    @Select("SELECT * FROM purchase_order_item WHERE inquiry_status = 'pending' ORDER BY create_time ASC")
    List<PurchaseOrderItem> selectPendingInquiryItems();

    /**
     * 查询需要检验的明细列表
     *
     * @return 需要检验的明细列表
     */
    @Select("SELECT * FROM purchase_order_item WHERE inspection_result IS NULL AND received_quantity > 0 ORDER BY create_time ASC")
    List<PurchaseOrderItem> selectPendingInspectionItems();

    /**
     * 统计订单的明细数量
     *
     * @param orderId 订单ID
     * @return 明细数量
     */
    @Select("SELECT COUNT(*) FROM purchase_order_item WHERE order_id = #{orderId}")
    int countItemsByOrderId(@Param("orderId") Long orderId);

    /**
     * 统计订单的总金额
     *
     * @param orderId 订单ID
     * @return 总金额
     */
    @Select("SELECT SUM(amount) FROM purchase_order_item WHERE order_id = #{orderId}")
    BigDecimal sumAmountByOrderId(@Param("orderId") Long orderId);

    /**
     * 统计订单的总收货数量
     *
     * @param orderId 订单ID
     * @return 总收货数量
     */
    @Select("SELECT SUM(received_quantity) FROM purchase_order_item WHERE order_id = #{orderId}")
    BigDecimal sumReceivedQuantityByOrderId(@Param("orderId") Long orderId);
}
