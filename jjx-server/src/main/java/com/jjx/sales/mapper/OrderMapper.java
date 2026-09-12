package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.io.Serializable;

/**
 * 销售订单Mapper接口
 * 提供销售订单的数据访问操作
 * 使用MyBatis-Plus注解方式，无需XML文件
 */
@Mapper
public interface OrderMapper extends BaseMapper<SalesOrder> {

    /** 样品字段已拆至扩展表，查询时回填为领域对象，兼容现有样品服务。 */
    @Override
    @Select("SELECT o.*, s.sample_status, s.sample_round, s.sample_qty, s.engineering_note, " +
            "s.engineering_acceptor, s.engineering_accept_time, s.reject_reason, s.current_process, " +
            "s.sample_cost, s.sample_work_hours, s.sample_tracking_no, s.sample_send_date, " +
            "s.sample_confirm_date, s.confirm_by, s.confirm_method, s.confirm_time, s.confirm_sent_time, " +
            "s.sample_client_name, s.converted_order_id, s.convert_order_time, s.formal_version, s.last_transfer_time " +
            "FROM sales_order o LEFT JOIN sales_sample_order s ON s.order_id=o.order_id AND s.deleted=0 " +
            "WHERE o.order_id=#{id} AND o.deleted=0")
    SalesOrder selectById(Serializable id);

    /** 样品字段拆表后的统一更新入口；公共字段写主表，样品字段写扩展表。 */
    @Override
    @Update("<script>UPDATE sales_order o LEFT JOIN sales_sample_order s ON s.order_id=o.order_id " +
            "SET o.update_time=NOW() " +
            "<if test='entity.remark != null'>,o.remark=#{entity.remark}</if> " +
            "<if test='entity.orderStatus != null'>,o.order_status=#{entity.orderStatus}</if> " +
            "<if test='entity.convertedOrderId != null'>,s.converted_order_id=#{entity.convertedOrderId}</if> " +
            "<if test='entity.sampleStatus != null'>,s.sample_status=#{entity.sampleStatus}</if> " +
            "<if test='entity.sampleRound != null'>,s.sample_round=#{entity.sampleRound}</if> " +
            "<if test='entity.sampleQty != null'>,s.sample_qty=#{entity.sampleQty}</if> " +
            "<if test='entity.engineeringNote != null'>,s.engineering_note=#{entity.engineeringNote}</if> " +
            "<if test='entity.engineeringAcceptor != null'>,s.engineering_acceptor=#{entity.engineeringAcceptor}</if> " +
            "<if test='entity.rejectReason != null'>,s.reject_reason=#{entity.rejectReason}</if> " +
            "<if test='entity.currentProcess != null'>,s.current_process=#{entity.currentProcess}</if> " +
            "<if test='entity.sampleCost != null'>,s.sample_cost=#{entity.sampleCost}</if> " +
            "<if test='entity.sampleWorkHours != null'>,s.sample_work_hours=#{entity.sampleWorkHours}</if> " +
            "<if test='entity.sampleTrackingNo != null'>,s.sample_tracking_no=#{entity.sampleTrackingNo}</if> " +
            "<if test='entity.sampleSendDate != null'>,s.sample_send_date=#{entity.sampleSendDate}</if> " +
            "<if test='entity.sampleConfirmDate != null'>,s.sample_confirm_date=#{entity.sampleConfirmDate}</if> " +
            "<if test='entity.confirmBy != null'>,s.confirm_by=#{entity.confirmBy}</if> " +
            "<if test='entity.confirmMethod != null'>,s.confirm_method=#{entity.confirmMethod}</if> " +
            "<if test='entity.confirmTime != null'>,s.confirm_time=#{entity.confirmTime}</if> " +
            "<if test='entity.confirmSentTime != null'>,s.confirm_sent_time=#{entity.confirmSentTime}</if> " +
            "<if test='entity.sampleClientName != null'>,s.sample_client_name=#{entity.sampleClientName}</if> " +
            "<if test='entity.convertOrderTime != null'>,s.convert_order_time=#{entity.convertOrderTime}</if> " +
            "<if test='entity.formalVersion != null'>,s.formal_version=#{entity.formalVersion}</if> " +
            "<if test='entity.lastTransferTime != null'>,s.last_transfer_time=#{entity.lastTransferTime}</if> " +
            "WHERE o.order_id=#{entity.orderId} AND o.deleted=0</script>")
    int updateById(@org.apache.ibatis.annotations.Param("entity") SalesOrder entity);

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
    @Update("UPDATE sales_sample_order SET sample_status = #{newStatus}, update_time = NOW() " +
            "WHERE order_id = #{orderId} AND sample_status = #{oldStatus} AND deleted = 0")
    int updateSampleStatus(@Param("orderId") Long orderId,
                           @Param("oldStatus") Integer oldStatus,
                           @Param("newStatus") Integer newStatus);

    /**
     * 工程接单：状态推进与接单信息一次性落库，且仅允许尚未接单的数据更新。
     */
    @Update("UPDATE sales_sample_order SET sample_status = #{engineeringStatus}, " +
            "engineering_acceptor = #{acceptorName}, engineering_accept_time = NOW(), update_time = NOW() " +
            "WHERE order_id = #{orderId} AND deleted = 0 " +
            "AND sample_status IN (#{requestStatus}, #{engineeringStatus}) " +
            "AND (engineering_acceptor IS NULL OR engineering_acceptor = '')")
    int acceptEngineering(@Param("orderId") Long orderId,
                          @Param("requestStatus") Integer requestStatus,
                          @Param("engineeringStatus") Integer engineeringStatus,
                          @Param("acceptorName") String acceptorName);

}
