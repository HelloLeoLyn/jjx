package com.jjx.production.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.QualityInspectionCreateDTO;
import com.jjx.production.domain.dto.QualityJudgeDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.entity.ProductionQualityInspection;
import com.jjx.production.domain.vo.QualityInspectionVO;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.enums.QualityInspectionTypeEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionQualityInspectionMapper;
import com.jjx.production.service.QualityActionService;
import com.jjx.production.service.QualityInspectionService;
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
        if (passQty.add(failQty).compareTo(total) > 0) {
            throw new BusinessException("合格+不合格数量不能超过检验数量");
        }
        if (pass && passQty.signum() <= 0) {
            throw new BusinessException("判定合格时合格数量必须大于 0");
        }

        // 写入质量事实（不可变：仅本次判定写入，之后禁止覆盖）
        entity.setResult(dto.getResult());
        entity.setTotalQty(total);
        entity.setPassQty(passQty);
        entity.setFailQty(failQty);
        entity.setDefectDesc(dto.getDefectDesc());
        entity.setRemark(dto.getRemark());
        entity.setInspector(com.jjx.system.utils.SecurityUtils.getUsername());
        entity.setInspectTime(java.time.LocalDateTime.now());
        inspectionMapper.updateById(entity);

        // FQC 生产联动（仅完工质检；IPQC/IQC/OQC 只记录质量事实）
        if (QualityInspectionTypeEnum.FQC.getCode().equals(entity.getInspectionType())) {
            if (pass) {
                handleFqcPass(entity, passQty);
            } else {
                handleFqcFail(entity);
            }
        }
        log.info("质检判定 inspectionId={} type={} result={} total={} pass={} fail={}",
                inspectionId, entity.getInspectionType(), dto.getResult(), total, passQty, failQty);
        return qualityInspectionService.getById(inspectionId);
    }

    /** FQC PASS：finishedQuantity = passQty（PASS=解锁 Order complete，不自动完成） */
    private void handleFqcPass(ProductionQualityInspection entity, BigDecimal passQty) {
        if (entity.getOrderId() == null) return;
        try {
            ProductionOrder order = productionOrderMapper.selectById(entity.getOrderId());
            if (order != null) {
                order.setFinishedQuantity(passQty);
                // 通过 → 清除返工标记
                if (order.getReworkFlag() != null && order.getReworkFlag() == 1) {
                    order.setReworkFlag(0);
                }
                productionOrderMapper.updateById(order);
                log.info("FQC PASS：order={} finishedQuantity={}", entity.getOrderId(), passQty);
            }
        } catch (Exception e) {
            log.warn("FQC PASS 更新 finishedQuantity 失败: {}", e.getMessage());
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
        // 恢复最后有效 Execution 为 EXECUTING（报工允许 EXECUTING/PAUSED，complete 仅 EXECUTING）
        if (entity.getExecutionId() != null) {
            try {
                ProductionOperationExecution exec = executionMapper.selectById(entity.getExecutionId());
                if (exec != null && ExecutionStatusEnum.COMPLETED.getCode().equals(exec.getExecutionStatus())) {
                    exec.setExecutionStatus(ExecutionStatusEnum.EXECUTING.getCode());
                    exec.setActualEndTime(null);
                    executionMapper.updateById(exec);
                    log.warn("FQC FAIL：execution={} 恢复 EXECUTING（可继续生产/报工）", entity.getExecutionId());
                }
            } catch (Exception e) {
                log.warn("FQC FAIL 恢复 execution 失败: {}", e.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reinspect(Long inspectionId) {
        ProductionQualityInspection old = inspectionMapper.selectById(inspectionId);
        if (old == null) throw new BusinessException("检验单不存在: " + inspectionId);
        // 复制上下文新建 PENDING（不覆盖历史；不需要 previousInspectionId）
        QualityInspectionCreateDTO dto = new QualityInspectionCreateDTO();
        dto.setInspectionType(old.getInspectionType());
        dto.setOrderId(old.getOrderId());
        dto.setExecutionId(old.getExecutionId());
        dto.setWorkReportId(old.getWorkReportId());
        dto.setMaterialId(old.getMaterialId());
        dto.setProductId(old.getProductId());
        dto.setInspector(com.jjx.system.utils.SecurityUtils.getUsername());
        dto.setRemark("复检（源自 " + old.getInspectionNo() + "）");
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
}
