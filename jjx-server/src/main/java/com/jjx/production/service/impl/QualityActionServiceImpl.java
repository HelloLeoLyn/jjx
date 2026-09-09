package com.jjx.production.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.QualityInspectionCreateDTO;
import com.jjx.production.domain.dto.QualityJudgeDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.entity.ProductionQualityInspection;
import com.jjx.production.domain.vo.QualityInspectionVO;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.ExecutionTypeEnum;
import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.enums.QualityInspectionTypeEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionQualityInspectionMapper;
import com.jjx.production.service.QualityActionService;
import com.jjx.production.service.QualityInspectionService;
import com.jjx.production.service.ProductionTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 质检正式动作实现（P3-C）
 *
 * 原则：
 *  - QualityInspection = 质量事实，WorkReport = 生产申报事实，两者禁止互相覆盖。
 *  - 已判定(PASS/FAIL) 不可修改结果/数量；复检必须新建记录。
 *  - FQC 在最后有效 Execution 完成后自动创建 PENDING（createFqcForExecution，幂等）。
 *  - FQC PASS → ProductionOrder.finishedQuantity = passQty（不覆盖 WorkReport/Execution qualified）。
 *  - FQC FAIL → 最后有效 Execution 恢复为 EXECUTING（可继续报工/可再次完成，复用现有状态机）。
 *  - IPQC V1 人工创建，不自动触发；FAIL 只记录质量事实，不控制完整生产状态机。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityActionServiceImpl implements QualityActionService {

    private final ProductionQualityInspectionMapper inspectionMapper;
    private final ProductionOperationExecutionMapper executionMapper;
    private final ProductionOrderMapper productionOrderMapper;
    private final QualityInspectionService qualityInspectionService;
    private final ProductionTaskService productionTaskService;
    private final org.springframework.beans.factory.ObjectProvider<com.jjx.inventory.service.InventoryInboundService> inventoryInboundServiceProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInspection(QualityInspectionCreateDTO dto) {
        // workReportId 非空 → 后端反查 WorkReport 校验关联一致性（不信任客户端组合 ID）
        if (dto.getWorkReportId() != null) {
            boolean linkOk = qualityInspectionService.checkWorkReportLink(
                    dto.getWorkReportId(), dto.getExecutionId(), dto.getOrderId());
            if (!linkOk) {
                throw new BusinessException("报工与工序/订单关联不一致，无法创建质检");
            }
        }
        return qualityInspectionService.create(dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityInspectionVO judge(Long inspectionId, QualityJudgeDTO dto) {
        ProductionQualityInspection entity = inspectionMapper.selectById(inspectionId);
        if (entity == null) throw new BusinessException("检验单不存在: " + inspectionId);

        // 不可变：已判定（PASS/FAIL）禁止再次判定
        String cur = entity.getResult();
        if (QualityInspectionResultEnum.PASS.getCode().equals(cur)
                || QualityInspectionResultEnum.FAIL.getCode().equals(cur)) {
            throw new BusinessException("质检结果已确定，不可修改；复检请新建质检单");
        }
        // 判定结果合法性
        boolean pass = QualityInspectionResultEnum.PASS.getCode().equals(dto.getResult());
        boolean fail = QualityInspectionResultEnum.FAIL.getCode().equals(dto.getResult());
        if (!pass && !fail) {
            throw new BusinessException("判定结果不合法（PASS/FAIL）");
        }
        // 数量校验：>=0 且 合格+不合格 <= 检验数量
        BigDecimal total = dto.getTotalQty() == null ? BigDecimal.ZERO : dto.getTotalQty();
        BigDecimal passQty = dto.getPassQty() == null ? BigDecimal.ZERO : dto.getPassQty();
        BigDecimal failQty = dto.getFailQty() == null ? BigDecimal.ZERO : dto.getFailQty();
        if (total.signum() < 0 || passQty.signum() < 0 || failQty.signum() < 0) {
            throw new BusinessException("检验/合格/不合格数量不能为负数");
        }
        if (passQty.add(failQty).compareTo(total) != 0) {
            throw new BusinessException("合格+不合格数量必须等于检验数量");
        }
        if (pass && passQty.signum() <= 0) {
            throw new BusinessException("判定合格时合格数量必须大于 0");
        }

        // 写入质量事实（不可变：仅本次判定写入，之后禁止覆盖）
        entity.setResult(dto.getResult());
        entity.setTotalQty(total);
        entity.setPassQty(passQty);
        entity.setFailQty(failQty);
        entity.setRemainingFailQty(failQty);
        if (failQty.signum() > 0 && entity.getDisposition() == null) {
            entity.setDisposition(com.jjx.production.enums.QualityDispositionEnum.INTERNAL_SORT.getCode());
        }
        entity.setDefectDesc(dto.getDefectDesc());
        entity.setRemark(dto.getRemark());
        entity.setInspector(com.jjx.system.utils.SecurityUtils.getUsername());
        entity.setInspectTime(java.time.LocalDateTime.now());
        inspectionMapper.updateById(entity);

        // FQC 生产联动（仅完工质检；IPQC/IQC/OQC 只记录质量事实）
        if (QualityInspectionTypeEnum.FQC.getCode().equals(entity.getInspectionType())) {
            if (passQty.signum() > 0) {
                handleFqcPass(entity, passQty);
            }
            if (failQty.signum() > 0) {
                handleFqcFail(entity);
            }
        }
        log.info("质检判定 inspectionId={} type={} result={} total={} pass={} fail={}",
                inspectionId, entity.getInspectionType(), dto.getResult(), total, passQty, failQty);
        return qualityInspectionService.getById(inspectionId);
    }

    /**
     * FQC PASS：成品口径统一写点（2026-09-09 Leo 定，口径Y）——
     * completedQuantity = finishedQuantity = passQty（质检通过数）；
     * remainingQuantity = max(0, plannedQuantity - passQty)。
     * 工序完工不再直接写这三列（updateOrderCompletedQuantity 已移除），避免“Σ工序合格数”污染成品口径。
     */
    private void handleFqcPass(ProductionQualityInspection entity, BigDecimal passQty) {
        if (entity.getOrderId() == null) return;
        try {
            ProductionOrder order = productionOrderMapper.selectById(entity.getOrderId());
            if (order != null) {
                BigDecimal pass = passQty == null ? BigDecimal.ZERO : passQty;
                BigDecimal finished = order.getFinishedQuantity() == null
                        ? BigDecimal.ZERO : order.getFinishedQuantity();
                finished = finished.add(pass);
                order.setFinishedQuantity(finished);
                order.setCompletedQuantity(finished);
                if (order.getPlannedQuantity() != null) {
                    BigDecimal remain = order.getPlannedQuantity().subtract(finished);
                    order.setRemainingQuantity(remain.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remain);
                }
                // 通过 → 清除返工标记
                if (order.getReworkFlag() != null && order.getReworkFlag() == 1) {
                    order.setReworkFlag(0);
                }
                productionOrderMapper.updateById(order);
                com.jjx.inventory.service.InventoryInboundService inboundService = inventoryInboundServiceProvider.getIfAvailable();
                if (inboundService != null && pass.signum() > 0) {
                    inboundService.createFromProduction(order.getOrderId(), entity.getInspectionId(), pass);
                }
                log.info("FQC PASS：order={} passQty={}（成品口径：完成={}，剩余={}）",
                        entity.getOrderId(), pass, finished, order.getRemainingQuantity());
            }
        } catch (Exception e) {
            log.warn("FQC PASS 更新成品数量失败: {}", e.getMessage());
        }
    }

    /**
     * FQC FAIL：Order 保持未完成；最后有效 Execution 恢复到 EXECUTING（可继续报工/可再次完成）。
     * 复用现有 ExecutionStatusEnum.EXECUTING，不新增返工状态机。
     */
    private void handleFqcFail(ProductionQualityInspection entity) {
        // 标记返工
        if (entity.getOrderId() != null) {
            try {
                ProductionOrder order = productionOrderMapper.selectById(entity.getOrderId());
                if (order != null) {
                    order.setReworkFlag(1);
                    productionOrderMapper.updateById(order);
                }
            } catch (Exception e) {
                log.warn("FQC FAIL 标记返工失败: {}", e.getMessage());
            }
        }
        // 原执行保持完成，不良通过独立 REWORK 执行返工，避免污染原报工事实。
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reinspect(Long inspectionId) {
        ProductionQualityInspection old = inspectionMapper.selectById(inspectionId);
        if (old == null) throw new BusinessException("检验单不存在: " + inspectionId);
        // 复制上下文新建 PENDING（不覆盖历史；不需要 previousInspectionId）
        QualityInspectionCreateDTO dto = new QualityInspectionCreateDTO();
        dto.setInspectionType(old.getInspectionType());
        dto.setSourceType(old.getSourceType());
        dto.setSourceId(old.getSourceId());
        dto.setSourceItemId(old.getSourceItemId());
        dto.setBatchNo(old.getBatchNo());
        dto.setPreviousInspectionId(old.getInspectionId());
        dto.setInspectionVersion(old.getInspectionVersion() == null ? 2 : old.getInspectionVersion() + 1);
        dto.setDisposition(old.getDisposition());
        dto.setOrderId(old.getOrderId());
        dto.setExecutionId(old.getExecutionId());
        dto.setWorkReportId(old.getWorkReportId());
        dto.setMaterialId(old.getMaterialId());
        dto.setProductId(old.getProductId());
        dto.setInspector(com.jjx.system.utils.SecurityUtils.getUsername());
        dto.setRemark("复检（源自 " + old.getInspectionNo() + "）");
        java.util.List<com.jjx.production.domain.vo.InspectionItemVO> oldItems =
                qualityInspectionService.getById(inspectionId).getItems();
        if (oldItems != null) {
            dto.setItems(oldItems.stream().map(item -> {
                com.jjx.production.domain.dto.InspectionItemDTO copy =
                        new com.jjx.production.domain.dto.InspectionItemDTO();
                copy.setCheckItem(item.getCheckItem());
                copy.setStandard(item.getStandard());
                copy.setInspectionMethod(item.getInspectionMethod());
                copy.setEquipment(item.getEquipment());
                copy.setActualValue(item.getActualValue());
                copy.setResult(item.getResult());
                copy.setCrQuantity(item.getCrQuantity());
                copy.setMaQuantity(item.getMaQuantity());
                copy.setMiQuantity(item.getMiQuantity());
                copy.setRemark(item.getRemark());
                return copy;
            }).collect(java.util.stream.Collectors.toList()));
        }
        Long newId = qualityInspectionService.create(dto);
        log.info("质检复检：{} → 新单 {}", old.getInspectionNo(), newId);
        return newId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFqcForExecution(Long executionId) {
        ProductionOperationExecution exec = executionMapper.selectById(executionId);
        if (exec == null) throw new BusinessException("工序执行记录不存在: " + executionId);
        // 幂等：同 execution 已有 PENDING FQC 不重复创建；历史 FAIL 不阻止新建
        if (qualityInspectionService.hasPendingFqc(executionId)) {
            log.info("execution={} 已有 PENDING FQC，跳过自动创建", executionId);
            return null;
        }
        QualityInspectionCreateDTO dto = new QualityInspectionCreateDTO();
        dto.setInspectionType(QualityInspectionTypeEnum.FQC.getCode());
        dto.setOrderId(exec.getOrderId());
        dto.setExecutionId(executionId);
        if (ExecutionTypeEnum.REWORK.getCode().equals(exec.getExecutionType())) {
            dto.setPreviousInspectionId(exec.getSourceInspectionId());
            dto.setInspectionVersion(2);
        }
        dto.setWorkReportId(null); // FQC：不绑定报工
        // productId 从订单带出（如存在）
        try {
            ProductionOrder order = productionOrderMapper.selectById(exec.getOrderId());
            if (order != null) dto.setProductId(order.getProductId());
        } catch (Exception ignored) {
        }
        dto.setInspector(com.jjx.system.utils.SecurityUtils.getUsername());
        dto.setRemark("最后工序完成自动创建完工质检");
        Long fqcId = qualityInspectionService.create(dto);
        log.info("最后工序 execution={} 完成，自动创建 FQC={}", executionId, fqcId);
        return fqcId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long disposeFqcFailure(Long inspectionId,
            com.jjx.production.domain.dto.FqcDispositionDTO dto) {
        ProductionQualityInspection inspection = inspectionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionQualityInspection>()
                        .eq(ProductionQualityInspection::getInspectionId, inspectionId)
                        .last("FOR UPDATE"));
        if (inspection == null || !QualityInspectionTypeEnum.FQC.getCode().equals(inspection.getInspectionType())) {
            throw new BusinessException("FQC检验单不存在");
        }
        if (!QualityInspectionResultEnum.FAIL.getCode().equals(inspection.getResult())) {
            throw new BusinessException("只有不合格FQC可执行处置");
        }
        BigDecimal remaining = inspection.getRemainingFailQty() == null
                ? BigDecimal.ZERO : inspection.getRemainingFailQty();
        if (dto.getQuantity().compareTo(remaining) > 0) {
            throw new BusinessException("处置数量不能超过待处置不良余量" + remaining);
        }
        com.jjx.production.enums.QualityDispositionEnum action =
                com.jjx.production.enums.QualityDispositionEnum.fromCode(dto.getAction());
        if (action != com.jjx.production.enums.QualityDispositionEnum.INTERNAL_SORT
                && action != com.jjx.production.enums.QualityDispositionEnum.SCRAP) {
            throw new BusinessException("FQC不良仅支持内部返工或报废");
        }

        inspection.setDisposition(action.getCode());
        inspection.setRemainingFailQty(remaining.subtract(dto.getQuantity()));
        if (dto.getRemark() != null && !dto.getRemark().isBlank()) inspection.setRemark(dto.getRemark());
        inspectionMapper.updateById(inspection);
        if (action == com.jjx.production.enums.QualityDispositionEnum.SCRAP) return null;

        ProductionOperationExecution source = executionMapper.selectById(inspection.getExecutionId());
        if (source == null) throw new BusinessException("FQC未关联有效工序执行");
        Integer maxOrder = executionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionOperationExecution>()
                        .eq(ProductionOperationExecution::getOrderId, inspection.getOrderId()))
                .stream().map(ProductionOperationExecution::getProcessOrder)
                .filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(0);
        ProductionOperationExecution rework = new ProductionOperationExecution();
        rework.setExecutionType(ExecutionTypeEnum.REWORK.getCode());
        rework.setSourceInspectionId(inspectionId);
        rework.setOrderId(source.getOrderId());
        rework.setProcessId(source.getProcessId());
        rework.setProcessName(source.getProcessName());
        rework.setMajorCategory(source.getMajorCategory());
        rework.setProcessOrder(maxOrder + 1);
        rework.setTaskSeq(0L);
        rework.setInputQuantity(dto.getQuantity());
        rework.setOutputQuantity(BigDecimal.ZERO);
        rework.setQualifiedQuantity(BigDecimal.ZERO);
        rework.setDefectiveQuantity(BigDecimal.ZERO);
        rework.setExecutionStatus(ExecutionStatusEnum.PENDING.getValue());
        executionMapper.insert(rework);
        productionTaskService.createFirstTask(rework.getExecutionId(), dto.getQuantity());
        log.info("FQC不良返工: inspectionId={}, executionId={}, quantity={}",
                inspectionId, rework.getExecutionId(), dto.getQuantity());
        return rework.getExecutionId();
    }
}
