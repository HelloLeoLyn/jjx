package com.jjx.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.service.InventoryInboundService;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityLotItem;
import com.jjx.quality.dto.QualityLotCreateDTO;
import com.jjx.quality.dto.QualityLotItemDTO;
import com.jjx.quality.dto.QualityLotJudgeDTO;
import com.jjx.quality.mapper.QualityLotMapper;
import com.jjx.quality.enums.QualityLotStatusEnum;
import com.jjx.quality.service.QualityFinishService;
import com.jjx.quality.service.QualityLotService;
import com.jjx.quality.service.QualityNcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 成品检验收口服务实现 —— dev-20260917-007
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityFinishServiceImpl implements QualityFinishService {

    private final QualityLotService qualityLotService;
    private final QualityNcrService qualityNcrService;
    private final QualityLotMapper lotMapper;
    private final InventoryInboundService inventoryInboundService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityLot judgeLot(Long lotId, QualityLotJudgeDTO dto) {
        if (dto == null) {
            throw new BusinessException("判定参数不能为空");
        }
        QualityLot lot = qualityLotService.getLot(lotId);
        BigDecimal inspected = nz(dto.getInspectedQuantity());
        BigDecimal pass = nz(dto.getPassQuantity());
        BigDecimal fail = nz(dto.getFailQuantity());
        String result = dto.getResult();
        if (result == null || result.isBlank()) {
            result = fail.signum() > 0 ? "fail" : "pass";
        }
        String inspector = null;
        try {
            inspector = com.jjx.system.utils.SecurityUtils.getUsername();
        } catch (Exception ignored) {
        }
        // 复检更正：同一批再次判定时，先冲掉本批已入账的合格量（差额同步会自动做减法）
        lot = qualityLotService.applyJudgement(lotId, inspected, pass, fail, result, inspector);
        if (fail.signum() > 0) {
            BigDecimal cr = BigDecimal.ZERO, ma = BigDecimal.ZERO, mi = BigDecimal.ZERO;
            for (QualityLotItem item : qualityLotService.listItems(lotId)) {
                cr = cr.add(nz(item.getCrQuantity()));
                ma = ma.add(nz(item.getMaQuantity()));
                mi = mi.add(nz(item.getMiQuantity()));
            }
            qualityNcrService.syncFromLot(lot, fail, cr, ma, mi, dto.getDefectReason(), inspector);
        }
        if ("FQC".equals(lot.getLotType()) && lot.getOrderId() != null) {
            syncFinishInbound(lot.getOrderId(), "成品检验批判定：" + lot.getLotNo());
        }
        return lot;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityLot reinspectLot(Long lotId) {
        // 2026-09-21（dev-20260921-030）：复检守卫 —— 已判定且无后继版本，行锁防并发双击重复建版
        QualityLot old = qualityLotService.lockLot(lotId);
        if (!QualityLotStatusEnum.JUDGED.getCode().equals(old.getStatus())) {
            throw new BusinessException("只有已判定的检验批可以复检（当前："
                    + QualityLotStatusEnum.labelOf(old.getStatus()) + "）");
        }
        if (!qualityLotService.isLatestVersion(lotId)) {
            throw new BusinessException("该批已有复检新版本，请对最新版本操作");
        }
        QualityLotCreateDTO dto = new QualityLotCreateDTO();
        dto.setLotType(old.getLotType());
        dto.setSourceType(old.getSourceType());
        dto.setSourceId(old.getSourceId());
        dto.setSourceItemId(old.getSourceItemId());
        dto.setOrderId(old.getOrderId());
        dto.setExecutionId(old.getExecutionId());
        dto.setMaterialId(old.getMaterialId());
        dto.setMaterialCode(old.getMaterialCode());
        dto.setMaterialName(old.getMaterialName());
        dto.setProductId(old.getProductId());
        dto.setProductCode(old.getProductCode());
        dto.setProductName(old.getProductName());
        dto.setBatchNo(old.getBatchNo());
        dto.setLotQuantity(old.getLotQuantity());
        dto.setSamplingPlanId(old.getSamplingPlanId());
        dto.setSampleQuantity(old.getSampleQuantity());
        dto.setAcceptNumber(old.getAcceptNumber());
        dto.setRejectNumber(old.getRejectNumber());
        dto.setParentLotId(old.getLotId());
        dto.setVersion((old.getVersion() == null ? 1 : old.getVersion()) + 1);
        dto.setRemark("复检（源自 " + old.getLotNo() + "）");
        List<QualityLotItemDTO> items = new ArrayList<>();
        for (QualityLotItem item : qualityLotService.listItems(lotId)) {
            QualityLotItemDTO copy = new QualityLotItemDTO();
            copy.setCheckItem(item.getCheckItem());
            copy.setStandard(item.getStandard());
            copy.setInspectionMethod(item.getInspectionMethod());
            copy.setEquipment(item.getEquipment());
            copy.setSortOrder(item.getSortOrder());
            items.add(copy);
        }
        dto.setItems(items);
        QualityLot created = qualityLotService.createLot(dto);
        log.info("复检已建新版本: 原批={} 新批={} version={}", old.getLotNo(), created.getLotNo(), created.getVersion());
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal syncFinishInbound(Long orderId, String reason) {
        if (orderId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal planned = null;
        try {
            planned = jdbcTemplate.queryForObject(
                    "SELECT planned_quantity FROM production_order WHERE order_id = ?", BigDecimal.class, orderId);
        } catch (Exception e) {
            log.warn("读取工单计划数量失败: orderId={} err={}", orderId, e.getMessage());
        }
        // 该工单有效成品检验批（无后继复检版本）合格累计 —— 唯一口径（dev-20260918-014）
        com.jjx.quality.dto.FqcCompletionSummary summary = qualityLotService.summarizeEffectiveFqc(orderId);
        BigDecimal target = nz(summary.getQualifiedTotal());
        if (planned != null && target.compareTo(planned) > 0) {
            throw new BusinessException("成品检验合格累计（" + target.toPlainString() + "）超过工单计划数量（"
                    + planned.toPlainString() + "），请检查检验批数量");
        }
        // 完工口径回写 production_order（幂等：= 有效批合格累计；替换旧 handleFqcPass 的累加写法，防重复累计）
        BigDecimal remain = BigDecimal.ZERO;
        if (planned != null) {
            remain = planned.subtract(target);
            if (remain.signum() < 0) {
                remain = BigDecimal.ZERO;
            }
        }
        try {
            jdbcTemplate.update(
                    "UPDATE production_order SET finished_quantity = ?, completed_quantity = ?, remaining_quantity = ? "
                            + "WHERE order_id = ?",
                    target, target, remain, orderId);
        } catch (Exception e) {
            log.warn("回写工单完工数量失败: orderId={} err={}", orderId, e.getMessage());
        }
        return inventoryInboundService.syncFinishInbound(orderId, null, target, reason);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
