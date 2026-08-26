package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 生产工序执行 Mapper 接口
 */
@Mapper
public interface ProductionOperationExecutionMapper extends BaseMapper<ProductionOperationExecution> {

    @Select("SELECT e.execution_id,e.process_order AS processOrder,e.task_seq AS taskSeq,o.order_no AS orderNo "
            + "FROM production_operation_execution e JOIN production_order o ON o.order_id=e.order_id "
            + "WHERE e.execution_id=#{executionId} FOR UPDATE")
    Map<String, Object> selectTaskNoContextForUpdate(@Param("executionId") Long executionId);

    @Update("UPDATE production_operation_execution SET task_seq=task_seq+1 WHERE execution_id=#{executionId}")
    int incrementTaskSeq(@Param("executionId") Long executionId);

    /**
     * 根据订单ID查询工序执行列表
     *
     * @param orderId 订单ID
     * @return 工序执行列表
     */
    List<ProductionOperationExecution> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据订单ID和状态查询工序执行
     *
     * @param orderId 订单ID
     * @param executionStatus 执行状态
     * @return 工序执行列表
     */
    List<ProductionOperationExecution> selectByOrderIdAndStatus(@Param("orderId") Long orderId, @Param("executionStatus") String executionStatus);

    /**
     * 根据工序ID查询工序执行
     *
     * @param processId 工序ID
     * @return 工序执行列表
     */
    List<ProductionOperationExecution> selectByProcessId(@Param("processId") Long processId);

    /**
     * 根据操作员ID查询工序执行
     *
     * @param operatorId 操作员ID
     * @return 工序执行列表
     */
    List<ProductionOperationExecution> selectByOperatorId(@Param("operatorId") Long operatorId);

    /**
     * 根据设备ID查询工序执行
     *
     * @param equipmentId 设备ID
     * @return 工序执行列表
     */
    List<ProductionOperationExecution> selectByEquipmentId(@Param("equipmentId") Long equipmentId);

    /**
     * 查询待执行的工序
     *
     * @return 待执行工序列表
     */
    List<ProductionOperationExecution> selectPending();

    /**
     * 查询执行中的工序
     *
     * @return 执行中工序列表
     */
    List<ProductionOperationExecution> selectProcessing();

    /**
     * 查询已完成的工序
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 已完成工序列表
     */
    List<ProductionOperationExecution> selectCompleted(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 查询已超期的工序
     *
     * @return 已超期工序列表
     */
    List<ProductionOperationExecution> selectOverdue();

    /**
     * 更新工序执行状态
     *
     * @param executionId 执行ID
     * @param executionStatus 执行状态
     * @return 更新数量
     */
    int updateStatus(@Param("executionId") Long executionId, @Param("executionStatus") String executionStatus);

    /**
     * 批量更新工序执行状态
     *
     * @param executionIds 执行ID列表
     * @param executionStatus 执行状态
     * @return 更新数量
     */
    int updateBatchStatus(@Param("executionIds") List<Long> executionIds, @Param("executionStatus") String executionStatus);

    /**
     * 开始工序执行
     *
     * @param executionId 执行ID
     * @param actualStartTime 实际开始时间
     * @return 更新数量
     */
    int startExecution(@Param("executionId") Long executionId, @Param("actualStartTime") LocalDateTime actualStartTime);

    /**
     * 完成工序执行
     *
     * @param executionId 执行ID
     * @param actualEndTime 实际结束时间
     * @param outputQuantity 产出数量
     * @param qualifiedQuantity 合格数量
     * @param defectiveQuantity 不良数量
     * @return 更新数量
     */
    int completeExecution(@Param("executionId") Long executionId,
                         @Param("actualEndTime") LocalDateTime actualEndTime,
                         @Param("outputQuantity") BigDecimal outputQuantity,
                         @Param("qualifiedQuantity") BigDecimal qualifiedQuantity,
                         @Param("defectiveQuantity") BigDecimal defectiveQuantity);

    /**
     * 更新工序产出数据
     *
     * @param executionId 执行ID
     * @param outputQuantity 产出数量
     * @param qualifiedQuantity 合格数量
     * @param defectiveQuantity 不良数量
     * @return 更新数量
     */
    int updateOutput(@Param("executionId") Long executionId,
                    @Param("outputQuantity") BigDecimal outputQuantity,
                    @Param("qualifiedQuantity") BigDecimal qualifiedQuantity,
                    @Param("defectiveQuantity") BigDecimal defectiveQuantity);

    /**
     * 更新工序工时
     *
     * @param executionId 执行ID
     * @param actualLaborHours 实际人工工时
     * @param actualMachineHours 实际机器工时
     * @return 更新数量
     */
    int updateHours(@Param("executionId") Long executionId,
                   @Param("actualLaborHours") BigDecimal actualLaborHours,
                   @Param("actualMachineHours") BigDecimal actualMachineHours);

    /**
     * 统计订单的工序执行情况
     *
     * @param orderId 订单ID
     * @return 执行统计
     */
    OrderExecutionStats getOrderExecutionStats(@Param("orderId") Long orderId);

    /**
     * 统计操作员的工序执行情况
     *
     * @param operatorId 操作员ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 操作员统计
     */
    OperatorStats getOperatorStats(@Param("operatorId") Long operatorId,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 统计设备的工序执行情况
     *
     * @param equipmentId 设备ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 设备统计
     */
    EquipmentStats getEquipmentStats(@Param("equipmentId") Long equipmentId,
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);

    /**
     * 订单执行统计类
     */
    class OrderExecutionStats {
        private Long totalExecutions;
        private Long pendingCount;
        private Long processingCount;
        private Long completedCount;
        private Long skippedCount;
        private BigDecimal totalOutput;
        private BigDecimal totalQualified;
        private BigDecimal totalDefective;
        private BigDecimal totalLaborHours;
        private BigDecimal totalMachineHours;

        public Long getTotalExecutions() {
            return totalExecutions;
        }

        public void setTotalExecutions(Long totalExecutions) {
            this.totalExecutions = totalExecutions;
        }

        public Long getPendingCount() {
            return pendingCount;
        }

        public void setPendingCount(Long pendingCount) {
            this.pendingCount = pendingCount;
        }

        public Long getProcessingCount() {
            return processingCount;
        }

        public void setProcessingCount(Long processingCount) {
            this.processingCount = processingCount;
        }

        public Long getCompletedCount() {
            return completedCount;
        }

        public void setCompletedCount(Long completedCount) {
            this.completedCount = completedCount;
        }

        public Long getSkippedCount() {
            return skippedCount;
        }

        public void setSkippedCount(Long skippedCount) {
            this.skippedCount = skippedCount;
        }

        public BigDecimal getTotalOutput() {
            return totalOutput;
        }

        public void setTotalOutput(BigDecimal totalOutput) {
            this.totalOutput = totalOutput;
        }

        public BigDecimal getTotalQualified() {
            return totalQualified;
        }

        public void setTotalQualified(BigDecimal totalQualified) {
            this.totalQualified = totalQualified;
        }

        public BigDecimal getTotalDefective() {
            return totalDefective;
        }

        public void setTotalDefective(BigDecimal totalDefective) {
            this.totalDefective = totalDefective;
        }

        public BigDecimal getTotalLaborHours() {
            return totalLaborHours;
        }

        public void setTotalLaborHours(BigDecimal totalLaborHours) {
            this.totalLaborHours = totalLaborHours;
        }

        public BigDecimal getTotalMachineHours() {
            return totalMachineHours;
        }

        public void setTotalMachineHours(BigDecimal totalMachineHours) {
            this.totalMachineHours = totalMachineHours;
        }
    }

    /**
     * 操作员统计类
     */
    class OperatorStats {
        private Long operatorId;
        private String operatorName;
        private Long totalExecutions;
        private Long completedCount;
        private BigDecimal totalOutput;
        private BigDecimal totalQualified;
        private BigDecimal totalLaborHours;
        private BigDecimal avgQualifiedRate;

        public Long getOperatorId() {
            return operatorId;
        }

        public void setOperatorId(Long operatorId) {
            this.operatorId = operatorId;
        }

        public String getOperatorName() {
            return operatorName;
        }

        public void setOperatorName(String operatorName) {
            this.operatorName = operatorName;
        }

        public Long getTotalExecutions() {
            return totalExecutions;
        }

        public void setTotalExecutions(Long totalExecutions) {
            this.totalExecutions = totalExecutions;
        }

        public Long getCompletedCount() {
            return completedCount;
        }

        public void setCompletedCount(Long completedCount) {
            this.completedCount = completedCount;
        }

        public BigDecimal getTotalOutput() {
            return totalOutput;
        }

        public void setTotalOutput(BigDecimal totalOutput) {
            this.totalOutput = totalOutput;
        }

        public BigDecimal getTotalQualified() {
            return totalQualified;
        }

        public void setTotalQualified(BigDecimal totalQualified) {
            this.totalQualified = totalQualified;
        }

        public BigDecimal getTotalLaborHours() {
            return totalLaborHours;
        }

        public void setTotalLaborHours(BigDecimal totalLaborHours) {
            this.totalLaborHours = totalLaborHours;
        }

        public BigDecimal getAvgQualifiedRate() {
            return avgQualifiedRate;
        }

        public void setAvgQualifiedRate(BigDecimal avgQualifiedRate) {
            this.avgQualifiedRate = avgQualifiedRate;
        }
    }

    /**
     * 设备统计类
     */
    class EquipmentStats {
        private Long equipmentId;
        private String equipmentCode;
        private String equipmentName;
        private Long totalExecutions;
        private Long completedCount;
        private BigDecimal totalOutput;
        private BigDecimal totalMachineHours;
        private BigDecimal utilizationRate;

        public Long getEquipmentId() {
            return equipmentId;
        }

        public void setEquipmentId(Long equipmentId) {
            this.equipmentId = equipmentId;
        }

        public String getEquipmentCode() {
            return equipmentCode;
        }

        public void setEquipmentCode(String equipmentCode) {
            this.equipmentCode = equipmentCode;
        }

        public String getEquipmentName() {
            return equipmentName;
        }

        public void setEquipmentName(String equipmentName) {
            this.equipmentName = equipmentName;
        }

        public Long getTotalExecutions() {
            return totalExecutions;
        }

        public void setTotalExecutions(Long totalExecutions) {
            this.totalExecutions = totalExecutions;
        }

        public Long getCompletedCount() {
            return completedCount;
        }

        public void setCompletedCount(Long completedCount) {
            this.completedCount = completedCount;
        }

        public BigDecimal getTotalOutput() {
            return totalOutput;
        }

        public void setTotalOutput(BigDecimal totalOutput) {
            this.totalOutput = totalOutput;
        }

        public BigDecimal getTotalMachineHours() {
            return totalMachineHours;
        }

        public void setTotalMachineHours(BigDecimal totalMachineHours) {
            this.totalMachineHours = totalMachineHours;
        }

        public BigDecimal getUtilizationRate() {
            return utilizationRate;
        }

        public void setUtilizationRate(BigDecimal utilizationRate) {
            this.utilizationRate = utilizationRate;
        }
    }
}
