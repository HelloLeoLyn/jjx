package com.jjx.sales.service;

import com.jjx.sales.domain.entity.SalesOrder;

import java.util.List;

/**
 * 样品单服务接口
 * 独立于标准订单的样品单生命周期管理
 */
public interface ISampleOrderService {

    /**
     * 从报价单创建样品单
     */
    SalesOrder createFromQuotation(Long quotationId, Integer sampleQty, String remark);

    /**
     * 样品单提交审核
     */
    SalesOrder submitReview(Long orderId);

    /**
     * 样品单审核通过（审核通过后进入工程打样阶段）
     */
    SalesOrder approveReview(Long orderId, String remark);

    /**
     * 样品单审核驳回
     */
    SalesOrder rejectReview(Long orderId, String remark);

    /**
     * 工程接单并上传工艺备注
     */
    SalesOrder startEngineering(Long orderId, String engineeringNote);

    /**
     * 工程标记样品完成，待送样
     */
    SalesOrder markSampleReady(Long orderId, Integer sampleQty);

    /**
     * 销售送样登记
     */
    SalesOrder sendSample(Long orderId, String trackingNo);

    /**
     * 客户确认样品OK
     */
    SalesOrder confirmSample(Long orderId, String clientName);

    /**
     * 客户退回样品（多轮迭代）
     */
    SalesOrder rejectSample(Long orderId, String rejectReason);

    /**
     * 退回后重新打样（REJECTED → ENGINEERING，轮次已+1）
     */
    SalesOrder restartEngineering(Long orderId);

    /**
     * 工程接单确认（工程打样中状态，记录接单人/时间）
     */
    SalesOrder acceptEngineering(Long orderId, String acceptorName);

    /**
     * 工程拒单（工程打样中状态，需拒单原因）
     */
    SalesOrder rejectEngineering(Long orderId, String rejectReason);

    /**
     * 更新打样当前工序
     */
    SalesOrder updateSampleProcess(Long orderId, String process, String materials, String processNote, Integer durationMinutes);

    /**
     * 查询打样工序历史
     */
    List<com.jjx.sales.domain.entity.SalesSampleProcess> listSampleProcesses(Long orderId);

    /**
     * 按轮次查询打样工序（DEV-500）
     *
     * @param roundNo 为空则返回全部
     */
    List<com.jjx.sales.domain.entity.SalesSampleProcess> listSampleProcesses(Long orderId, Integer roundNo);

    /**
     * 打样汇总：总工时 + 材料成本估算（自动计算）
     */
    java.util.Map<String, Object> getSampleSummary(Long orderId);

    /**
     * 查询打样BOM物料清单
     */
    List<com.jjx.sales.domain.entity.SalesSampleBom> listSampleBom(Long orderId);

    /**
     * 保存打样BOM物料（覆盖当前轮次）
     */
    List<com.jjx.sales.domain.entity.SalesSampleBom> saveSampleBom(Long orderId, Integer roundNo, List<com.jjx.sales.domain.entity.SalesSampleBom> items);

    /**
     * 删除单条打样BOM
     */
    boolean deleteSampleBomItem(Long bomId);

    /**
     * 录入打样成本/工时
     */
    SalesOrder recordSampleCost(Long orderId, java.math.BigDecimal cost, java.math.BigDecimal workHours);

    /**
     * 查询打样轮次快照列表
     */
    List<com.jjx.sales.domain.entity.SalesSampleRound> listSampleRounds(Long orderId);

    /**
     * 样品转量产（创建标准订单）
     */
    SalesOrder convertToProduction(Long orderId);

    /**
     * 产品资料转移（DEV-505）：样品确认后建档产品/BOM/工艺路线
     */
    java.util.Map<String, Object> transferMaterials(Long orderId);

    /**
     * 样品单作废
     * 非终态（未转量产/未关闭/未作废）样品单可作废
     */
    SalesOrder cancelSample(Long orderId, String cancelReason);

    /**
     * 查询样品单详情（含迭代历史）
     */
    SalesOrder selectById(Long orderId);

    /**
     * 查询样品单列表（可关联查询报价单信息）
     */
    List<SalesOrder> selectSampleList(Long customerId, Integer sampleStatus, Long salesPersonId);
}
