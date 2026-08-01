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
     * 样品转量产（创建标准订单）
     */
    SalesOrder convertToProduction(Long orderId);

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
