package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.OrderReviewRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单审核记录Mapper接口
 */
@Mapper
public interface OrderReviewRecordMapper extends BaseMapper<OrderReviewRecord> {

    /**
     * 根据订单ID查询审核记录列表
     *
     * @param orderId 订单ID
     * @return 审核记录列表
     */
    @Select("SELECT * FROM order_review_record WHERE order_id = #{orderId} AND deleted = 0 ORDER BY create_time DESC")
    List<OrderReviewRecord> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据订单ID和审核阶段查询审核记录
     *
     * @param orderId 订单ID
     * @param reviewStage 审核阶段
     * @return 审核记录
     */
    @Select("SELECT * FROM order_review_record WHERE order_id = #{orderId} AND review_stage = #{reviewStage} AND deleted = 0 ORDER BY create_time DESC LIMIT 1")
    OrderReviewRecord selectByOrderIdAndStage(@Param("orderId") Long orderId, @Param("reviewStage") Integer reviewStage);

    /**
     * 根据审核人ID查询待审核记录
     *
     * @param reviewerId 审核人ID
     * @return 审核记录列表
     */
    @Select("SELECT * FROM order_review_record WHERE reviewer_id = #{reviewerId} AND review_result IS NULL AND deleted = 0 ORDER BY create_time DESC")
    List<OrderReviewRecord> selectPendingByReviewerId(@Param("reviewerId") Long reviewerId);

    /**
     * 根据订单ID查询当前审核记录
     *
     * @param orderId 订单ID
     * @return 当前审核记录
     */
    @Select("SELECT * FROM order_review_record WHERE order_id = #{orderId} AND review_result IS NULL AND deleted = 0 ORDER BY create_time DESC LIMIT 1")
    OrderReviewRecord selectCurrentReview(@Param("orderId") Long orderId);

    /**
     * 根据订单ID查询审核历史
     *
     * @param orderId 订单ID
     * @return 审核历史记录
     */
    @Select("SELECT * FROM order_review_record WHERE order_id = #{orderId} AND review_result IS NOT NULL AND deleted = 0 ORDER BY create_time DESC")
    List<OrderReviewRecord> selectReviewHistory(@Param("orderId") Long orderId);

    /**
     * 根据审核流程ID查询审核记录
     *
     * @param reviewProcessId 审核流程ID
     * @return 审核记录列表
     */
    @Select("SELECT * FROM order_review_record WHERE review_process_id = #{reviewProcessId} AND deleted = 0 ORDER BY node_sequence ASC")
    List<OrderReviewRecord> selectByProcessId(@Param("reviewProcessId") String reviewProcessId);

    /**
     * 更新审核结果
     *
     * @param recordId 记录ID
     * @param reviewResult 审核结果
     * @param resultDescription 结果描述
     * @param reviewDuration 审核耗时
     * @return 更新结果
     */
    @Select("UPDATE order_review_record SET review_result = #{reviewResult}, result_description = #{resultDescription}, review_duration = #{reviewDuration}, review_time = NOW() WHERE record_id = #{recordId}")
    int updateReviewResult(@Param("recordId") Long recordId, @Param("reviewResult") Integer reviewResult,
                           @Param("resultDescription") String resultDescription, @Param("reviewDuration") Integer reviewDuration);

    /**
     * 检查订单是否有未完成的审核
     *
     * @param orderId 订单ID
     * @return 是否有未完成的审核
     */
    @Select("SELECT COUNT(*) FROM order_review_record WHERE order_id = #{orderId} AND review_result IS NULL AND deleted = 0")
    int hasPendingReview(@Param("orderId") Long orderId);

    /**
     * 获取审核统计信息
     *
     * @param reviewerId 审核人ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 统计信息
     */
    @Select("SELECT " +
            "COUNT(*) as total_reviews, " +
            "SUM(CASE WHEN review_result = 1 THEN 1 ELSE 0 END) as approved_count, " +
            "SUM(CASE WHEN review_result = 2 THEN 1 ELSE 0 END) as rejected_count, " +
            "SUM(CASE WHEN review_result = 3 THEN 1 ELSE 0 END) as returned_count, " +
            "AVG(review_duration) as avg_duration " +
            "FROM order_review_record " +
            "WHERE reviewer_id = #{reviewerId} " +
            "AND review_time BETWEEN #{startDate} AND #{endDate} " +
            "AND deleted = 0")
    Object getReviewStatistics(@Param("reviewerId") Long reviewerId, @Param("startDate") String startDate,
                               @Param("endDate") String endDate);

    /**
     * 获取超时审核记录
     *
     * @param timeoutHours 超时小时数
     * @return 超时审核记录列表
     */
    @Select("SELECT * FROM order_review_record " +
            "WHERE review_result IS NULL " +
            "AND TIMESTAMPDIFF(HOUR, create_time, NOW()) > #{timeoutHours} " +
            "AND deleted = 0")
    List<OrderReviewRecord> selectTimeoutReviews(@Param("timeoutHours") Integer timeoutHours);

    /**
     * 获取审核流程进度
     *
     * @param reviewProcessId 审核流程ID
     * @return 进度信息
     */
    @Select("SELECT " +
            "COUNT(*) as total_nodes, " +
            "SUM(CASE WHEN review_result IS NOT NULL THEN 1 ELSE 0 END) as completed_nodes, " +
            "MIN(CASE WHEN review_result IS NULL THEN node_sequence END) as current_node " +
            "FROM order_review_record " +
            "WHERE review_process_id = #{reviewProcessId} " +
            "AND deleted = 0")
    Object getProcessProgress(@Param("reviewProcessId") String reviewProcessId);
}
