package com.jjx.production.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.ProductionOperationExecutionCreateDTO;
import com.jjx.production.domain.dto.ProductionOperationExecutionQueryDTO;
import com.jjx.production.domain.dto.ProductionOperationExecutionUpdateDTO;
import com.jjx.production.domain.vo.ProductionOperationExecutionVO;

import java.util.List;

/**
 * 生产工序执行服务接口
 */
public interface ProductionOperationExecutionService {

    /**
     * 创建工序执行记录
     */
    Long createExecution(ProductionOperationExecutionCreateDTO createDTO);

    /**
     * 更新工序执行记录
     */
    boolean updateExecution(ProductionOperationExecutionUpdateDTO updateDTO);

    /**
     * 删除工序执行记录
     */
    boolean deleteExecution(Long executionId);

    /**
     * 批量删除工序执行记录
     */
    boolean batchDeleteExecution(List<Long> executionIds);

    /**
     * 根据ID获取工序执行详情
     */
    ProductionOperationExecutionVO getExecutionById(Long executionId);

    /**
     * 查询工序执行列表
     */
    List<ProductionOperationExecutionVO> queryExecutionList(ProductionOperationExecutionQueryDTO queryDTO);

    /**
     * 分页查询工序执行
     */
    Page<ProductionOperationExecutionVO> queryExecutionPage(ProductionOperationExecutionQueryDTO queryDTO);

    /**
     * 开始工序执行
     */
    boolean startExecution(Long executionId);

    /**
     * 暂停工序执行
     */
    boolean pauseExecution(Long executionId);

    /**
     * 完成工序执行
     */
    boolean completeExecution(Long executionId);

    /**
     * 取消工序执行
     */
    boolean cancelExecution(Long executionId);

    /**
     * 根据生产工单ID查询工序执行
     */
    List<ProductionOperationExecutionVO> getExecutionsByOrderId(Long orderId);

    /**
     * 根据工序ID查询工序执行
     */
    List<ProductionOperationExecutionVO> getExecutionsByProcessId(Long processId);

    /**
     * 导入工序执行数据
     */
    Result importExecutionData(List<ProductionOperationExecutionCreateDTO> importData);

    /**
     * 导出工序执行数据
     */
    List<ProductionOperationExecutionVO> exportExecutionData(ProductionOperationExecutionQueryDTO queryDTO);

    /**
     * 获取工序执行统计信息
     */
    Result getExecutionStatistics(ProductionOperationExecutionQueryDTO queryDTO);
}
