package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产订单 Mapper 接口
 */
@Mapper
public interface ProductionOrderMapper extends BaseMapper<ProductionOrder> {

    /**
     * 根据订单编号查询订单
     *
     * @param orderNo 订单编号
     * @return 订单信息
     */
    ProductionOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 根据销售订单ID查询生产订单
     *
     * @param salesOrderId 销售订单ID
     * @return 生产订单列表
     */
    List<ProductionOrder> selectBySalesOrderId(@Param("salesOrderId") Long salesOrderId);

    /**
     * 根据产品ID查询生产订单
     *
     * @param productId 产品ID
     * @return 生产订单列表
     */
    List<ProductionOrder> selectByProductId(@Param("productId") Long productId);

    /**
     * 根据状态查询生产订单
     *
     * @param orderStatus 订单状态
     * @return 生产订单列表
     */
    List<ProductionOrder> selectByStatus(@Param("orderStatus") Integer orderStatus);

    /**
     * 根据类型查询生产订单
     *
     * @param orderType 订单类型（PLAN/ORDER）
     * @return 生产订单列表
     */
    List<ProductionOrder> selectByType(@Param("orderType") String orderType);

    /**
     * 查询待开始的生产订单
     *
     * @return 待开始订单列表
     */
    List<ProductionOrder> selectPendingStart();

    /**
     * 查询进行中的生产订单
     *
     * @return 进行中订单列表
     */
    List<ProductionOrder> selectInProgress();

    /**
     * 查询已超期的生产订单
     *
     * @return 已超期订单列表
     */
    List<ProductionOrder> selectOverdue();

    /**
     * 根据计划时间范围查询订单
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 订单列表
     */
    List<ProductionOrder> selectByPlanTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 根据实际时间范围查询订单
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 订单列表
     */
    List<ProductionOrder> selectByActualTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 更新订单状态
     *
     * @param orderId 订单ID
     * @param orderStatus 订单状态
     * @return 更新数量
     */
    int updateStatus(@Param("orderId") Long orderId, @Param("orderStatus") Integer orderStatus);

    /**
     * 批量更新订单状态
     *
     * @param orderIds 订单ID列表
     * @param orderStatus 订单状态
     * @return 更新数量
     */
    int updateBatchStatus(@Param("orderIds") List<Long> orderIds, @Param("orderStatus") Integer orderStatus);

    /**
     * 更新订单完成度
     *
     * @param orderId 订单ID
     * @param completionRate 完成度
     * @return 更新数量
     */
    int updateCompletionRate(@Param("orderId") Long orderId, @Param("completionRate") Integer completionRate);

    /**
     * 查询父订单的子订单
     *
     * @param parentOrderId 父订单ID
     * @return 子订单列表
     */
    List<ProductionOrder> selectChildren(@Param("parentOrderId") Long parentOrderId);

    /**
     * 统计各状态的订单数量
     *
     * @return 状态统计列表
     */
    List<StatusCount> countByStatus();

    /**
     * 统计各类型的订单数量
     *
     * @return 类型统计列表
     */
    List<TypeCount> countByType();

    /**
     * 统计月度订单数量
     *
     * @param year 年份
     * @param month 月份
     * @return 月度统计
     */
    MonthlyStats getMonthlyStats(@Param("year") Integer year, @Param("month") Integer month);

    /**
     * 状态统计结果类
     */
    class StatusCount {
        private Integer orderStatus;
        private Long count;

        public Integer getOrderStatus() {
            return orderStatus;
        }

        public void setOrderStatus(Integer orderStatus) {
            this.orderStatus = orderStatus;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }

    /**
     * 类型统计结果类
     */
    class TypeCount {
        private String orderType;
        private Long count;

        public String getOrderType() {
            return orderType;
        }

        public void setOrderType(String orderType) {
            this.orderType = orderType;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }

    /**
     * 月度统计结果类
     */
    class MonthlyStats {
        private Integer totalOrders;
        private Integer completedOrders;
        private Integer inProgressOrders;
        private Integer pendingOrders;
        private Integer overdueOrders;

        public Integer getTotalOrders() {
            return totalOrders;
        }

        public void setTotalOrders(Integer totalOrders) {
            this.totalOrders = totalOrders;
        }

        public Integer getCompletedOrders() {
            return completedOrders;
        }

        public void setCompletedOrders(Integer completedOrders) {
            this.completedOrders = completedOrders;
        }

        public Integer getInProgressOrders() {
            return inProgressOrders;
        }

        public void setInProgressOrders(Integer inProgressOrders) {
            this.inProgressOrders = inProgressOrders;
        }

        public Integer getPendingOrders() {
            return pendingOrders;
        }

        public void setPendingOrders(Integer pendingOrders) {
            this.pendingOrders = pendingOrders;
        }

        public Integer getOverdueOrders() {
            return overdueOrders;
        }

        public void setOverdueOrders(Integer overdueOrders) {
            this.overdueOrders = overdueOrders;
        }
    }
}
