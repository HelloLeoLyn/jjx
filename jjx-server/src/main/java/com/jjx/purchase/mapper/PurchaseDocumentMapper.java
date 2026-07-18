package com.jjx.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.purchase.domain.entity.PurchaseDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 采购票据Mapper接口
 */
@Mapper
public interface PurchaseDocumentMapper extends BaseMapper<PurchaseDocument> {

    /**
     * 检查票据编号是否唯一
     */
    @Select("SELECT COUNT(*) FROM purchase_document WHERE document_no = #{documentNo}")
    int checkDocumentNoUnique(@Param("documentNo") String documentNo);

    /**
     * 根据订单ID查询票据列表
     */
    @Select("SELECT * FROM purchase_document WHERE order_id = #{orderId} ORDER BY document_date DESC")
    List<PurchaseDocument> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据供应商ID查询票据列表
     */
    @Select("SELECT * FROM purchase_document WHERE supplier_id = #{supplierId} ORDER BY document_date DESC")
    List<PurchaseDocument> selectBySupplierId(@Param("supplierId") Long supplierId);

    /**
     * 查询待核验的票据列表
     */
    @Select("SELECT * FROM purchase_document WHERE document_status = 'pending' ORDER BY document_date ASC")
    List<PurchaseDocument> selectPendingVerification();

    /**
     * 查询已核验的票据列表
     */
    @Select("SELECT * FROM purchase_document WHERE document_status = 'verified' ORDER BY document_date DESC")
    List<PurchaseDocument> selectVerified();

    /**
     * 查询今日票据
     */
    @Select("SELECT * FROM purchase_document WHERE DATE(create_time) = CURDATE() ORDER BY create_time DESC")
    List<PurchaseDocument> selectToday();

    /**
     * 查询本周票据
     */
    @Select("SELECT * FROM purchase_document WHERE YEARWEEK(create_time, 1) = YEARWEEK(CURDATE(), 1) ORDER BY create_time DESC")
    List<PurchaseDocument> selectWeek();

    /**
     * 查询本月票据
     */
    @Select("SELECT * FROM purchase_document WHERE YEAR(create_time) = YEAR(CURDATE()) AND MONTH(create_time) = MONTH(CURDATE()) ORDER BY create_time DESC")
    List<PurchaseDocument> selectMonth();

    /**
     * 查询逾期未开票的订单ID列表
     */
    @Select("SELECT DISTINCT po.order_id FROM purchase_order po " +
            "LEFT JOIN purchase_document pd ON po.order_id = pd.order_id " +
            "WHERE po.approval_status = 4 AND pd.document_id IS NULL " +
            "AND po.expected_delivery_date < #{date}")
    List<Long> selectOverdueOrderIds(@Param("date") LocalDate date);
}
