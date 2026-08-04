package com.jjx.sales.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.sales.domain.dto.SalesOrderProductDTO;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.domain.entity.SalesSampleProcess;
import com.jjx.sales.domain.entity.SalesSampleBom;
import com.jjx.sales.enums.OrderTypeEnum;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.mapper.SalesSampleRoundMapper;
import com.jjx.sales.mapper.SalesSampleProcessMapper;
import com.jjx.sales.mapper.SalesSampleBomMapper;
import com.jjx.sales.service.ISampleOrderService;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.system.annotation.Event;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 样品单服务实现类
 * 独立于标准订单的样品单生命周期管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleOrderServiceImpl implements ISampleOrderService {

    private final OrderMapper orderMapper;
    private final QuotationMapper quotationMapper;
    private final com.jjx.sales.mapper.SalesQuotationItemMapper quotationItemMapper;
    private final com.jjx.product.mapper.ProductMapper productMapper;
    private final com.jjx.sales.mapper.SalesSampleTransferMapper sampleTransferMapper;
    private final com.jjx.sales.mapper.SalesOrderProductMapper orderProductMapper;
    private final com.jjx.system.mapper.SysTaskMapper sysTaskMapper;
    private final RedisSequenceService redisSequenceService;
    private final ISalesOrderProductService orderProductService;
    private final SalesSampleRoundMapper sampleRoundMapper;
    private final SalesSampleProcessMapper sampleProcessMapper;
    private final SalesSampleBomMapper sampleBomMapper;
    private final com.jjx.system.service.ISysAttachmentService attachmentService;
    private final com.jjx.engineering.service.IBomService bomService;
    private final com.jjx.engineering.service.IRoutingService routingService;
    private final com.jjx.product.mapper.EngineeringBomItemMapper bomItemMapper;
    private final com.jjx.inventory.mapper.InventoryMaterialMapper inventoryMaterialMapper;
    private final com.jjx.engineering.mapper.RoutingItemMapper routingItemMapper;
    private final com.jjx.engineering.mapper.StandardProcessMapper standardProcessMapper;

    // ============ 状态更新辅助 ============

    /**
     * 安全更新样品单状态（带旧状态校验防并发）
     */
    private void safeTransition(Long orderId, SampleOrderStatusEnum from, SampleOrderStatusEnum to, String actionDesc) {
        int affected = orderMapper.updateSampleStatus(orderId, from.getCode(), to.getCode());
        if (affected == 0) {
            SalesOrder current = orderMapper.selectById(orderId);
            String statusName = current != null && current.getSampleStatus() != null
                    ? SampleOrderStatusEnum.getByCodeSafe(current.getSampleStatus()).map(SampleOrderStatusEnum::getName).orElse("未知")
                    : "未知";
            throw new BusinessException(String.format("样品单状态已变更(当前:%s)，无法%s，请刷新后重试", statusName, actionDesc));
        }
        log.info("样品单[{}] 状态 {} → {}", orderId, from.getName(), to.getName());
    }

    // ============ 核心业务流程 ============

    @Override
    @Event(value = "sample.created", bizId = "#result.orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder createFromQuotation(Long quotationId, Integer sampleQty, String remark) {
        SalesQuotation quotation = quotationMapper.selectById(quotationId);
        if (quotation == null || quotation.getDeleted() == 1) {
            throw new BusinessException("报价单不存在");
        }

        // 防重复转换：已完成(9)的报价单不可再转样品
        if (quotation.getQuotationStatus() != null
                && quotation.getQuotationStatus() == com.jjx.sales.enums.QuotationStatus.COMPLETED.getCode()) {
            throw new BusinessException("报价单已完成，不可重复转样品单");
        }
        // 只有客户已确认(2)的报价单可转样品单（8-03 规则）
        if (quotation.getQuotationStatus() == null
                || quotation.getQuotationStatus() != com.jjx.sales.enums.QuotationStatus.ACCEPTED.getCode()) {
            throw new BusinessException("只有客户已确认的报价单可以转为样品单");
        }

        // 生成订单编号
        String orderNo = redisSequenceService.generateBusinessNumber("SP", "样品单号");

        SalesOrder order = new SalesOrder();
        order.setOrderNo(orderNo);
        order.setQuotationId(quotationId);
        order.setCustomerId(quotation.getCustomerId());
        order.setCustomerName(quotation.getCustomerName());
        order.setContactPerson(quotation.getContactPerson());
        order.setContactPhone(quotation.getContactPhone());
        order.setOrderDate(new Date());
        order.setOrderType(OrderTypeEnum.SAMPLE.getCode());
        order.setSampleStatus(SampleOrderStatusEnum.CREATED.getCode());
        order.setSampleRound(1);
        order.setSampleQty(sampleQty);
        order.setCurrency(quotation.getCurrency());
        order.setExchangeRate(quotation.getExchangeRate());
        order.setSalesManagerId(quotation.getSalesPersonId());
        order.setSalesManagerName(quotation.getSalesPersonName());
        order.setRemark(remark);
        order.setTotalQuantity(sampleQty != null ? sampleQty : 0);
        order.setTotalAmount(quotation.getTotalAmount());
        order.setFinalAmount(quotation.getFinalAmount());
        // 继承报价单链路追踪ID（同一业务链路）
        order.setTraceId(quotation.getTraceId());
        if (quotation.getValidUntil() != null) {
            order.setDeliveryDate(Date.from(quotation.getValidUntil().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }

        orderMapper.insert(order);
        log.info("从报价单[{}]创建样品单[{}] orderId={}", quotation.getQuotationNo(), orderNo, order.getOrderId());

        // 复制报价单明细到样品单（产品资料转移/转量产依赖明细，源头修复）
        copyQuotationItemsToOrder(quotationId, order.getOrderId());

        // 报价单状态更新：已确认(2) → 已完成(9)（报价已转化为样品打样，不可重复转）
        try {
            SalesQuotation update = new SalesQuotation();
            update.setQuotationId(quotationId);
            update.setQuotationStatus(com.jjx.sales.enums.QuotationStatus.COMPLETED.getCode());
            quotationMapper.updateById(update);
            log.info("报价单[{}] 转样品后状态 → 已完成(9)", quotation.getQuotationNo());
        } catch (Exception e) {
            log.warn("更新报价单状态失败: {}", e.getMessage());
        }

        return order;
    }

    @Override
    @Event(value = "sample.submitted", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder submitReview(Long orderId) {
        safeTransition(orderId,
                SampleOrderStatusEnum.CREATED,
                SampleOrderStatusEnum.PENDING_REVIEW,
                "提交审核");
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.approved", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder approveReview(Long orderId, String remark) {
        safeTransition(orderId,
                SampleOrderStatusEnum.PENDING_REVIEW,
                SampleOrderStatusEnum.ENGINEERING,
                "审核通过");

        // 更新审核信息
        try {
            SalesOrder update = new SalesOrder();
            update.setOrderId(orderId);
            update.setRemark(remark);
            orderMapper.updateById(update);
        } catch (Exception e) {
            log.warn("更新样品单审核备注失败: {}", e.getMessage());
        }

        log.info("样品单[{}] 审核通过，进入工程打样阶段", orderId);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.rejected", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder rejectReview(Long orderId, String remark) {
        // 审核驳回：回到创建状态(1)，销售可改单后重新提交（语义与客户退回9区分）
        safeTransition(orderId,
                SampleOrderStatusEnum.PENDING_REVIEW,
                SampleOrderStatusEnum.CREATED,
                "审核驳回");
        // 记录驳回原因
        try {
            SalesOrder update = new SalesOrder();
            update.setOrderId(orderId);
            update.setRemark("[审核驳回] " + (remark != null ? remark : ""));
            orderMapper.updateById(update);
        } catch (Exception e) {
            log.warn("记录审核驳回原因失败: {}", e.getMessage());
        }
        return orderMapper.selectById(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder startEngineering(Long orderId, String engineeringNote) {
        // 只有 ENGINEERING 状态能设置工程备注
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (!SampleOrderStatusEnum.ENGINEERING.getCode().equals(order.getSampleStatus())) {
            throw new BusinessException("当前状态不可进行工程接单操作");
        }

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setEngineeringNote(engineeringNote);
        orderMapper.updateById(update);

        log.info("样品单[{}] 工程接单，工程备注已记录", orderId);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.ready", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder markSampleReady(Long orderId, Integer sampleQty) {
        // 前置校验（DEV-491）：① 必须已工程接单
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (current.getEngineeringAcceptor() == null || current.getEngineeringAcceptor().isEmpty()) {
            throw new BusinessException("请先工程接单，再进行样品完成");
        }
        // ② 当前轮次至少 1 条工序记录（DEV-500 轮次语义对齐）
        Integer roundNo = current.getSampleRound() != null ? current.getSampleRound() : 1;
        java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> roundProcs =
                sampleProcessMapper.selectByOrderAndRound(orderId, roundNo);
        if (roundProcs == null || roundProcs.isEmpty()) {
            throw new BusinessException("当前轮次(Round" + roundNo + ")无工序记录，请先录入工序再标记完成");
        }

        safeTransition(orderId,
                SampleOrderStatusEnum.ENGINEERING,
                SampleOrderStatusEnum.SAMPLE_READY,
                "标记样品完成");

        if (sampleQty != null) {
            SalesOrder update = new SalesOrder();
            update.setOrderId(orderId);
            update.setSampleQty(sampleQty);
            orderMapper.updateById(update);
        }

        // 归档本轮快照（工艺参数 + 图纸附件 + BOM + 工序，DEV-456 补全）
        try {
            SalesOrder full = orderMapper.selectById(orderId);
            com.jjx.sales.domain.entity.SalesSampleRound round = new com.jjx.sales.domain.entity.SalesSampleRound();
            round.setOrderId(orderId);
            round.setRoundNo(full.getSampleRound() != null ? full.getSampleRound() : 1);
            round.setEngineeringNote(full.getEngineeringNote());
            round.setResult("pending");
            round.setCreateTime(java.time.LocalDateTime.now());

            // ① 图纸附件ID：按 traceId 查附件，写入 attachmentIds(JSON数组)
            if (full.getTraceId() != null) {
                try {
                    java.util.List<com.jjx.system.domain.entity.SysAttachment> atts =
                            attachmentService.getAttachmentsByTraceId(full.getTraceId());
                    if (atts != null && !atts.isEmpty()) {
                        java.util.List<Long> ids = new java.util.ArrayList<>();
                        for (com.jjx.system.domain.entity.SysAttachment a : atts) {
                            if (a.getId() != null) ids.add(a.getId());
                        }
                        if (!ids.isEmpty()) {
                            round.setAttachmentIds(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ids));
                        }
                    }
                } catch (Exception ae) {
                    log.warn("归档图纸附件失败: {}", ae.getMessage());
                }
            }

            // ② BOM 物料快照（从工序单元材料聚合）
            try {
                java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> procs =
                        sampleProcessMapper.selectByOrderId(orderId);
                java.util.List<java.util.Map<String, Object>> aggMats = new java.util.ArrayList<>();
                if (procs != null) {
                    for (com.jjx.sales.domain.entity.SalesSampleProcess sp : procs) {
                        if (sp.getMaterials() == null || sp.getMaterials().isEmpty()) continue;
                        try {
                            java.util.List<java.util.Map<String, Object>> mats =
                                    new com.fasterxml.jackson.databind.ObjectMapper().readValue(sp.getMaterials(),
                                            new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
                            for (java.util.Map<String, Object> m : mats) {
                                java.util.Map<String, Object> agg = new java.util.LinkedHashMap<>();
                                agg.put("process", sp.getProcessName());
                                agg.putAll(m);
                                aggMats.add(agg);
                            }
                        } catch (Exception pe) {
                            log.warn("解析工序材料失败: {}", pe.getMessage());
                        }
                    }
                }
                if (!aggMats.isEmpty()) {
                    round.setBomSnapshot(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(aggMats));
                }
            } catch (Exception be) {
                log.warn("归档BOM快照失败: {}", be.getMessage());
            }

            // ③ 工序记录快照
            try {
                java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> procs = sampleProcessMapper.selectByOrderId(orderId);
                if (procs != null && !procs.isEmpty()) {
                    round.setProcessSnapshot(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(procs));
                }
            } catch (Exception pe) {
                log.warn("归档工序快照失败: {}", pe.getMessage());
            }

            sampleRoundMapper.insert(round);
            log.info("样品单[{}] 归档轮次快照 round={} (附件{}个/BOM{}条/工序{}条)",
                    orderId, round.getRoundNo(),
                    round.getAttachmentIds() != null ? "有" : "无",
                    round.getBomSnapshot() != null ? "有" : "无",
                    round.getProcessSnapshot() != null ? "有" : "无");
        } catch (Exception e) {
            log.warn("归档样品轮次快照失败: {}", e.getMessage());
        }

        log.info("样品单[{}] 样品制作完成，待送样", orderId);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.sent", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder sendSample(Long orderId, String trackingNo) {
        safeTransition(orderId,
                SampleOrderStatusEnum.SAMPLE_READY,
                SampleOrderStatusEnum.SAMPLE_SENT,
                "送样");

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setSampleTrackingNo(trackingNo);
        update.setSampleSendDate(new Date());
        orderMapper.updateById(update);

        log.info("样品单[{}] 已送样，快递单号:{}", orderId, trackingNo);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.confirmed", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder confirmSample(Long orderId, String clientName) {
        safeTransition(orderId,
                SampleOrderStatusEnum.SAMPLE_SENT,
                SampleOrderStatusEnum.CONFIRMED,
                "客户确认样品");

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setSampleClientName(clientName);
        update.setSampleConfirmDate(new Date());
        orderMapper.updateById(update);

        // 更新本轮快照结果：confirmed
        try {
            List<com.jjx.sales.domain.entity.SalesSampleRound> rounds = sampleRoundMapper.selectByOrderId(orderId);
            if (!rounds.isEmpty()) {
                com.jjx.sales.domain.entity.SalesSampleRound latest = rounds.get(rounds.size() - 1);
                if ("pending".equals(latest.getResult())) {
                    latest.setResult("confirmed");
                    sampleRoundMapper.updateById(latest);
                }
            }
        } catch (Exception e) {
            log.warn("更新轮次快照确认结果失败: {}", e.getMessage());
        }

        log.info("样品单[{}] 客户[{}]已确认样品OK", orderId, clientName);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.rejected_by_customer", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder rejectSample(Long orderId, String rejectReason) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        // 只能从 SAMPLE_SENT 退回（已送样的状态下退回）
        SampleOrderStatusEnum currentStatus = SampleOrderStatusEnum.getByCodeSafe(current.getSampleStatus())
                .orElseThrow(() -> new BusinessException("当前状态不可退回"));

        if (currentStatus != SampleOrderStatusEnum.SAMPLE_SENT) {
            throw new BusinessException("当前状态不可退回，仅已送样待确认的样品可退回");
        }

        orderMapper.updateSampleStatus(orderId, SampleOrderStatusEnum.SAMPLE_SENT.getCode(),
                SampleOrderStatusEnum.REJECTED.getCode());

        // 退回轮次+1，并退回工程阶段
        int nextRound = (current.getSampleRound() != null ? current.getSampleRound() : 1) + 1;
        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setSampleRound(nextRound);
        update.setRemark(rejectReason);
        orderMapper.updateById(update);

        // 更新本轮快照结果：rejected + 退回原因
        try {
            List<com.jjx.sales.domain.entity.SalesSampleRound> rounds = sampleRoundMapper.selectByOrderId(orderId);
            if (!rounds.isEmpty()) {
                com.jjx.sales.domain.entity.SalesSampleRound latest = rounds.get(rounds.size() - 1);
                if ("pending".equals(latest.getResult())) {
                    latest.setResult("rejected");
                    latest.setRejectReason(rejectReason);
                    sampleRoundMapper.updateById(latest);
                }
            }
        } catch (Exception e) {
            log.warn("更新轮次快照结果失败: {}", e.getMessage());
        }

        log.info("样品单[{}] 客户退回(Round{})，退回原因:{}", orderId, nextRound, rejectReason);
        return orderMapper.selectById(orderId);
    }

    /**
     * 退回后重新打样（REJECTED → ENGINEERING，轮次已+1）
     */
    @Override
    @Event(value = "sample.restarted", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder restartEngineering(Long orderId) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        // 只有客户退回(9)状态可以重新打样
        if (!SampleOrderStatusEnum.REJECTED.getCode().equals(current.getSampleStatus())) {
            String name = SampleOrderStatusEnum.getByCodeSafe(current.getSampleStatus())
                    .map(SampleOrderStatusEnum::getName).orElse("未知");
            throw new BusinessException("当前状态[" + name + "]不可重新打样，仅客户退回状态可重新打样");
        }

        int affected = orderMapper.updateSampleStatus(orderId, SampleOrderStatusEnum.REJECTED.getCode(),
                SampleOrderStatusEnum.ENGINEERING.getCode());
        if (affected == 0) {
            throw new BusinessException("样品单状态已变更，无法重新打样，请刷新后重试");
        }

        log.info("样品单[{}] 重新打样(Round{})，回到工程打样阶段", current.getOrderNo(),
                current.getSampleRound() != null ? current.getSampleRound() : 1);
        return orderMapper.selectById(orderId);
    }

    /**
     * 工程接单确认
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder acceptEngineering(Long orderId, String acceptorName) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (!SampleOrderStatusEnum.ENGINEERING.getCode().equals(current.getSampleStatus())) {
            throw new BusinessException("当前状态不可接单，仅工程打样中状态可接单");
        }

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setEngineeringAcceptor(acceptorName);
        update.setEngineeringAcceptTime(new Date());
        orderMapper.updateById(update);

        log.info("样品单[{}] 工程接单: {}", current.getOrderNo(), acceptorName);
        return orderMapper.selectById(orderId);
    }

    /**
     * 工程拒单（回退到待审核，销售可改单重提）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder rejectEngineering(Long orderId, String rejectReason) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (!SampleOrderStatusEnum.ENGINEERING.getCode().equals(current.getSampleStatus())) {
            throw new BusinessException("当前状态不可拒单，仅工程打样中状态可拒单");
        }
        if (rejectReason == null || rejectReason.trim().isEmpty()) {
            throw new BusinessException("拒单原因不能为空");
        }

        int affected = orderMapper.updateSampleStatus(orderId, SampleOrderStatusEnum.ENGINEERING.getCode(),
                SampleOrderStatusEnum.PENDING_REVIEW.getCode());
        if (affected == 0) {
            throw new BusinessException("样品单状态已变更，无法拒单，请刷新后重试");
        }

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setRejectReason(rejectReason);
        orderMapper.updateById(update);

        log.info("样品单[{}] 工程拒单: {}", current.getOrderNo(), rejectReason);
        return orderMapper.selectById(orderId);
    }

    /**
     * 更新打样当前工序
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder updateSampleProcess(Long orderId, String process, String materials, String processNote, Integer durationMinutes) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (!SampleOrderStatusEnum.ENGINEERING.getCode().equals(current.getSampleStatus())
                && !SampleOrderStatusEnum.SAMPLE_READY.getCode().equals(current.getSampleStatus())
                && !SampleOrderStatusEnum.SAMPLE_SENT.getCode().equals(current.getSampleStatus())) {
            throw new BusinessException("当前状态不可更新工序进度");
        }

        // 记录工序历史（追加，不覆盖）— 工序单元化：材料+工艺说明+耗时
        SalesSampleProcess record = new SalesSampleProcess();
        record.setOrderId(orderId);
        record.setRoundNo(current.getSampleRound() != null ? current.getSampleRound() : 1);
        record.setProcessName(process);
        record.setMaterials(materials);
        record.setProcessNote(processNote);
        record.setOperator(SecurityUtils.getUsername());
        record.setStartTime(LocalDateTime.now());
        record.setDurationMinutes(durationMinutes);
        record.setRemark("工序进度更新");
        sampleProcessMapper.insert(record);

        // 更新当前工序字段（用于列表展示当前进度）
        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setCurrentProcess(process);
        orderMapper.updateById(update);

        log.info("样品单[{}] 更新当前工序: {} (已记录历史)", current.getOrderNo(), process);
        return orderMapper.selectById(orderId);
    }

    /**
     * 查询打样工序历史
     */
    @Override
    @Transactional(readOnly = true)
    public List<SalesSampleProcess> listSampleProcesses(Long orderId) {
        return sampleProcessMapper.selectByOrderId(orderId);
    }

    /**
     * 按轮次查询打样工序（DEV-500）
     */
    @Override
    @Transactional(readOnly = true)
    public List<SalesSampleProcess> listSampleProcesses(Long orderId, Integer roundNo) {
        if (roundNo == null) {
            return sampleProcessMapper.selectByOrderId(orderId);
        }
        return sampleProcessMapper.selectByOrderAndRound(orderId, roundNo);
    }

    /**
     * 打样汇总（DEV-454 增强）：总工时 + 材料成本估算（自动计算，不手填）
     */
    @Override
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getSampleSummary(Long orderId) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        java.util.List<SalesSampleProcess> processes = sampleProcessMapper.selectByOrderId(orderId);

        // 总工时（各工序 durationMinutes 之和，分钟→小时）
        int totalMinutes = 0;
        java.util.Map<String, java.util.Map<String, Object>> materialAgg = new java.util.LinkedHashMap<>();
        for (SalesSampleProcess sp : processes) {
            if (sp.getDurationMinutes() != null) totalMinutes += sp.getDurationMinutes();
            if (sp.getMaterials() == null || sp.getMaterials().isEmpty()) continue;
            try {
                java.util.List<java.util.Map<String, Object>> mats =
                        new com.fasterxml.jackson.databind.ObjectMapper().readValue(sp.getMaterials(),
                                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
                for (java.util.Map<String, Object> m : mats) {
                    String name = m.get("name") != null ? m.get("name").toString() : null;
                    if (name == null) continue;
                    String key = name + "|" + (m.get("spec") != null ? m.get("spec") : "");
                    java.util.Map<String, Object> agg = materialAgg.get(key);
                    if (agg == null) {
                        agg = new java.util.LinkedHashMap<>();
                        agg.put("name", name);
                        agg.put("spec", m.get("spec"));
                        agg.put("unit", m.get("unit") != null ? m.get("unit") : "PCS");
                        agg.put("qty", java.math.BigDecimal.ZERO);
                        // 匹配物料标准价
                        try {
                            com.jjx.inventory.domain.InventoryMaterial mat = inventoryMaterialMapper.selectOne(
                                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.inventory.domain.InventoryMaterial>()
                                            .eq(com.jjx.inventory.domain.InventoryMaterial::getMaterialName, name)
                                            .last("LIMIT 1"));
                            if (mat != null && mat.getStandardPrice() != null) {
                                agg.put("unitPrice", mat.getStandardPrice());
                            }
                        } catch (Exception me) { /* ignore */ }
                        materialAgg.put(key, agg);
                    }
                    if (m.get("qty") != null) {
                        try {
                            java.math.BigDecimal qty = (java.math.BigDecimal) agg.get("qty");
                            agg.put("qty", qty.add(new java.math.BigDecimal(m.get("qty").toString())));
                        } catch (Exception qe) { /* ignore */ }
                    }
                }
            } catch (Exception pe) {
                log.warn("解析工序材料失败: {}", pe.getMessage());
            }
        }

        // 材料成本估算
        java.math.BigDecimal materialCost = java.math.BigDecimal.ZERO;
        for (java.util.Map<String, Object> agg : materialAgg.values()) {
            if (agg.get("unitPrice") != null && agg.get("qty") != null) {
                try {
                    materialCost = materialCost.add(
                            ((java.math.BigDecimal) agg.get("qty")).multiply((java.math.BigDecimal) agg.get("unitPrice")));
                } catch (Exception e) { /* ignore */ }
            }
        }

        result.put("totalMinutes", totalMinutes);
        result.put("totalHours", java.math.BigDecimal.valueOf(totalMinutes).divide(java.math.BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP));
        result.put("materialCount", materialAgg.size());
        result.put("materials", new java.util.ArrayList<>(materialAgg.values()));
        result.put("materialCost", materialCost.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("processCount", processes.size());
        return result;
    }

    /**
     * 查询打样BOM物料清单
     */
    @Override
    @Transactional(readOnly = true)
    public List<SalesSampleBom> listSampleBom(Long orderId) {
        return sampleBomMapper.selectByOrderId(orderId);
    }

    /**
     * 保存打样BOM物料（覆盖当前轮次：先删后插，保持结构化一致）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SalesSampleBom> saveSampleBom(Long orderId, Integer roundNo, List<SalesSampleBom> items) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("物料清单不能为空");
        }

        int round = roundNo != null ? roundNo : (current.getSampleRound() != null ? current.getSampleRound() : 1);
        // 覆盖当前轮次：先删旧记录
        sampleBomMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SalesSampleBom>()
                .eq(SalesSampleBom::getOrderId, orderId)
                .eq(SalesSampleBom::getRoundNo, round));

        String username = SecurityUtils.getUsername();
        for (SalesSampleBom item : items) {
            if (item.getLayerName() == null || item.getMaterialName() == null) {
                throw new BusinessException("层结构和物料名称必填");
            }
            item.setBomId(null);
            item.setOrderId(orderId);
            item.setRoundNo(round);
            item.setCreateBy(username);
            if (item.getQuantity() == null) item.setQuantity(java.math.BigDecimal.ONE);
            if (item.getUnit() == null) item.setUnit("PCS");
            sampleBomMapper.insert(item);
        }
        log.info("样品单[{}] 保存打样BOM {} 条 (round={})", orderId, items.size(), round);
        return sampleBomMapper.selectByOrderId(orderId);
    }

    /**
     * 删除单条打样BOM
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSampleBomItem(Long bomId) {
        return sampleBomMapper.deleteById(bomId) > 0;
    }

    /**
     * 录入打样成本/工时
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder recordSampleCost(Long orderId, java.math.BigDecimal cost, java.math.BigDecimal workHours) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        if (cost != null) update.setSampleCost(cost);
        if (workHours != null) update.setSampleWorkHours(workHours);
        orderMapper.updateById(update);

        log.info("样品单[{}] 录入打样成本: {}, 工时: {}h", current.getOrderNo(), cost, workHours);
        return orderMapper.selectById(orderId);
    }

    /**
     * 查询打样轮次快照列表
     */
    @Override
    public List<com.jjx.sales.domain.entity.SalesSampleRound> listSampleRounds(Long orderId) {
        return sampleRoundMapper.selectByOrderId(orderId);
    }

    /**
     * 产品资料转移（DEV-505）：样品确认后，把打样成果建档为正式产品/BOM/工艺路线
     * 状态全部初始化（产品=待审核，BOM/路线=草稿），事件通知+派任务由工程完善后提交审核
     */
    @Override
    @Event(value = "sample.transferred", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public java.util.Map<String, Object> transferMaterials(Long orderId) {
        SalesOrder sampleOrder = orderMapper.selectById(orderId);
        if (sampleOrder == null || sampleOrder.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        // 已确认(6)或已转量产(7)可转移（转量产后可补转移）
        Integer st = sampleOrder.getSampleStatus();
        if (!SampleOrderStatusEnum.CONFIRMED.getCode().equals(st)
                && !SampleOrderStatusEnum.TRANSFERRED.getCode().equals(st)) {
            throw new BusinessException("仅已确认或已转量产的样品单可进行资料转移");
        }

        String transferNo = redisSequenceService.generateBusinessNumber("TF", "资料转移单号");
        java.util.List<String> details = new java.util.ArrayList<>();
        String productAction = "NONE", bomAction = "NONE", routingAction = "NONE";
        Long builtProductId = null, builtBomId = null, builtRoutingId = null;

        // 明细：样品单若无明细则从报价单补复制（数据断链兜底）
        java.util.List<com.jjx.sales.domain.vo.SalesOrderProductVO> prodList =
                orderProductService.getListByOrderId(orderId);
        if (prodList == null || prodList.isEmpty()) {
            if (sampleOrder.getQuotationId() != null) {
                copyQuotationItemsToOrder(sampleOrder.getQuotationId(), orderId);
                prodList = orderProductService.getListByOrderId(orderId);
            }
        }
        // DEV-500 联动：聚合源用最新轮次工序（避免旧轮次试错工序混入量产BOM/路线）
        java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> allProcesses =
                sampleProcessMapper.selectByOrderId(orderId);
        java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> processes = allProcesses;
        if (allProcesses != null && !allProcesses.isEmpty()) {
            Integer latestRound = allProcesses.stream()
                    .map(com.jjx.sales.domain.entity.SalesSampleProcess::getRoundNo)
                    .filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo).orElse(null);
            if (latestRound != null) {
                processes = allProcesses.stream()
                        .filter(p -> latestRound.equals(p.getRoundNo()))
                        .collect(java.util.stream.Collectors.toList());
                log.info("样品单[{}] 资料转移聚合源：最新轮次Round{} ({}条工序，全量{}条)",
                        orderId, latestRound, processes.size(), allProcesses.size());
            }
        }

        if (prodList != null) {
            for (com.jjx.sales.domain.vo.SalesOrderProductVO prod : prodList) {
                Long pid = prod.getProductId();

                // ===== ① 产品建档：无则建草稿（待审核），有则核对 =====
                com.jjx.product.domain.entity.Product product = pid != null ? productMapper.selectById(pid) : null;
                if (product == null) {
                    com.jjx.product.domain.entity.Product np = new com.jjx.product.domain.entity.Product();
                    np.setProductCode(prod.getProductCode());
                    np.setProductName(prod.getProductName());
                    np.setProductStatus(2); // 待审核
                    np.setUnit(prod.getUnit());
                    np.setCreateBy(SecurityUtils.getUsername());
                    productMapper.insert(np);
                    pid = np.getProductId();
                    productAction = "CREATE";
                    details.add("产品[" + prod.getProductCode() + "]新建建档(待审核)");
                    // 回写明细 productId
                    try {
                        com.jjx.sales.domain.entity.SalesOrderProduct op = new com.jjx.sales.domain.entity.SalesOrderProduct();
                        op.setId(prod.getId());
                        op.setProductId(pid);
                        orderProductMapper.updateById(op);
                    } catch (Exception e) {
                        log.warn("样品单明细回写productId失败: {}", e.getMessage());
                    }
                } else {
                    productAction = "UPDATE";
                    details.add("产品[" + prod.getProductCode() + "]已存在，档案核对");
                }
                if (builtProductId == null) builtProductId = pid;
                if (pid == null) continue;

                // ===== ② BOM 建档：从工序单元材料聚合生成草稿 =====
                com.jjx.engineering.domain.entity.Bom existBom = bomService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.Bom>()
                                .eq(com.jjx.engineering.domain.entity.Bom::getProductId, pid)
                                .eq(com.jjx.engineering.domain.entity.Bom::getIsCurrent, 1)
                                .last("LIMIT 1"));
                if (existBom == null) {
                    if (processes != null && !processes.isEmpty()) {
                        com.jjx.engineering.domain.entity.Bom newBom = new com.jjx.engineering.domain.entity.Bom();
                        newBom.setBomCode("BOM-" + prod.getProductCode() + "-SAMPLE");
                        newBom.setBomName(prod.getProductName() + "（打样传承BOM）");
                        newBom.setProductId(pid);
                        newBom.setBomVersion("V1");
                        newBom.setBomType("manufacturing");
                        newBom.setIsCurrent(1);
                        newBom.setApproveStatus(1L); // 草稿
                        newBom.setRemark("由样品单[" + sampleOrder.getOrderNo() + "]资料转移生成，请工程确认后批准");
                        newBom.setCreateBy(SecurityUtils.getUsername());
                        bomService.save(newBom);

                        int order = 1;
                        java.util.Map<String, com.jjx.product.domain.entity.EngineeringBomItem> aggMap = new java.util.LinkedHashMap<>();
                        for (com.jjx.sales.domain.entity.SalesSampleProcess sp : processes) {
                            if (sp.getMaterials() == null || sp.getMaterials().isEmpty()) continue;
                            try {
                                java.util.List<java.util.Map<String, Object>> mats =
                                        new com.fasterxml.jackson.databind.ObjectMapper().readValue(sp.getMaterials(),
                                                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
                                for (java.util.Map<String, Object> m : mats) {
                                    String name = m.get("name") != null ? m.get("name").toString() : null;
                                    if (name == null || name.isEmpty()) continue;
                                    String key = name + "|" + (m.get("spec") != null ? m.get("spec") : "");
                                    com.jjx.product.domain.entity.EngineeringBomItem item = aggMap.get(key);
                                    if (item == null) {
                                        item = new com.jjx.product.domain.entity.EngineeringBomItem();
                                        item.setBomId(newBom.getBomId());
                                        item.setMaterialName(name);
                                        item.setSpecification(m.get("spec") != null ? m.get("spec").toString() : null);
                                        item.setUnit(m.get("unit") != null ? m.get("unit").toString() : "PCS");
                                        item.setLayer(sp.getProcessName());
                                        item.setItemOrder(order++);
                                        item.setQuantity(java.math.BigDecimal.ZERO);
                                        aggMap.put(key, item);
                                        try {
                                            com.jjx.inventory.domain.InventoryMaterial mat = inventoryMaterialMapper.selectOne(
                                                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.inventory.domain.InventoryMaterial>()
                                                            .eq(com.jjx.inventory.domain.InventoryMaterial::getMaterialName, name)
                                                            .last("LIMIT 1"));
                                            if (mat != null) {
                                                item.setMaterialId(mat.getMaterialId());
                                                item.setMaterialCode(mat.getMaterialCode());
                                            }
                                        } catch (Exception me) {
                                            log.warn("工序材料匹配物料失败: {}", me.getMessage());
                                        }
                                    }
                                    if (m.get("qty") != null) {
                                        try {
                                            item.setQuantity(item.getQuantity().add(new java.math.BigDecimal(m.get("qty").toString())));
                                        } catch (Exception qe) { /* ignore */ }
                                    }
                                }
                            } catch (Exception pe) {
                                log.warn("解析工序材料失败: {}", pe.getMessage());
                            }
                        }
                        for (com.jjx.product.domain.entity.EngineeringBomItem item : aggMap.values()) {
                            bomItemMapper.insert(item);
                        }
                        builtBomId = newBom.getBomId();
                        bomAction = "CREATE";
                        details.add("BOM[" + newBom.getBomCode() + "]生成草稿(" + aggMap.size() + "条明细)");
                    } else {
                        bomAction = "SKIP_NO_PROCESS";
                        details.add("产品[" + prod.getProductCode() + "]无打样工序，未生成BOM");
                    }
                } else {
                    bomAction = "EXISTS";
                    builtBomId = existBom.getBomId();
                    details.add("产品[" + prod.getProductCode() + "]已有BOM[" + existBom.getBomCode() + "]");
                }

                // ===== ③ 工艺路线建档：从工序单元生成草稿 =====
                com.jjx.engineering.domain.entity.Routing existRouting = routingService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.Routing>()
                                .eq(com.jjx.engineering.domain.entity.Routing::getProductId, pid)
                                .last("LIMIT 1"));
                if (existRouting == null) {
                    if (processes != null && !processes.isEmpty()) {
                        com.jjx.engineering.domain.entity.Routing newRouting = new com.jjx.engineering.domain.entity.Routing();
                        newRouting.setRoutingCode("RTE-" + prod.getProductCode() + "-SAMPLE");
                        newRouting.setRoutingName(prod.getProductName() + "（打样传承工艺路线）");
                        newRouting.setProductId(pid);
                        newRouting.setProductCode(prod.getProductCode());
                        newRouting.setProductName(prod.getProductName());
                        newRouting.setRoutingVersion("V1");
                        newRouting.setIsCurrent(1);
                        newRouting.setStatus(1); // 草稿
                        newRouting.setCreateBy(SecurityUtils.getUsername());
                        routingService.save(newRouting);

                        int stepOrder = 1;
                        java.math.BigDecimal totalLabor = java.math.BigDecimal.ZERO;
                        java.math.BigDecimal totalMachine = java.math.BigDecimal.ZERO;
                        for (com.jjx.sales.domain.entity.SalesSampleProcess sp : processes) {
                            com.jjx.engineering.domain.entity.StandardProcess std = standardProcessMapper.selectOne(
                                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.StandardProcess>()
                                            .eq(com.jjx.engineering.domain.entity.StandardProcess::getProcessName, sp.getProcessName())
                                            .last("LIMIT 1"));
                            Long stdProcessId = std != null ? std.getProcessId() : null;
                            String category = std != null ? std.getProcessCategory() : "M";
                            java.math.BigDecimal laborHours = sp.getDurationMinutes() != null
                                    ? java.math.BigDecimal.valueOf(sp.getDurationMinutes()).divide(java.math.BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP)
                                    : (std != null && std.getStandardLaborHours() != null ? std.getStandardLaborHours() : java.math.BigDecimal.ZERO);
                            java.math.BigDecimal machineHours = std != null && std.getStandardMachineHours() != null
                                    ? std.getStandardMachineHours() : laborHours;
                            totalLabor = totalLabor.add(laborHours);
                            totalMachine = totalMachine.add(machineHours);
                            routingItemMapper.insertItem(newRouting.getRoutingId(),
                                    stdProcessId != null ? stdProcessId : null,
                                    stepOrder++, laborHours, machineHours,
                                    sp.getProcessNote() != null ? sp.getProcessNote() : null,
                                    "打样传承: " + (sp.getProcessNote() != null ? sp.getProcessNote() : sp.getProcessName()),
                                    category);
                        }
                        newRouting.setTotalLaborHours(totalLabor);
                        newRouting.setTotalMachineHours(totalMachine);
                        newRouting.setProcessCount(processes.size());
                        routingService.updateById(newRouting);
                        builtRoutingId = newRouting.getRoutingId();
                        routingAction = "CREATE";
                        details.add("工艺路线[" + newRouting.getRoutingCode() + "]生成草稿(" + processes.size() + "道工序)");
                    } else {
                        routingAction = "SKIP_NO_PROCESS";
                        details.add("产品[" + prod.getProductCode() + "]无工序记录，未生成工艺路线");
                    }
                } else {
                    routingAction = "EXISTS";
                    builtRoutingId = existRouting.getRoutingId();
                    details.add("产品[" + prod.getProductCode() + "]已有路线[" + existRouting.getRoutingCode() + "]");
                }
            }
        }

        // 写转移记录
        com.jjx.sales.domain.entity.SalesSampleTransfer transfer = new com.jjx.sales.domain.entity.SalesSampleTransfer();
        transfer.setOrderId(orderId);
        transfer.setOrderNo(sampleOrder.getOrderNo());
        transfer.setTransferNo(transferNo);
        transfer.setProductId(builtProductId);
        transfer.setBomId(builtBomId);
        transfer.setRoutingId(builtRoutingId);
        transfer.setProductAction(productAction);
        transfer.setBomAction(bomAction);
        transfer.setRoutingAction(routingAction);
        transfer.setStatus("SUCCESS");
        transfer.setDetail(String.join("\n", details));
        transfer.setCreateBy(SecurityUtils.getUsername());
        sampleTransferMapper.insert(transfer);

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("transferNo", transferNo);
        result.put("transferId", transfer.getTransferId());
        result.put("productAction", productAction);
        result.put("bomAction", bomAction);
        result.put("routingAction", routingAction);
        result.put("productId", builtProductId);
        result.put("bomId", builtBomId);
        result.put("routingId", builtRoutingId);
        result.put("detail", details);
        log.info("样品单[{}] 资料转移完成[{}] 产品={} BOM={} 路线={}",
                sampleOrder.getOrderNo(), transferNo, productAction, bomAction, routingAction);
        return result;
    }

    @Override
    @Event(value = "sample.converted", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder convertToProduction(Long orderId) {
        SalesOrder sampleOrder = orderMapper.selectById(orderId);
        if (sampleOrder == null || sampleOrder.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        safeTransition(orderId,
                SampleOrderStatusEnum.CONFIRMED,
                SampleOrderStatusEnum.TRANSFERRED,
                "转量产");

        // 生成标准订单
        String standardOrderNo = redisSequenceService.generateBusinessNumber("SO", "标准订单号");

        SalesOrder standardOrder = new SalesOrder();
        standardOrder.setOrderNo(standardOrderNo);
        standardOrder.setQuotationId(sampleOrder.getQuotationId());
        standardOrder.setCustomerId(sampleOrder.getCustomerId());
        standardOrder.setCustomerName(sampleOrder.getCustomerName());
        standardOrder.setContactPerson(sampleOrder.getContactPerson());
        standardOrder.setContactPhone(sampleOrder.getContactPhone());
        standardOrder.setOrderDate(new Date());
        standardOrder.setDeliveryDate(sampleOrder.getDeliveryDate());
        standardOrder.setOrderType(OrderTypeEnum.STANDARD.getCode());
        standardOrder.setOrderStatus(1); // DRAFT
        standardOrder.setCurrency(sampleOrder.getCurrency());
        standardOrder.setExchangeRate(sampleOrder.getExchangeRate());
        standardOrder.setSalesManagerId(sampleOrder.getSalesManagerId());
        standardOrder.setSalesManagerName(sampleOrder.getSalesManagerName());
        standardOrder.setTotalQuantity(sampleOrder.getTotalQuantity());
        standardOrder.setTotalAmount(sampleOrder.getTotalAmount());
        standardOrder.setFinalAmount(sampleOrder.getFinalAmount());
        // 打样汇总自动计算（总工时+材料成本），替代手填 sampleCost/sampleWorkHours
        java.util.Map<String, Object> summary = getSampleSummary(orderId);
        java.math.BigDecimal totalHours = summary.get("totalHours") instanceof java.math.BigDecimal
                ? (java.math.BigDecimal) summary.get("totalHours") : java.math.BigDecimal.ZERO;
        java.math.BigDecimal materialCost = summary.get("materialCost") instanceof java.math.BigDecimal
                ? (java.math.BigDecimal) summary.get("materialCost") : java.math.BigDecimal.ZERO;
        standardOrder.setRemark("由样品单[" + sampleOrder.getOrderNo() + "]转量产生成"
                + (sampleOrder.getEngineeringNote() != null && !sampleOrder.getEngineeringNote().isEmpty()
                    ? "\n【工艺参数传承】" + sampleOrder.getEngineeringNote() : "")
                + (sampleOrder.getCurrentProcess() != null && !sampleOrder.getCurrentProcess().isEmpty()
                    ? "\n【最后工序】" + sampleOrder.getCurrentProcess() : "")
                + (materialCost.compareTo(java.math.BigDecimal.ZERO) > 0
                    ? "\n【材料成本】" + materialCost + "元" : "")
                + (totalHours.compareTo(java.math.BigDecimal.ZERO) > 0
                    ? "\n【打样工时】" + totalHours + "小时" : ""));

        orderMapper.insert(standardOrder);

        // 复制产品明细
        copyOrderProducts(sampleOrder.getOrderId(), standardOrder.getOrderId());

        // 更新报价单的 convertedOrderId（如果有报价单关联）
        if (sampleOrder.getQuotationId() != null) {
            try {
                SalesQuotation quotation = quotationMapper.selectById(sampleOrder.getQuotationId());
                if (quotation != null) {
                    quotation.setConvertedOrderId(standardOrder.getOrderId());
                    quotation.setConvertTime(java.time.LocalDateTime.now());
                    quotationMapper.updateById(quotation);
                }
            } catch (Exception e) {
                log.warn("更新报价单转换信息失败: {}", e.getMessage());
            }
        }

        // 回写样品单的 convertedOrderId
        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setConvertedOrderId(standardOrder.getOrderId());
        update.setConvertOrderTime(new Date());
        orderMapper.updateById(update);

        // ========== 转量产校验（DEV-505 资料转移方案）==========
        // 明细产品必须已建档且已发布（产品资料转移后走产品审核流）
        java.util.List<com.jjx.sales.domain.vo.SalesOrderProductVO> prodList =
                orderProductService.getListByOrderId(standardOrder.getOrderId());
        if (prodList != null) {
            for (com.jjx.sales.domain.vo.SalesOrderProductVO prod : prodList) {
                if (prod.getProductId() == null) continue;
                com.jjx.product.domain.entity.Product product = productMapper.selectById(prod.getProductId());
                if (product == null) {
                    throw new BusinessException("产品[" + prod.getProductCode() + "]不存在，请先完成产品资料转移建档");
                }
                if (product.getProductStatus() != null && product.getProductStatus() != 6) {
                    throw new BusinessException("产品[" + prod.getProductCode() + "]未发布（状态:" + product.getProductStatus() + "），请先完成产品资料转移并审核发布");
                }
            }
        }
        // 提示：量产 BOM/工艺路线由【产品资料转移】建档并走工程审核，此处不再自动生成

        log.info("样品单[{}] 转量产成功，生成标准订单[{}] (orderId={})",
                sampleOrder.getOrderNo(), standardOrderNo, standardOrder.getOrderId());
        return orderMapper.selectById(orderId);
    }

    /**
     * 样品单作废
     * 非终态（未转量产/未关闭/未作废）样品单可作废
     */
    @Override
    @Event(value = "sample.cancelled", bizId = "#orderId", bizType = "'sample'")
    public SalesOrder cancelSample(Long orderId, String cancelReason) {
        SalesOrder sampleOrder = orderMapper.selectById(orderId);
        if (sampleOrder == null || sampleOrder.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        // 终态不允许作废：已转量产(7)/已关闭(8)/已作废(10)
        Integer current = sampleOrder.getSampleStatus();
        if (SampleOrderStatusEnum.TRANSFERRED.getCode().equals(current)
                || SampleOrderStatusEnum.CLOSED.getCode().equals(current)
                || SampleOrderStatusEnum.CANCELLED.getCode().equals(current)) {
            String name = SampleOrderStatusEnum.getByCodeSafe(current)
                    .map(SampleOrderStatusEnum::getName).orElse("未知");
            throw new BusinessException("当前状态[" + name + "]不允许作废");
        }

        int affected = orderMapper.updateSampleStatus(orderId, current, SampleOrderStatusEnum.CANCELLED.getCode());
        if (affected == 0) {
            throw new BusinessException("样品单状态已变更，无法作废，请刷新后重试");
        }

        // 记录作废原因
        try {
            SalesOrder update = new SalesOrder();
            update.setOrderId(orderId);
            update.setRemark("[作废] " + (cancelReason != null ? cancelReason : ""));
            orderMapper.updateById(update);
        } catch (Exception e) {
            log.warn("记录样品单作废原因失败: {}", e.getMessage());
        }

        // DEV-527：已接单 → 派任务到接单人（具体人），未接单由事件按角色通知工程管理
        if (sampleOrder.getEngineeringAcceptor() != null && !sampleOrder.getEngineeringAcceptor().isEmpty()) {
            try {
                com.jjx.system.domain.entity.SysTask task = new com.jjx.system.domain.entity.SysTask();
                task.setTaskCode("sample.cancelled-" + System.currentTimeMillis());
                task.setTaskType("general");
                task.setTitle("样品单【" + sampleOrder.getOrderNo() + "】已作废，请停止打样并确认");
                task.setDescription("作废原因：" + (cancelReason != null ? cancelReason : "-")
                        + "\n接单人：" + sampleOrder.getEngineeringAcceptor());
                task.setKanbanModule("office");
                task.setAssignRole(9L);
                task.setAssigneeName(sampleOrder.getEngineeringAcceptor());
                task.setSourceEvent("sample.cancelled");
                task.setBizType("sample");
                task.setBizId(orderId);
                task.setPriority("high");
                task.setStatus(0);
                task.setStartTime(java.time.LocalDateTime.now());
                sysTaskMapper.insert(task);
                log.info("样品单[{}] 作废，已派任务给接单人[{}]", sampleOrder.getOrderNo(), sampleOrder.getEngineeringAcceptor());
            } catch (Exception te) {
                log.warn("派任务给接单人失败: {}", te.getMessage());
            }
        }

        log.info("样品单[{}] 已作废，原因: {}", sampleOrder.getOrderNo(), cancelReason);
        return orderMapper.selectById(orderId);
    }

    /**
     * 复制报价单明细到样品单/订单（产品资料转移与转量产的数据基础）
     */
    private void copyQuotationItemsToOrder(Long quotationId, Long targetOrderId) {
        java.util.List<com.jjx.sales.domain.entity.SalesQuotationItem> items = quotationItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesQuotationItem>()
                        .eq(com.jjx.sales.domain.entity.SalesQuotationItem::getQuotationId, quotationId));
        if (items == null || items.isEmpty()) {
            log.warn("报价单[{}]无明细，未复制到订单[{}]", quotationId, targetOrderId);
            return;
        }
        java.util.List<com.jjx.sales.domain.dto.SalesOrderProductDTO> dtos = new java.util.ArrayList<>();
        for (com.jjx.sales.domain.entity.SalesQuotationItem it : items) {
            com.jjx.sales.domain.dto.SalesOrderProductDTO dto = new com.jjx.sales.domain.dto.SalesOrderProductDTO();
            dto.setOrderId(targetOrderId);
            dto.setProductId(it.getProductId());
            dto.setProductCode(it.getProductCode());
            dto.setProductName(it.getProductName());
            dto.setQuantity(it.getQuantity());
            dto.setUnitPrice(it.getUnitPrice());
            dto.setAmount(it.getAmount());
            dto.setUnit(it.getUnit());
            dto.setSpecification(it.getCustomRequirements());
            dtos.add(dto);
        }
        orderProductService.batchAdd(dtos);
        log.info("报价单[{}]明细已复制到订单[{}] ({}条)", quotationId, targetOrderId, dtos.size());
    }

    /**
     * 复制样品单的产品明细到新标准订单
     */
    private void copyOrderProducts(Long sourceOrderId, Long targetOrderId) {
        List<SalesOrderProductDTO> items = new ArrayList<>();
        var productVOs = orderProductService.getListByOrderId(sourceOrderId);

        for (var vo : productVOs) {
            SalesOrderProductDTO dto = new SalesOrderProductDTO();
            dto.setOrderId(targetOrderId);
            dto.setProductId(vo.getProductId());
            dto.setProductCode(vo.getProductCode());
            dto.setProductName(vo.getProductName());
            dto.setQuantity(vo.getQuantity());
            dto.setUnitPrice(vo.getUnitPrice());
            dto.setAmount(vo.getAmount());
            dto.setUnit(vo.getUnit());
            dto.setSpecification(vo.getSpecification());
            dto.setRemark(vo.getRemark());
            items.add(dto);
        }

        if (!items.isEmpty()) {
            orderProductService.batchAdd(items);
            log.info("转量产时复制了 {} 条产品明细到标准订单[{}]", items.size(), targetOrderId);
        }
    }

    @Override
    public SalesOrder selectById(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getDeleted() == 1) {
            throw new BusinessException("样品单不存在或已被删除");
        }
        return order;
    }

    @Override
    public List<SalesOrder> selectSampleList(Long customerId, Integer sampleStatus, Long salesPersonId) {
        return selectSampleList(customerId, sampleStatus, salesPersonId, null);
    }

    @Override
    public List<SalesOrder> selectSampleList(Long customerId, Integer sampleStatus, Long salesPersonId, Boolean hasAcceptor) {
        java.util.List<SalesOrder> list = orderMapper.selectSampleOrders();
        if (list == null || list.isEmpty()) {
            return list;
        }
        java.util.List<SalesOrder> result = list;
        if (customerId != null) {
            result = result.stream().filter(o -> customerId.equals(o.getCustomerId())).collect(java.util.stream.Collectors.toList());
        }
        if (sampleStatus != null) {
            result = result.stream().filter(o -> sampleStatus.equals(o.getSampleStatus())).collect(java.util.stream.Collectors.toList());
        }
        if (hasAcceptor != null && hasAcceptor) {
            result = result.stream().filter(o -> o.getEngineeringAcceptor() != null && !o.getEngineeringAcceptor().isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        }
        return result;
    }
}
