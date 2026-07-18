package com.jjx.production.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.ProductionOperationRecordCreateDTO;
import com.jjx.production.domain.dto.ProductionOperationRecordQueryDTO;
import com.jjx.production.domain.dto.ProductionOperationRecordUpdateDTO;
import com.jjx.production.domain.vo.ProductionOperationRecordVO;

import java.util.List;

/**
 * 生产工序记录服务接口
 */
public interface ProductionOperationRecordService {

    /**
     * 创建工序记录
     */
    Long createRecord(ProductionOperationRecordCreateDTO createDTO);

    /**
     * 更新工序记录
     */
    boolean updateRecord(ProductionOperationRecordUpdateDTO updateDTO);

    /**
     * 删除工序记录
     */
    boolean deleteRecord(Long recordId);

    /**
     * 批量删除工序记录
     */
    boolean batchDeleteRecord(List<Long> recordIds);

    /**
     * 根据ID获取工序记录详情
     */
    ProductionOperationRecordVO getRecordById(Long recordId);

    /**
     * 查询工序记录列表
     */
    List<ProductionOperationRecordVO> queryRecordList(ProductionOperationRecordQueryDTO queryDTO);

    /**
     * 分页查询工序记录
     */
    Page<ProductionOperationRecordVO> queryRecordPage(ProductionOperationRecordQueryDTO queryDTO);

    /**
     * 根据工序执行ID查询工序记录
     */
    List<ProductionOperationRecordVO> getRecordsByExecutionId(Long executionId);

    /**
     * 根据生产工单ID查询工序记录
     */
    List<ProductionOperationRecordVO> getRecordsByOrderId(Long orderId);

    /**
     * 根据工序ID查询工序记录
     */
    List<ProductionOperationRecordVO> getRecordsByProcessId(Long processId);

    /**
     * 导入工序记录数据
     */
    Result importRecordData(List<ProductionOperationRecordCreateDTO> importData);

    /**
     * 导出工序记录数据
     */
    List<ProductionOperationRecordVO> exportRecordData(ProductionOperationRecordQueryDTO queryDTO);

    /**
     * 获取工序记录统计信息
     */
    Result getRecordStatistics(ProductionOperationRecordQueryDTO queryDTO);
}
