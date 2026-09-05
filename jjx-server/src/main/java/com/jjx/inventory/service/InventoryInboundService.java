package com.jjx.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.inventory.domain.InventoryInboundOrder;
import com.jjx.inventory.dto.query.InboundQueryDTO;
import com.jjx.inventory.dto.save.InboundInspectionSubmitDTO;
import com.jjx.inventory.dto.vo.InboundVO;

import java.util.List;
import java.util.Map;

/**
 * 入库服务接口
 */
public interface InventoryInboundService extends IService<InventoryInboundOrder> {

    /**
     * 分页查询入库单
     */
    IPage<InboundVO> page(InboundQueryDTO query);

    /**
     * 获取入库单详情
     */
    InboundVO getDetail(Long inboundId);

    /**
     * 创建入库单
     */
    Long create(Map<String, Object> params);

    /**
     * 确认入库（执行库存增加）
     */
    boolean confirm(Long inboundId, Long operatorId, String operatorName);

    /**
     * 取消入库单
     */
    boolean cancel(Long inboundId, String reason);

    /**
     * 提交审批
     */
    boolean submitApprove(Long inboundId, InboundInspectionSubmitDTO inspection);

    /**
     * 审批通过
     */
    boolean approve(Long inboundId, Long approverId, String approverName, String remark);

    /**
     * 审批驳回
     */
    boolean reject(Long inboundId, Long approverId, String approverName, String remark);

    /**
     * 采购入库（从采购订单创建）
     */
    Long createFromPurchase(Long purchaseOrderId);

    /**
     * 采购收货自动生成入库单记录（DEV-624）
     * 幂等：PO-单号已存在则返回已有ID；明细=已收数量；不加库存（收货流程已直接加库存，避免重复）
     */
    Long createInboundRecordFromPurchase(Long purchaseOrderId);

    /**
     * 生产入库（从生产工单创建）
     */
    Long createFromProduction(Long workOrderId);

    /**
     * 查询待审批的入库单
     */
    List<InboundVO> getPendingApproval();

    /**
     * 查询指定日期范围内的入库单
     */
    List<InboundVO> getByDateRange(String startDate, String endDate);

    /**
     * 根据来源单据查询入库单
     */
    InboundVO getBySource(String sourceType, Long sourceId);

    /**
     * 更新入库单状态
     */
    boolean updateStatus(Long inboundId, Integer status);

    /**
     * 分页查询入库单（旧方法，兼容性）
     */
    IPage<InventoryInboundOrder> pageQuery(Map<String, Object> params);

    /**
     * 获取入库单详情（旧方法，兼容性）
     */
    Map<String, Object> getDetail(Map<String, Object> params);

    /**
     * 导出入库单PDF（单张表单）
     */

}
