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
    SalesOrder createFromQuotation(Long quotationId, Integer sampleQty, String remark,
                                   String deliveryDate, String contactPerson, String contactPhone, String techRequirement);

    /**
     * 新增样品单（直接选客户+产品明细，报价单为可选来源）
     */
    SalesOrder createSample(com.jjx.sales.domain.dto.SampleOrderCreateDTO dto);

    /**
     * 更新样品单（驳回后编辑：仅 CREATED 状态可编辑；明细事务内全量替换；锁定单号/来源报价/状态/审核工程字段）
     */
    SalesOrder updateSampleOrder(Long orderId, com.jjx.sales.domain.dto.SampleOrderUpdateDTO dto);

    /**
     * 复制样品单（仅已完成/已取消终态单，一键生成新草稿单）
     */
    SalesOrder copySampleOrder(Long orderId);

    /**
     * 样品单提交审核
     */
    SalesOrder submitRequest(Long orderId);

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
    SalesOrder acceptEngineering(Long orderId);

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
     * 打样转标准-资料转移提醒（DEV-1228）：不再直接转移，发布任务给工程执行资料转移
     */
    java.util.Map<String, Object> remindTransfer(Long orderId);

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

    /**
     * 印刷工序历史输入联想（dev-20260901-1225）
     * 返回已录入的印刷名称/色号/油墨编号去重列表，供打样工作台 el-autocomplete 使用
     */
    java.util.Map<String, java.util.List<String>> processHistory();

    /**
     * 色号联想（2026-09-04 打样印刷搜索式下拉）：
     * 空输入 → 返回常用 TOP N（历史印刷 colorNo 频次降序，不足用字典排序补足）；
     * 有输入 → 字典模糊搜索（label/itemKey/itemValue/remark），返回最多 limit 条。
     * 返回统一为 "PANTONE xxx" 展示文本。
     */
    java.util.List<String> suggestColors(String keyword, Integer limit);

    /**
     * 油墨联想（2026-09-04 打样印刷搜索式下拉，参考色号方案）：
     * 空输入 → 常用 TOP N（历史印刷 inkNo 频次降序，不足用 INK 物料档案补足）；
     * 有输入 → INK 物料模糊搜（编码/名称/规格）+ 历史 inkNo 模糊，合并去重。
     * 返回 [{text, materialId}]：text 为展示/存储文本"物料名 (编码)"，历史文本项 materialId 为 null。
     */
    java.util.List<java.util.Map<String, Object>> suggestInks(String keyword, Integer limit);
}
