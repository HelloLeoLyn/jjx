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
        QualityLot old = qualityLotService.getLot(lotId);
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
        // 该工单成品检验批的"有效版本"（无后继复检版本）合格数合计
        List<QualityLot> lots = lotMapper.selectList(new LambdaQueryWrapper<QualityLot>()
                .eq(QualityLot::getLotType, "FQC")
                .eq(QualityLot::getOrderId, orderId));
        BigDecimal target = BigDecimal.ZERO;
        for (QualityLot lot : lots) {
            Long childCount = lotMapper.selectCount(new LambdaQueryWrapper<QualityLot>()
                    .eq(QualityLot::getParentLotId, lot.getLotId()));
            if (childCount != null && childCount > 0) {
                continue; // 已有复检新版本 → 不计账
            }
            boolean judged = lot.getInspectedQuantity() != null && lot.getInspectedQuantity().signum() > 0;
            if (judged) {
                target = target.add(nz(lot.getPassQuantity()));
            }
        }
        if (planned != null && target.compareTo(planned) > 0) {
            throw new BusinessException("成品检验合格累计（" + target.toPlainString() + "）超过工单计划数量（"
                    + planned.toPlainString() + "），请检查检验批数量");
        }
        return inventoryInboundService.syncFinishInbound(orderId, null, target, reason);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
