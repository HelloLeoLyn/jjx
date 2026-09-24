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
    /** dev-20260924-004：不良数量 N 与检验项目不合格合计 Σ 的联动校验 */
    private final com.jjx.quality.service.QualityNcrPieceService qualityNcrPieceService;

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
        // dev-20260924-004（方案 §5）：不良数量 N 与「检验项目不合格合计 Σ」联动校验 ——
        //   Σ>0 且 N>Σ → 拒绝（不允许无因不良：还有 K 件没归因，先补录检验项目的不合格数）
        //   Σ>0 且 N<Σ → 需勾选确认 + 填说明（留痕），否则拒绝
        //   Σ=0（检验单未录项目级不合格数）→ 放行，件级按「其他」兜底归因（不留白）
        BigDecimal defectSum = qualityNcrPieceService.sumLotItemDefects(lotId);
        if (defectSum == null) {
            defectSum = BigDecimal.ZERO;
        }
        if (fail.signum() > 0 && defectSum.signum() > 0) {
            if (fail.compareTo(defectSum) > 0) {
                throw new BusinessException("不良数量 " + fail.stripTrailingZeros().toPlainString()
                        + " 大于检验项目不合格合计 " + defectSum.stripTrailingZeros().toPlainString()
                        + "：还有 " + fail.subtract(defectSum).stripTrailingZeros().toPlainString()
                        + " 件未归因 —— 请先在「检验录入」补录检验项目的不合格数（CR/MA/MI）后再判定");
            }
            boolean partial = fail.compareTo(defectSum) < 0;
            boolean confirmed = Boolean.TRUE.equals(dto.getPartialConfirmed())
                    && dto.getPartialReason() != null && !dto.getPartialReason().isBlank();
            if (partial && !confirmed) {
                throw new BusinessException("不良数量 " + fail.stripTrailingZeros().toPlainString()
                        + " 小于检验项目不合格合计 " + defectSum.stripTrailingZeros().toPlainString()
                        + "：如确属调整后结论，请勾选「确认」并填写说明（留痕）");
            }
        }
        if (lot.getParentLotId() != null && inspected.compareTo(nz(lot.getLotQuantity())) != 0) {
            throw new BusinessException("复检必须覆盖整个检验批，检验数量应为 "
                    + nz(lot.getLotQuantity()).stripTrailingZeros().toPlainString());
        }
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
            if (nz(lot.getPassQuantity()).signum() > 0) {
                inventoryInboundService.createFromProduction(lot.getOrderId(), lot.getLotId(), lot.getPassQuantity());
            }
            // dev-20260923-033：返工复检批判定 → 返工处置收口（否则返工永远停在"执行中"，回收没有终点）
            closeReworkActionIfRecovered(lot);
        }
        return lot;
    }

    /**
     * dev-20260923-033：返工是唯一回收通道 —— 返工复检批判定后把对应处置动作置 DONE 并把台账收口。
     *
     * <p>口径：判定即代表返工已完工并复检合格（回收量 = 本批合格量，实物走既有「确认入库」入良品）；
     * 幂等：只处理 PROCESSING 的 REWORK 动作，重复判定不会重复收口。</p>
     */
    private void closeReworkActionIfRecovered(com.jjx.quality.domain.entity.QualityLot lot) {
        if (lot.getExecutionId() == null) {
            return;
        }
        try {
            Long actionId = jdbcTemplate.queryForObject(
                    "SELECT action_id FROM quality_ncr_action WHERE rework_execution_id = ? "
                            + "AND action_type = 'REWORK' AND status = 'PROCESSING' ORDER BY action_id DESC LIMIT 1",
                    Long.class, lot.getExecutionId());
            if (actionId == null) {
                return;
            }
            String pass = nz(lot.getPassQuantity()).stripTrailingZeros().toPlainString();
            String total = nz(lot.getLotQuantity()).stripTrailingZeros().toPlainString();
            String note = "返工复检合格 " + pass + "/" + total + " 件，已回收（判定批 " + lot.getLotNo() + "）";
            jdbcTemplate.update(
                    "UPDATE quality_ncr_action SET status = 'DONE', reinspection_lot_id = ?, "
                            + "result_remark = CONCAT(IFNULL(result_remark,''), ?) WHERE action_id = ?",
                    lot.getLotId(), " ｜ " + note, actionId);
            jdbcTemplate.update(
                    "UPDATE quality_ncr n JOIN quality_ncr_action a ON a.ncr_id = n.ncr_id "
                            + "SET n.status = 'CLOSED', n.remark = CONCAT(IFNULL(n.remark,''), ?) "
                            + "WHERE a.action_id = ? AND IFNULL(n.disposed_quantity,0) >= IFNULL(n.defect_quantity,0) "
                            + "AND n.status <> 'CLOSED'",
                    " ｜ " + note, actionId);
            // dev-20260922-018：件级「已回收」接线 —— 返工复检合格量对应的件置 RECOVERED
            // （口径：件级「已回收」数 = 本批复检合格量；未回收的件留在「返工中」，可继续返工/改报废）
            int passQty = nz(lot.getPassQuantity()).setScale(0, java.math.RoundingMode.DOWN).intValue();
            if (passQty > 0) {
                int recovered = jdbcTemplate.update(
                        "UPDATE quality_ncr_piece SET status = 'RECOVERED', update_time = NOW() "
                                + "WHERE action_id = ? AND status = 'REWORKING' AND del_flag = 0 "
                                + "ORDER BY seq_no LIMIT " + passQty,
                        actionId);
                if (recovered > 0) {
                    log.info("返工件级已回收: actionId={} 件数={}（复检合格量={}）", actionId, recovered, passQty);
                }
            }
            log.info("返工回收收口: lotNo={} actionId={} {}", lot.getLotNo(), actionId, note);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // 不是返工复检批（或已收口）→ 正常情况，忽略
        } catch (Exception e) {
            log.warn("返工回收收口失败（不影响判定结果）: lotNo={} err={}", lot.getLotNo(), e.getMessage());
        }
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
        // dev-20260923-039：守卫同源（复检）—— 与列表下发的 allowedActions 同一判据
        qualityLotService.assertAction(old, com.jjx.common.enums.AllowedActionEnum.LOT_REINSPECT);
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
        if ("FQC".equals(old.getLotType()) && old.getOrderId() != null) {
            // dev-20260923（022 收尾）：复检换代 → 原批那张入库单按口径处理（未过账作废 / 已过账红冲单待确认）。
            // 不再用「工单级 target=0 冲销」——那会把整张工单级单冲掉，与按批单叠加导致重复入库。
            inventoryInboundService.handleSupersededLotInbound(old.getLotId(), "复检换代：" + old.getLotNo());
        }
        // dev-20260923-022（看板 2205）：被取代批上未完成的不良单随批作废（VOID），禁止再处置
        try {
            qualityNcrService.voidOpenDispositionsBySupersededLot(old.getLotId(), "复检换代：" + old.getLotNo());
        } catch (Exception e) {
            log.warn("随批作废不良单失败（不阻断复检）: lotNo={} err={}", old.getLotNo(), e.getMessage());
        }
        QualityLot created = qualityLotService.createLot(dto);
        log.info("复检已建新版本: 原批={} 新批={} version={}", old.getLotNo(), created.getLotNo(), created.getVersion());
        // dev-20260923-022（看板 2230 / dev-20260922-018）：换代改变了「有效批集合」→ 重算工单有效合格累计，
        // 否则工单完工数会停留在旧值（现象：工单显示 200，有效合格只剩 100）。
        if (old.getOrderId() != null) {
            try {
                syncFinishInbound(old.getOrderId(), "复检换代重算：" + created.getLotNo());
            } catch (Exception e) {
                log.warn("复检换代后重算工单完工失败（不阻断复检）: orderId={} err={}", old.getOrderId(), e.getMessage());
            }
        }
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
        return target;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
