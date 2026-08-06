package com.jjx.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.dto.query.TransactionQueryDTO;
import com.jjx.inventory.dto.vo.TransactionVO;

import java.util.List;
import java.util.Map;

/**
 * 库存流水服务接口
 */
public interface InventoryTransactionService extends IService<InventoryTransaction> {

    /**
     * 分页查询库存流水
     */
    IPage<TransactionVO> page(TransactionQueryDTO query);

    /**
     * 记录库存变更流水
     */
    void recordTransaction(InventoryTransaction transaction);

    /**
     * 根据来源单据查询流水
     */
    List<TransactionVO> getBySource(String sourceType, Long sourceId);

    /**
     * 根据单据号查询流水（DEV-661：出入库详情展示用，按 source_no 精确匹配）
     */
    List<TransactionVO> getByDocNo(String docNo);

    /**
     * 查询指定物料的流水记录
     */
    List<TransactionVO> getByMaterial(Long materialId, int limit);

    /**
     * 查询指定时间范围内的流水
     */
    List<TransactionVO> getByTimeRange(String startTime, String endTime);

    /**
     * 统计指定物料的出入库数量
     */
    Map<String, Object> statByMaterial(Long materialId, String startTime);

    /**
     * 获取流水详情
     */
    TransactionVO getById(Long transactionId);

    /**
     * 分页查询库存流水（旧方法，兼容性）
     */
    IPage<InventoryTransaction> pageQuery(Map<String, Object> params);

}
