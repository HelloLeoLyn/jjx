package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.ProductionOperationRecordCreateDTO;
import com.jjx.production.domain.dto.ProductionOperationRecordQueryDTO;
import com.jjx.production.domain.dto.ProductionOperationRecordUpdateDTO;
import com.jjx.production.domain.entity.ProductionOperationRecord;
import com.jjx.production.domain.vo.ProductionOperationExecutionVO;
import com.jjx.production.domain.vo.ProductionOperationRecordVO;
import com.jjx.production.mapper.ProductionOperationRecordMapper;
import com.jjx.production.service.ProductionOperationExecutionService;
import com.jjx.production.service.ProductionOperationRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 生产工序记录服务实现类
 */
@Slf4j
@Service
public class ProductionOperationRecordServiceImpl extends ServiceImpl<ProductionOperationRecordMapper, ProductionOperationRecord>
        implements ProductionOperationRecordService {

    private final ProductionOperationRecordMapper productionOperationRecordMapper;
    private final ProductionOperationExecutionService productionOperationExecutionService;

    public ProductionOperationRecordServiceImpl(ProductionOperationRecordMapper productionOperationRecordMapper,
                                                @Lazy ProductionOperationExecutionService productionOperationExecutionService) {
        this.productionOperationRecordMapper = productionOperationRecordMapper;
        this.productionOperationExecutionService = productionOperationExecutionService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRecord(ProductionOperationRecordCreateDTO createDTO) {
        log.info("创建工序记录: {}", createDTO);

        // 验证数据
        validateRecordData(createDTO);

        // 转换为实体
        ProductionOperationRecord record = convertCreateDTOToEntity(createDTO);

        // 保存到数据库
        boolean success = save(record);
        if (!success) {
            throw new BusinessException("创建工序记录失败");
        }

        log.info("工序记录创建成功, ID: {}", record.getRecordId());
        return record.getRecordId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRecord(ProductionOperationRecordUpdateDTO updateDTO) {
        log.info("更新工序记录: {}", updateDTO);

        // 检查记录是否存在
        ProductionOperationRecord record = getById(updateDTO.getRecordId());
        if (record == null) {
            throw new BusinessException("工序记录不存在: " + updateDTO.getRecordId());
        }

        // 更新实体
        updateEntityFromUpdateDTO(record, updateDTO);

        // 更新到数据库
        boolean success = updateById(record);
        if (!success) {
            throw new BusinessException("更新工序记录失败");
        }

        log.info("工序记录更新成功, ID: {}", record.getRecordId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRecord(Long recordId) {
        log.info("删除工序记录: {}", recordId);

        // 检查记录是否存在
        ProductionOperationRecord record = getById(recordId);
        if (record == null) {
            throw new BusinessException("工序记录不存在: " + recordId);
        }

        // 删除记录
        boolean success = removeById(recordId);
        if (!success) {
            throw new BusinessException("删除工序记录失败");
        }

        log.info("工序记录删除成功, ID: {}", recordId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeleteRecord(List<Long> recordIds) {
        log.info("批量删除工序记录: {}", recordIds);

        if (recordIds == null || recordIds.isEmpty()) {
            throw new BusinessException("记录ID列表不能为空");
        }

        // 批量删除
        boolean success = removeByIds(recordIds);
        if (!success) {
            throw new BusinessException("批量删除工序记录失败");
        }

        log.info("批量删除工序记录成功, 数量: {}", recordIds.size());
        return true;
    }

    @Override
    public ProductionOperationRecordVO getRecordById(Long recordId) {
        log.debug("根据ID获取工序记录详情: {}", recordId);

        ProductionOperationRecord record = getById(recordId);
        if (record == null) {
            throw new BusinessException("工序记录不存在: " + recordId);
        }

        return convertToVO(record);
    }

    @Override
    public List<ProductionOperationRecordVO> queryRecordList(ProductionOperationRecordQueryDTO queryDTO) {
        log.debug("查询工序记录列表: {}", queryDTO);

        // 处理orderId和processId查询条件
//        if (queryDTO.getOrderId() != null || queryDTO.getProcessId() != null) {
//            // 使用关联查询的方式
//            return queryRecordListWithAssociation(queryDTO);
//        }

        LambdaQueryWrapper<ProductionOperationRecord> wrapper = buildQueryWrapper(queryDTO);
        wrapper.orderByDesc(ProductionOperationRecord::getRecordTime);

        List<ProductionOperationRecord> records = list(wrapper);
        return records.stream()
                .map(ProductionOperationRecordServiceImpl::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ProductionOperationRecordVO> queryRecordPage(ProductionOperationRecordQueryDTO queryDTO) {
        log.debug("分页查询工序记录: {}", queryDTO);

        // 构建查询条件
        LambdaQueryWrapper<ProductionOperationRecord> wrapper = buildQueryWrapper(queryDTO);

        // 设置排序
        wrapper.orderByDesc(ProductionOperationRecord::getRecordTime);

        // 分页查询
        Page<ProductionOperationRecord> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<ProductionOperationRecord> recordPage = page(page, wrapper);

        // 转换为VO分页
        Page<ProductionOperationRecordVO> voPage = new Page<>(recordPage.getCurrent(), recordPage.getSize(), recordPage.getTotal());
        List<ProductionOperationRecordVO> voList = recordPage.getRecords().stream()
                .map(ProductionOperationRecordServiceImpl::convertToVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public List<ProductionOperationRecordVO> getRecordsByExecutionId(Long executionId) {
        log.debug("根据工序执行ID查询工序记录: {}", executionId);

        LambdaQueryWrapper<ProductionOperationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductionOperationRecord::getExecutionId, executionId)
                .orderByDesc(ProductionOperationRecord::getRecordTime);

        List<ProductionOperationRecord> records = list(wrapper);
        return records.stream()
                .map(ProductionOperationRecordServiceImpl::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductionOperationRecordVO> getRecordsByOrderId(Long orderId) {
        log.debug("根据生产工单ID查询工序记录: {}", orderId);

        // 由于ProductionOperationRecord实体没有orderId字段，需要通过execution_id关联查询
        // 1. 先查询该工单的所有工序执行记录
        List<Long> executionIds = getExecutionIdsByOrderId(orderId);

        if (executionIds.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        // 2. 查询这些工序执行记录对应的所有工序记录
        LambdaQueryWrapper<ProductionOperationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ProductionOperationRecord::getExecutionId, executionIds)
                .orderByDesc(ProductionOperationRecord::getRecordTime);

        List<ProductionOperationRecord> records = list(wrapper);
        return records.stream()
                .map(ProductionOperationRecordServiceImpl::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductionOperationRecordVO> getRecordsByProcessId(Long processId) {
        log.debug("根据工序ID查询工序记录: {}", processId);

        // 由于ProductionOperationRecord实体没有processId字段，需要通过execution_id关联查询
        // 1. 先查询该工序的所有工序执行记录
        List<Long> executionIds = getExecutionIdsByProcessId(processId);

        if (executionIds.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        // 2. 查询这些工序执行记录对应的所有工序记录
        LambdaQueryWrapper<ProductionOperationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ProductionOperationRecord::getExecutionId, executionIds)
                .orderByDesc(ProductionOperationRecord::getRecordTime);

        List<ProductionOperationRecord> records = list(wrapper);
        return records.stream()
                .map(ProductionOperationRecordServiceImpl::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result importRecordData(List<ProductionOperationRecordCreateDTO> importData) {
        log.info("导入工序记录数据, 数量: {}", importData.size());

        if (importData == null || importData.isEmpty()) {
            return Result.error("导入数据不能为空");
        }

        int successCount = 0;
        int failCount = 0;
        List<String> failMessages = new java.util.ArrayList<>();

        for (int i = 0; i < importData.size(); i++) {
            ProductionOperationRecordCreateDTO dto = importData.get(i);
            try {
                // 验证数据
                validateRecordData(dto);

                // 转换为实体并保存
                ProductionOperationRecord record = convertCreateDTOToEntity(dto);
                save(record);

                successCount++;
            } catch (Exception e) {
                failCount++;
                String message = String.format("第%d行导入失败: %s", i + 1, e.getMessage());
                failMessages.add(message);
                log.error(message, e);
            }
        }

        String resultMessage = String.format("导入完成: 成功%d条, 失败%d条", successCount, failCount);
        log.info(resultMessage);

        if (failCount > 0) {
            // 创建一个包含失败消息的Map作为data
            java.util.Map<String, Object> resultData = new java.util.HashMap<>();
            resultData.put("successCount", successCount);
            resultData.put("failCount", failCount);
            resultData.put("failMessages", failMessages);
            Result<Object> result = Result.error(resultMessage);
            result.setData(resultData);
            return result;
        } else {
            return Result.success(resultMessage);
        }
    }

    @Override
    public List<ProductionOperationRecordVO> exportRecordData(ProductionOperationRecordQueryDTO queryDTO) {
        log.debug("导出工序记录数据: {}", queryDTO);

        // 处理orderId和processId查询条件
//        if (queryDTO.getOrderId() != null || queryDTO.getProcessId() != null) {
//            // 使用关联查询的方式
//            return queryRecordListWithAssociation(queryDTO);
//        }

        LambdaQueryWrapper<ProductionOperationRecord> wrapper = buildQueryWrapper(queryDTO);
        wrapper.orderByDesc(ProductionOperationRecord::getRecordTime);

        List<ProductionOperationRecord> records = list(wrapper);
        return records.stream()
                .map(ProductionOperationRecordServiceImpl::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public Result getRecordStatistics(ProductionOperationRecordQueryDTO queryDTO) {
        log.debug("获取工序记录统计信息: {}", queryDTO);



        // 构建查询条件
        LambdaQueryWrapper<ProductionOperationRecord> wrapper = buildQueryWrapper(queryDTO);

        // 获取统计数据
        long totalCount = count(wrapper);

        // 按记录类型统计
        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOperationRecord::getRecordType, "START");
        long startCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOperationRecord::getRecordType, "COMPLETE");
        long completeCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOperationRecord::getRecordType, "QUALITY");
        long qualityCount = count(wrapper);

        wrapper = buildQueryWrapper(queryDTO);
        wrapper.eq(ProductionOperationRecord::getRecordType, "ISSUE");
        long issueCount = count(wrapper);

        // 构建统计结果
        java.util.Map<String, Object> statistics = new java.util.HashMap<>();
        statistics.put("totalCount", totalCount);
        statistics.put("startCount", startCount);
        statistics.put("completeCount", completeCount);
        statistics.put("qualityCount", qualityCount);
        statistics.put("issueCount", issueCount);

        return Result.success(statistics);
    }

    // ============ 私有方法 ============

    /**
     * 根据工单ID获取工序执行ID列表
     */
    private List<Long> getExecutionIdsByOrderId(Long orderId) {
        // 调用ProductionOperationExecutionService获取该工单的所有工序执行记录
        List<ProductionOperationExecutionVO> executions = productionOperationExecutionService.getExecutionsByOrderId(orderId);

        // 提取executionId列表
        return executions.stream()
                .map(ProductionOperationExecutionVO::getExecutionId)
                .collect(Collectors.toList());
    }

    /**
     * 根据工序ID获取工序执行ID列表
     */
    private List<Long> getExecutionIdsByProcessId(Long processId) {
        // 调用ProductionOperationExecutionService获取该工序的所有工序执行记录
        List<ProductionOperationExecutionVO> executions = productionOperationExecutionService.getExecutionsByProcessId(processId);

        // 提取executionId列表
        return executions.stream()
                .map(ProductionOperationExecutionVO::getExecutionId)
                .collect(Collectors.toList());
    }

    /**
     * 使用关联查询方式查询工序记录列表
     */
    private List<ProductionOperationRecordVO> queryRecordListWithAssociation(ProductionOperationRecordQueryDTO queryDTO) {
        List<Long> executionIds = new java.util.ArrayList<>();

        // 根据orderId获取executionIds
        if (queryDTO.getOrderId() != null) {
            executionIds.addAll(getExecutionIdsByOrderId(queryDTO.getOrderId()));
        }

        // 根据processId获取executionIds
//        if (queryDTO.getProcessId() != null) {
//            executionIds.addAll(getExecutionIdsByProcessId(queryDTO.getProcessId()));
//        }

        // 去重
        executionIds = executionIds.stream().distinct().collect(Collectors.toList());

        if (executionIds.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        // 构建查询条件
        LambdaQueryWrapper<ProductionOperationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ProductionOperationRecord::getExecutionId, executionIds);

        if (queryDTO.getRecordType() != null) {
            wrapper.eq(ProductionOperationRecord::getRecordType, queryDTO.getRecordType());
        }

        wrapper.orderByDesc(ProductionOperationRecord::getRecordTime);

        List<ProductionOperationRecord> records = list(wrapper);
        return records.stream()
                .map(ProductionOperationRecordServiceImpl::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 构建查询条件
     */
    private static LambdaQueryWrapper<ProductionOperationRecord> buildQueryWrapper(ProductionOperationRecordQueryDTO queryDTO) {
        LambdaQueryWrapper<ProductionOperationRecord> wrapper = new LambdaQueryWrapper<>();

        if (queryDTO.getExecutionId() != null) {
            wrapper.eq(ProductionOperationRecord::getExecutionId, queryDTO.getExecutionId());
        }
        // 注意：ProductionOperationRecord实体没有orderId和processId字段
        // 这些查询条件需要在Service层通过关联查询处理
        // 这里只处理executionId和recordType的查询
        if (queryDTO.getRecordType() != null) {
            wrapper.eq(ProductionOperationRecord::getRecordType, queryDTO.getRecordType());
        }

        return wrapper;
    }

    /**
     * 验证工序记录数据
     */
    private static void validateRecordData(ProductionOperationRecordCreateDTO createDTO) {
        if (createDTO.getExecutionId() == null) {
            throw new BusinessException("工序执行ID不能为空");
        }
        if (createDTO.getRecordType() == null) {
            throw new BusinessException("记录类型不能为空");
        }
        if (createDTO.getRecordTime() == null) {
            throw new BusinessException("记录时间不能为空");
        }
    }

    /**
     * 转换为VO
     */
    private static ProductionOperationRecordVO convertToVO(ProductionOperationRecord record) {
        ProductionOperationRecordVO vo = new ProductionOperationRecordVO();

        // 复制基本字段
        vo.setRecordId(record.getRecordId());
        vo.setExecutionId(record.getExecutionId());
        vo.setRecordType(record.getRecordType());
        vo.setRecordTime(record.getRecordTime());
        vo.setOperatorId(record.getOperatorId());
        vo.setOperatorName(record.getOperatorName());
        vo.setQuantity(record.getQuantity());
        vo.setQualityData(record.getQualityData());
        vo.setIssueDescription(record.getIssueDescription());
        vo.setIssueSolution(record.getIssueSolution());
        vo.setParameters(record.getParameters());
        vo.setRemark(record.getRemark());

        // 设置记录类型描述
//        vo.setRecordTypeDesc(getRecordTypeDesc(record.getRecordType()));

        return vo;
    }

    /**
     * 将CreateDTO转换为实体
     */
    private static ProductionOperationRecord convertCreateDTOToEntity(ProductionOperationRecordCreateDTO createDTO) {
        ProductionOperationRecord record = new ProductionOperationRecord();

        record.setExecutionId(createDTO.getExecutionId());
//        record.setOrderId(createDTO.getOrderId());
//        record.setProcessId(createDTO.getProcessId());
        record.setRecordType(createDTO.getRecordType());
        record.setRecordTime(createDTO.getRecordTime());
        record.setOperatorId(createDTO.getOperatorId());
        record.setOperatorName(createDTO.getOperatorName());
//        record.setQuantity(createDTO.getQuantity());
//        record.setQualityData(createDTO.getQualityData());
//        record.setIssueDescription(createDTO.getIssueDescription());
//        record.setIssueSolution(createDTO.getIssueSolution());
//        record.setParameters(createDTO.getParameters());
        record.setRemark(createDTO.getRemark());

        return record;
    }

    /**
     * 从UpdateDTO更新实体
     */
    private static void updateEntityFromUpdateDTO(ProductionOperationRecord record, ProductionOperationRecordUpdateDTO updateDTO) {
        if (updateDTO.getRecordType() != null) {
            record.setRecordType(updateDTO.getRecordType());
        }
        if (updateDTO.getRecordTime() != null) {
            record.setRecordTime(updateDTO.getRecordTime());
        }
        if (updateDTO.getOperatorId() != null) {
            record.setOperatorId(updateDTO.getOperatorId());
        }
        if (updateDTO.getOperatorName() != null) {
            record.setOperatorName(updateDTO.getOperatorName());
        }

    }

    /**
     * 获取记录类型描述
     */
    private static String getRecordTypeDesc(String recordType) {
        if (recordType == null) {
            return "未知";
        }
        switch (recordType) {
            case "START": return "开始";
            case "COMPLETE": return "完成";
            case "PAUSE": return "暂停";
            case "RESUME": return "恢复";
            case "QUALITY": return "质量检查";
            case "ISSUE": return "问题记录";
            case "PARAM": return "参数调整";
            case "STATUS": return "状态变更";
            default: return "未知";
        }
    }
}
