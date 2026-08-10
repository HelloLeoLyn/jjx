package com.jjx.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.inventory.domain.InventoryOutboundOrder;
import com.jjx.inventory.dto.query.OutboundQueryDTO;
import com.jjx.inventory.dto.vo.OutboundVO;

import java.util.List;
import java.util.Map;

/**
 * 出库服务接口
 */
public interface InventoryOutboundService extends IService<InventoryOutboundOrder> {

    /**
     * 分页查询出库单
     */
    IPage<OutboundVO> page(OutboundQueryDTO query);

    /**
     * 获取出库单详情
     */
    OutboundVO getDetail(Long outboundId);

    /**
     * 创建出库单
     */
    Long create(Map<String, Object> params);

    /**
     * 确认出库（执行库存扣减）
     */
    boolean confirm(Long outboundId, Long operatorId, String operatorName);

    /**
     * 取消出库单
     */
    boolean cancel(Long outboundId, String reason);

    /**
     * 提交审批
     */
    boolean submitApprove(Long outboundId);

    /**
     * 审批通过
     */
    boolean approve(Long outboundId, Long approverId, String approverName, String remark);

    /**
     * 审批驳回
     */
    boolean reject(Long outboundId, Long approverId, String approverName, String remark);

    /**
     * 生产领料（从生产工单创建）
     */
    Long createFromProduction(Long workOrderId);

    /**
     * 追加领料（033定稿：多次领料，去幂等）
     * 出库单号 PICK-{工单号}-{序号}；每次填本次领料数量，Σ累计领料 ≤ BOM需求量
     * @param workOrderId 工单ID
     * @param items 本次领料明细 [{materialId, materialCode, materialName, quantity}]
     * @return 出库单ID
     */
    Long createProductionPick(Long workOrderId, java.util.List<java.util.Map<String, Object>> items);

    /**
     * 查询工单剩余可领料量（033：剩余需求量 = BOM需求量 - Σ已领料量）
     */
    java.util.List<java.util.Map<String, Object>> getPickRemaining(Long workOrderId);

    /**
     * 销售出库（从销售订单创建）
     */
    Long createFromSales(Long salesOrderId);

    /**
     * 查询待审批的出库单
     */
    List<OutboundVO> getPendingApproval();

    /**
     * 查询指定日期范围内的出库单
     */
    List<OutboundVO> getByDateRange(String startDate, String endDate);

    /**
     * 根据来源单据查询出库单
     */
    OutboundVO getBySource(String sourceType, Long sourceId);

    /**
     * 更新出库单状态
     */
    boolean updateStatus(Long outboundId, Integer status);

    /**
     * 更新出库单（含明细，DEV-695）
     */
    boolean update(Map<String, Object> params);

    /**
     * 分页查询出库单（旧方法，兼容性）
     */
    IPage<InventoryOutboundOrder> pageQuery(Map<String, Object> params);

    /**
     * 获取出库单详情（旧方法，兼容性）
     */
    Map<String, Object> getDetail(Map<String, Object> params);

    /**
     * 导出入库单PDF（单张表单）
     */
    byte[] exportPdf(Long outboundId);

}
