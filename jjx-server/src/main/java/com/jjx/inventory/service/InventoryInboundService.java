package com.jjx.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.inventory.domain.InventoryInboundOrder;
import com.jjx.inventory.dto.query.InboundQueryDTO;
import com.jjx.inventory.dto.query.IqcPendingQueryDTO;
import com.jjx.inventory.dto.query.IqcQuarantineLedgerQueryDTO;
import com.jjx.inventory.dto.save.InboundInspectionSubmitDTO;
import com.jjx.inventory.dto.save.InboundInspectionReviewDTO;
import com.jjx.inventory.dto.save.IqcQuarantineActionDTO;
import com.jjx.inventory.domain.InventoryIqcQuarantine;
import com.jjx.inventory.dto.vo.InboundVO;
import com.jjx.inventory.dto.vo.IqcPendingVO;
import com.jjx.inventory.dto.vo.IqcQuarantineLedgerPageVO;

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

    /** 分页查询 IQC 采购收货单及检验进度。 */
    IPage<IqcPendingVO> pageIqcPending(IqcPendingQueryDTO query);

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

    /** 单项 IQC 审核通过，只锁定质量结论，不执行库存过账。 */
    boolean approveInspectionItem(Long itemId, InboundInspectionReviewDTO review);

    /**
     * 手工重算入库单审核状态，给「明细已全部审核、单据状态未推进」的历史单据收尾（幂等）。
     * 2026-09-21 dev-20260921-003。
     */
    boolean syncReviewStatus(Long inboundId);

    /** 单项 IQC 驳回，保留原检验记录供检验员修改后重新提交。 */
    boolean rejectInspectionItem(Long itemId, InboundInspectionReviewDTO review);

    /** 对已审核的单项 IQC 发起新版本复检。 */
    Long reinspectItem(Long itemId);

    List<InventoryIqcQuarantine> listQuarantine(Long inboundId);

    boolean handleQuarantine(Long quarantineId, IqcQuarantineActionDTO action);
    List<com.jjx.inventory.domain.InventoryIqcDispositionOrder> listDispositionOrders(Long inboundId);
    List<com.jjx.inventory.domain.InventoryIqcQuarantine> listAllQuarantine(String status);
    IqcQuarantineLedgerPageVO pageIqcQuarantineLedger(IqcQuarantineLedgerQueryDTO query);
    List<com.jjx.inventory.domain.InventoryIqcDispositionOrder> listAllDispositionOrders(String action);
    com.jjx.inventory.domain.InventoryIqcDispositionOrder getDispositionOrder(Long dispositionId);
    List<com.jjx.inventory.domain.InventoryIqcReturnOrder> listIqcReturnOrders(Long inboundId);
    List<com.jjx.inventory.domain.InventoryIqcReworkOrder> listIqcReworkOrders(Long inboundId);
    List<com.jjx.inventory.domain.InventoryIqcBatch> listIqcBatches(Long inboundId);
    List<com.jjx.inventory.domain.InventoryIqcScrapOrder> listIqcScrapOrders(Long inboundId);
    boolean approveIqcScrap(Long scrapId, com.jjx.inventory.dto.save.IqcScrapApproveDTO approval);
    Long completeIqcRework(Long reworkId);

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

    /** FQC单次合格数量分批完工入库。 */
    Long createFromProduction(Long workOrderId, Long lotId, java.math.BigDecimal quantity);

    /**
     * 客户拒收回库（按发货单明细自动回冲成品库存）
     */
    Long createSalesRejectInbound(Long deliveryId);

    /**
     * 返工退料预览（dev-20260923-043）：该不良单的补料明细 + 已退量 + 可退量（净耗口径：可退 = 补料 − 已退）。
     * 每行含原发料批次（退料默认回原批次）。
     */
    List<java.util.Map<String, Object>> returnPreview(Long ncrId);

    /**
     * 返工退料入库（dev-20260923-043）：返工/补料剩余料退回仓库。
     *
     * <p>单号 RTN-&lt;工单号&gt;-&lt;NCR号&gt;-&lt;序号&gt;；source_type=QUALITY_NCR；
     * 批次默认回**原发料批次**（补料出库明细上的批次）；自动审批 + 过账（仓库无需手工建单）。
     * 数量不得超过净耗可退量。</p>
     *
     * @return 退料入库单ID
     */
    Long createProductionReturnInbound(Long orderId, Long ncrId,
                                       List<java.util.Map<String, Object>> items, String reason);

    /**
     * 完工入库差额同步（dev-20260917-007）：把工单完工入库数量对齐到 targetQuantity（= 该工单成品检验批累计合格数），只做差额。
     * - 未过账：直接改入库单明细数量；
     * - 已过账：调整库存（+/-delta）并写 ADJUST 流水（带 lotId 可追）；
     * 返回实际调整的差额（可为负）。
     */
    java.math.BigDecimal syncFinishInbound(Long orderId, Long lotId, java.math.BigDecimal targetQuantity, String reason);

    /**
     * dev-20260923（022 收尾）：复检换代后处理原检验批那张入库单 ——
     * 未过账直接作废；已过账生成红冲单（负数量）等仓库确认入库。
     */
    void handleSupersededLotInbound(Long lotId, String reason);

    /**
     * 成品库存定向调整（dev-20260917-008 不良处置联动）：
     * 让步接收（特采）→ +quantity 入良品库存并写 ADJUST 凭证（带 lotId/ncrId + 特采标记）；
     * 报废在"只入合格数"的口径下不产生扣减（不良品从未入良品库），故只记台账。
     * @param deltaQuantity 正数=入库，负数=扣减
     */
    java.math.BigDecimal adjustFinishStock(Long orderId, Long lotId, Long ncrId, java.math.BigDecimal deltaQuantity, String remark);

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
