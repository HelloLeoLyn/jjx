package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 销售订单Mapper接口
 * 提供销售订单的数据访问操作
 * 使用MyBatis-Plus注解方式，无需XML文件
 */
@Mapper
public interface OrderMapper extends BaseMapper<SalesOrder> {

    /**
     * 检查订单号是否存在
     *
     * @param orderNo 订单号
     * @return 是否存在
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sales_order WHERE order_no = #{orderNo} " +
            "</script>")
    int checkOrderNoUnique(@Param("orderNo") String orderNo);

    /**
     * 更新订单状态
     *
     * @param orderId 订单ID
     * @param status 订单状态
     * @return 结果
     */
    @Update("UPDATE sales_order SET order_status = #{status}, update_time = NOW() WHERE order_id = #{orderId} AND deleted = 0")
    int updateOrderStatus(@Param("orderId") Long orderId, @Param("status") Integer status);

    /**
     * 更新审核信息
     *
     * @param orderId 订单ID
     * @param approverId 审核人ID
     * @param approverName 审核人姓名
     * @param approveRemark 审核备注
     * @return 结果
     */
    @Update("UPDATE sales_order SET approver_id = #{approverId}, approver_name = #{approverName}, approve_time = NOW(), approve_remark = #{approveRemark} WHERE order_id = #{orderId} AND deleted = 0")
    int updateApproveInfo(@Param("orderId") Long orderId, @Param("approverId") Long approverId,
                         @Param("approverName") String approverName, @Param("approveRemark") String approveRemark);

    /**
     * 更新付款信息
     *
     * @param orderId 订单ID
     * @param paidAmount 已付金额
     * @return 结果
     */
    @Update("UPDATE sales_order SET paid_amount = paid_amount + #{paidAmount}, unpaid_amount = final_amount - (paid_amount + #{paidAmount}), update_time = NOW() WHERE order_id = #{orderId} AND deleted = 0")
    int updatePaymentInfo(@Param("orderId") Long orderId, @Param("paidAmount") BigDecimal paidAmount);

    /**
     * 根据客户ID查询订单列表
     *
     * @param customerId 客户ID
     * @return 订单列表
     */
    @Select("SELECT * FROM sales_order WHERE customer_id = #{customerId} AND deleted = 0 ORDER BY create_time DESC")
    List<SalesOrder> selectOrdersByCustomerId(@Param("customerId") Long customerId);

    /**
     * 根据报价单ID查询订单
     *
     * @param quotationId 报价单ID
     * @return 订单
     */
    @Select("SELECT * FROM sales_order WHERE quotation_id = #{quotationId} AND deleted = 0")
    SalesOrder selectOrderByQuotationId(@Param("quotationId") Long quotationId);


    @Update("UPDATE sales_order SET order_status = #{status}, update_time = NOW() " +
            "WHERE order_id = #{orderId} AND order_status = #{oldStatus}")
    int updateStatusWithCheck(@Param("orderId") Long orderId,
                              @Param("status") Integer status,
                              @Param("oldStatus") Integer oldStatus);

    /**
     * 更新样品单状态（带状态校验，防止并发脏数据）
     */
    @Update("UPDATE sales_order SET sample_status = #{newStatus}, update_time = NOW() " +
            "WHERE order_id = #{orderId} AND order_type = 2 AND sample_status = #{oldStatus} AND deleted = 0")
    int updateSampleStatus(@Param("orderId") Long orderId,
                           @Param("oldStatus") Integer oldStatus,
                           @Param("newStatus") Integer newStatus);

    /**
     * 工程接单：状态推进与接单信息一次性落库，且仅允许尚未接单的数据更新。
     */
    @Update("UPDATE sales_order SET sample_status = #{engineeringStatus}, " +
            "engineering_acceptor = #{acceptorName}, engineering_accept_time = NOW(), update_time = NOW() " +
            "WHERE order_id = #{orderId} AND order_type = 2 AND deleted = 0 " +
            "AND sample_status IN (#{requestStatus}, #{engineeringStatus}) " +
            "AND (engineering_acceptor IS NULL OR engineering_acceptor = '')")
    int acceptEngineering(@Param("orderId") Long orderId,
                          @Param("requestStatus") Integer requestStatus,
                          @Param("engineeringStatus") Integer engineeringStatus,
                          @Param("acceptorName") String acceptorName);

    /**
     * 查询样品单列表（order_type=2）
     */
    @Select("SELECT * FROM sales_order WHERE order_type = 2 AND deleted = 0 ORDER BY create_time DESC")
    List<SalesOrder> selectSampleOrders();
}
