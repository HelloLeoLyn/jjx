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
     * 保存打样工序计划（方案A：多选作业项目形成计划，整单覆盖当前轮次）
     *
     * @param orderId 样品单ID
     * @param dto     工序计划（items有序）
     * @return 保存后的工序计划列表
     */
    List<com.jjx.sales.domain.entity.SalesSampleProcess> saveProcessPlan(Long orderId, com.jjx.sales.dto.save.SampleProcessPlanDTO dto);

    /**
     * 推进打样工序状态（方案A：逐项开始/完成）
     *
     * @param orderId   样品单ID
     * @param processId 工序记录ID
     * @param dto       目标状态+可选耗时/说明/材料
     * @return 更新后的工序
     */
    com.jjx.sales.domain.entity.SalesSampleProcess updateProcessItemStatus(Long orderId, Long processId, com.jjx.sales.dto.save.SampleProcessItemStatusDTO dto);

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
     * 样品转量产（产品标准化窗口：items 逐条指定正式产品，覆盖样品临时数据）
     */
    SalesOrder convertToProduction(Long orderId, java.util.List<com.jjx.sales.domain.dto.SampleConvertItemDTO> items);

    /**
     * 转量产就绪检查（产品/BOM/工艺路线/菲林/资料转移清单）
     */
    com.jjx.sales.domain.vo.SampleConvertCheckVO checkConvertReady(Long orderId);

    /**
     * 产品资料转移（DEV-505）：样品确认后建档产品/BOM/工艺路线
     */
    java.util.Map<String, Object> transferMaterials(Long orderId);

    /**
     * 打样转标准-预览：读取打样数据（工序+物料JSON），自动匹配标准工序/物料，返回带匹配推荐的预览数据
     */
    com.jjx.sales.domain.vo.SampleTransferPreviewVO previewTransfer(Long orderId);

    /**
     * 打样转标准-确认转移：接收前端编辑后的标准数据（工序映射+物料映射），
     * 生成新版本BOM/Routing，旧版本失效，回填打样单和产品表
     */
    java.util.Map<String, Object> confirmTransfer(com.jjx.sales.dto.transfer.SampleTransferConfirmDTO dto);

    /**
     * 样品单列表（DEV-526 打样平台：支持按是否已接单筛选）
     */
    List<com.jjx.sales.domain.entity.SalesOrder> selectSampleList(Long customerId, Integer sampleStatus, Long salesPersonId, Boolean hasAcceptor);

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
