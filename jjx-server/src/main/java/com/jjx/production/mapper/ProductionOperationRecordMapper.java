package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionOperationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产工序记录 Mapper 接口
 */
@Mapper
public interface ProductionOperationRecordMapper extends BaseMapper<ProductionOperationRecord> {

    /**
     * 根据工序执行ID查询记录列表
     *
     * @param executionId 工序执行ID
     * @return 记录列表
     */
    List<ProductionOperationRecord> selectByExecutionId(@Param("executionId") Long executionId);

    /**
     * 根据工序执行ID和记录类型查询记录
     *
     * @param executionId 工序执行ID
     * @param recordType 记录类型
     * @return 记录列表
     */
    List<ProductionOperationRecord> selectByExecutionIdAndType(@Param("executionId") Long executionId, @Param("recordType") String recordType);

    /**
     * 根据操作员ID查询记录
     *
     * @param operatorId 操作员ID
     * @return 记录列表
     */
    List<ProductionOperationRecord> selectByOperatorId(@Param("operatorId") Long operatorId);

    /**
     * 根据记录类型查询记录
     *
     * @param recordType 记录类型
     * @return 记录列表
     */
    List<ProductionOperationRecord> selectByRecordType(@Param("recordType") String recordType);

    /**
     * 根据时间范围查询记录
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 记录列表
     */
    List<ProductionOperationRecord> selectByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 查询有问题的记录
     *
     * @return 问题记录列表
     */
    List<ProductionOperationRecord> selectIssueRecords();

    /**
     * 查询有附件的记录
     *
     * @return 有附件记录列表
     */
    List<ProductionOperationRecord> selectRecordsWithAttachment();

    /**
     * 查询工序执行的完整操作历史
     *
     * @param executionId 工序执行ID
     * @return 操作历史记录
     */
    List<OperationHistory> selectOperationHistory(@Param("executionId") Long executionId);

    /**
     * 统计工序执行的操作次数
     *
     * @param executionId 工序执行ID
     * @return 操作统计
     */
    OperationCount countOperations(@Param("executionId") Long executionId);

    /**
     * 统计操作员的操作记录
     *
     * @param operatorId 操作员ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 操作员统计
     */
    OperatorRecordStats getOperatorRecordStats(@Param("operatorId") Long operatorId,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    /**
     * 批量插入记录
     *
     * @param records 记录列表
     * @return 插入数量
     */
    int insertBatch(@Param("records") List<ProductionOperationRecord> records);

    /**
     * 删除工序执行的所有记录
     *
     * @param executionId 工序执行ID
     * @return 删除数量
     */
    int deleteByExecutionId(@Param("executionId") Long executionId);

    /**
     * 更新记录附件
     *
     * @param recordId 记录ID
     * @param attachmentUrl 附件URL
     * @return 更新数量
     */
    int updateAttachment(@Param("recordId") Long recordId, @Param("attachmentUrl") String attachmentUrl);

    /**
     * 更新问题解决方案
     *
     * @param recordId 记录ID
     * @param issueSolution 解决方案
     * @return 更新数量
     */
    int updateIssueSolution(@Param("recordId") Long recordId, @Param("issueSolution") String issueSolution);

    /**
     * 操作历史类（包含关联信息）
     */
    class OperationHistory {
        private ProductionOperationRecord record;
        private String executionInfo;
        private String operatorInfo;
        private String equipmentInfo;

        public ProductionOperationRecord getRecord() {
            return record;
        }

        public void setRecord(ProductionOperationRecord record) {
            this.record = record;
        }

        public String getExecutionInfo() {
            return executionInfo;
        }

        public void setExecutionInfo(String executionInfo) {
            this.executionInfo = executionInfo;
        }

        public String getOperatorInfo() {
            return operatorInfo;
        }

        public void setOperatorInfo(String operatorInfo) {
            this.operatorInfo = operatorInfo;
        }

        public String getEquipmentInfo() {
            return equipmentInfo;
        }

        public void setEquipmentInfo(String equipmentInfo) {
            this.equipmentInfo = equipmentInfo;
        }
    }

    /**
     * 操作统计类
     */
    class OperationCount {
        private Long totalRecords;
        private Long startCount;
        private Long pauseCount;
        private Long resumeCount;
        private Long completeCount;
        private Long qualityCount;
        private Long issueCount;
        private Long paramCount;
        private Long statusCount;

        public Long getTotalRecords() {
            return totalRecords;
        }

        public void setTotalRecords(Long totalRecords) {
            this.totalRecords = totalRecords;
        }

        public Long getStartCount() {
            return startCount;
        }

        public void setStartCount(Long startCount) {
            this.startCount = startCount;
        }

        public Long getPauseCount() {
            return pauseCount;
        }

        public void setPauseCount(Long pauseCount) {
            this.pauseCount = pauseCount;
        }

        public Long getResumeCount() {
            return resumeCount;
        }

        public void setResumeCount(Long resumeCount) {
            this.resumeCount = resumeCount;
        }

        public Long getCompleteCount() {
            return completeCount;
        }

        public void setCompleteCount(Long completeCount) {
            this.completeCount = completeCount;
        }

        public Long getQualityCount() {
            return qualityCount;
        }

        public void setQualityCount(Long qualityCount) {
            this.qualityCount = qualityCount;
        }

        public Long getIssueCount() {
            return issueCount;
        }

        public void setIssueCount(Long issueCount) {
            this.issueCount = issueCount;
        }

        public Long getParamCount() {
            return paramCount;
        }

        public void setParamCount(Long paramCount) {
            this.paramCount = paramCount;
        }

        public Long getStatusCount() {
            return statusCount;
        }

        public void setStatusCount(Long statusCount) {
            this.statusCount = statusCount;
        }
    }

    /**
     * 操作员记录统计类
     */
    class OperatorRecordStats {
        private Long operatorId;
        private String operatorName;
        private Long totalRecords;
        private Long operationRecords;
        private Long dataRecords;
        private Long issueRecords;
        private Long qualityRecords;
        private BigDecimal avgResponseTime;

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

        public Long getTotalRecords() {
            return totalRecords;
        }

        public void setTotalRecords(Long totalRecords) {
            this.totalRecords = totalRecords;
        }

        public Long getOperationRecords() {
            return operationRecords;
        }

        public void setOperationRecords(Long operationRecords) {
            this.operationRecords = operationRecords;
        }

        public Long getDataRecords() {
            return dataRecords;
        }

        public void setDataRecords(Long dataRecords) {
            this.dataRecords = dataRecords;
        }

        public Long getIssueRecords() {
            return issueRecords;
        }

        public void setIssueRecords(Long issueRecords) {
            this.issueRecords = issueRecords;
        }

        public Long getQualityRecords() {
            return qualityRecords;
        }

        public void setQualityRecords(Long qualityRecords) {
            this.qualityRecords = qualityRecords;
        }

        public BigDecimal getAvgResponseTime() {
            return avgResponseTime;
        }

        public void setAvgResponseTime(BigDecimal avgResponseTime) {
            this.avgResponseTime = avgResponseTime;
        }
    }
}
