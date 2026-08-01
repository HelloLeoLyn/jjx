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
    private final RedisSequenceService redisSequenceService;
    private final ISalesOrderProductService orderProductService;
    private final SalesSampleRoundMapper sampleRoundMapper;
    private final SalesSampleProcessMapper sampleProcessMapper;
    private final SalesSampleBomMapper sampleBomMapper;
    private final com.jjx.system.service.ISysAttachmentService attachmentService;
    private final com.jjx.engineering.service.IBomService bomService;
    private final com.jjx.engineering.service.IRoutingService routingService;
    private final com.jjx.product.mapper.ProductBomItemMapper bomItemMapper;
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

        // ========== 转量产建档联动（DEV-457 重构：从工序单元聚合生成标准BOM+工艺路线）==========
        try {
            java.util.List<com.jjx.sales.domain.vo.SalesOrderProductVO> prodList =
                    orderProductService.getListByOrderId(standardOrder.getOrderId());
            if (prodList != null) {
                for (com.jjx.sales.domain.vo.SalesOrderProductVO prod : prodList) {
                    Long pid = prod.getProductId();
                    if (pid == null) continue;

                    // 查询打样工序单元（本单全部工序记录）
                    java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> processes =
                            sampleProcessMapper.selectByOrderId(orderId);

                    // 1. 检查是否已有 BOM（当前生效版）
                    com.jjx.engineering.domain.entity.Bom existBom = bomService.getOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.Bom>()
                                    .eq(com.jjx.engineering.domain.entity.Bom::getProductId, pid)
                                    .eq(com.jjx.engineering.domain.entity.Bom::getIsCurrent, 1)
                                    .last("LIMIT 1"));
                    if (existBom == null) {
                        // 2. 无BOM → 从工序单元材料聚合生成BOM草稿（approve_status=1）
                        java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> procsWithMat =
                                processes != null ? processes : java.util.Collections.emptyList();
                        if (!procsWithMat.isEmpty()) {
                            com.jjx.engineering.domain.entity.Bom newBom = new com.jjx.engineering.domain.entity.Bom();
                            newBom.setBomCode("BOM-" + prod.getProductCode() + "-SAMPLE");
                            newBom.setBomName(prod.getProductName() + "（打样传承BOM）");
                            newBom.setProductId(pid);
                            newBom.setBomVersion("V1");
                            newBom.setBomType("manufacturing");
                            newBom.setIsCurrent(1);
                            newBom.setApproveStatus(1L); // 草稿
                            newBom.setRemark("由样品单[" + sampleOrder.getOrderNo() + "]打样工序单元自动生成，请工程确认后批准");
                            newBom.setCreateBy(SecurityUtils.getUsername());
                            bomService.save(newBom);

                            // 明细：遍历工序单元的材料JSON → 正式BOM明细（按物料名聚合去重）
                            int order = 1;
                            java.util.Map<String, com.jjx.product.domain.entity.ProductBomItem> aggMap = new java.util.LinkedHashMap<>();
                            for (com.jjx.sales.domain.entity.SalesSampleProcess sp : procsWithMat) {
                                if (sp.getMaterials() == null || sp.getMaterials().isEmpty()) continue;
                                try {
                                    java.util.List<java.util.Map<String, Object>> mats =
                                            new com.fasterxml.jackson.databind.ObjectMapper().readValue(sp.getMaterials(),
                                                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
                                    for (java.util.Map<String, Object> m : mats) {
                                        String name = m.get("name") != null ? m.get("name").toString() : null;
                                        if (name == null || name.isEmpty()) continue;
                                        String key = name + "|" + (m.get("spec") != null ? m.get("spec") : "");
                                        com.jjx.product.domain.entity.ProductBomItem item = aggMap.get(key);
                                        if (item == null) {
                                            item = new com.jjx.product.domain.entity.ProductBomItem();
                                            item.setBomId(newBom.getBomId());
                                            item.setMaterialName(name);
                                            item.setSpecification(m.get("spec") != null ? m.get("spec").toString() : null);
                                            item.setUnit(m.get("unit") != null ? m.get("unit").toString() : "PCS");
                                            item.setLayer(sp.getProcessName()); // 层=工序名，保留工序归属
                                            item.setItemOrder(order++);
                                            item.setQuantity(java.math.BigDecimal.ZERO);
                                            aggMap.put(key, item);
                                            // 按物料名称匹配库存物料表，补 material_id/material_code
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
                                        // 累加数量
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
                            for (com.jjx.product.domain.entity.ProductBomItem item : aggMap.values()) {
                                bomItemMapper.insert(item);
                            }
                            log.info("样品单[{}] 转量产从工序单元生成BOM草稿[{}] ({}条明细)",
                                    orderId, newBom.getBomCode(), aggMap.size());
                        } else {
                            log.warn("样品单[{}] 转量产：产品[{}]无打样工序记录，未生成BOM草稿",
                                    orderId, prod.getProductCode());
                        }
                    }

                    // 3. 工艺路线：从工序单元生成路线草稿（每道工序→一个步骤）
                    com.jjx.engineering.domain.entity.Routing existRouting = routingService.getOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.Routing>()
                                    .eq(com.jjx.engineering.domain.entity.Routing::getProductId, pid)
                                    .last("LIMIT 1"));
                    if (existRouting == null && processes != null && !processes.isEmpty()) {
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
                            // 匹配标准工序（按名称）
                            com.jjx.engineering.domain.entity.StandardProcess std = standardProcessMapper.selectOne(
                                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.StandardProcess>()
                                            .eq(com.jjx.engineering.domain.entity.StandardProcess::getProcessName, sp.getProcessName())
                                            .last("LIMIT 1"));
                            Long stdProcessId = std != null ? std.getProcessId() : null;
                            String category = std != null ? std.getProcessCategory() : "M";
                            // 耗时：分钟→小时
                            java.math.BigDecimal laborHours = sp.getDurationMinutes() != null
                                    ? java.math.BigDecimal.valueOf(sp.getDurationMinutes()).divide(java.math.BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP)
                                    : (std != null && std.getStandardLaborHours() != null ? std.getStandardLaborHours() : java.math.BigDecimal.ZERO);
                            java.math.BigDecimal machineHours = std != null && std.getStandardMachineHours() != null
                                    ? std.getStandardMachineHours() : laborHours;
                            totalLabor = totalLabor.add(laborHours);
                            totalMachine = totalMachine.add(machineHours);
                            routingItemMapper.insertItem(newRouting.getRoutingId(),
                                    stdProcessId != null ? stdProcessId : -1L,
                                    stepOrder++, laborHours, machineHours,
                                    sp.getProcessNote() != null ? sp.getProcessNote() : null,
                                    "打样传承: " + (sp.getProcessNote() != null ? sp.getProcessNote() : sp.getProcessName()),
                                    category);
                        }
                        newRouting.setTotalLaborHours(totalLabor);
                        newRouting.setTotalMachineHours(totalMachine);
                        newRouting.setProcessCount(processes.size());
                        routingService.updateById(newRouting);
                        log.info("样品单[{}] 转量产从工序单元生成路线草稿[{}] ({}道工序)",
                                orderId, newRouting.getRoutingCode(), processes.size());
                    } else if (existRouting == null) {
                        log.warn("样品单[{}] 转量产：产品[{}]无工序记录，未生成工艺路线", orderId, prod.getProductCode());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("样品单[{}] 转量产建档联动失败: {}", orderId, e.getMessage());
        }


        log.info("样品单[{}] 转量产成功，生成标准订单[{}] (orderId={})",
                sampleOrder.getOrderNo(), standardOrderNo, standardOrder.getOrderId());
        return orderMapper.selectById(orderId);
    }

    /**
     * 样品单作废
     * 非终态（未转量产/未关闭/未作废）样品单可作废
     */
    @Override
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

        log.info("样品单[{}] 已作废，原因: {}", sampleOrder.getOrderNo(), cancelReason);
        return orderMapper.selectById(orderId);
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
        return orderMapper.selectSampleOrders();
    }
}
