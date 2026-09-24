package com.jjx.quality.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.domain.entity.QualityNcrAction;
import com.jjx.quality.domain.entity.QualityScrapOrder;
import com.jjx.quality.mapper.QualityScrapOrderMapper;
import com.jjx.quality.service.QualityScrapOrderService;
import com.jjx.framework.common.RedisSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 成品报废单实现 —— dev-20260924-006（报废线三期）。
 *
 * <p>三条硬口径：</p>
 * <ol>
 *   <li>一笔报废处置只出一张单（uk_scrap_action 幂等；重复调用返回既有单）</li>
 *   <li>报废单只是**实物凭据**，库存不动（口径A：不良品从未进良品库）</li>
 *   <li>原样带上追溯信息：不良单号 / 检验批 / 工单 / 主缺陷（检验项目+分级）/ 件号区间</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityScrapOrderServiceImpl implements QualityScrapOrderService {

    private final QualityScrapOrderMapper scrapOrderMapper;
    private final RedisSequenceService redisSequenceService;
    private final JdbcTemplate jdbcTemplate;
    /** dev-20260924-010：损失成本核算（材料/工时分开，口径快照） */
    private final com.jjx.quality.service.QualityLossCostService lossCostService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createForAction(QualityNcrAction action, QualityNcr ncr) {
        if (action == null || action.getActionId() == null || ncr == null) {
            return null;
        }
        QualityScrapOrder existing = scrapOrderMapper.selectOne(Wrappers.<QualityScrapOrder>lambdaQuery()
                .eq(QualityScrapOrder::getActionId, action.getActionId())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing.getScrapId();
        }
        QualityScrapOrder order = new QualityScrapOrder();
        try {
            order.setScrapNo(redisSequenceService.generateBusinessNumberByType(
                    "quality_scrap", "SCR", "yyMMdd", 3));
        } catch (Exception e) {
            log.warn("报废单号生成失败，按 NCR 号兜底: actionId={} err={}", action.getActionId(), e.getMessage());
            order.setScrapNo("SCR-" + (ncr.getNcrNo() == null ? action.getActionId() : ncr.getNcrNo()));
        }
        order.setActionId(action.getActionId());
        order.setNcrId(ncr.getNcrId());
        order.setNcrNo(ncr.getNcrNo());
        order.setLotId(ncr.getLotId());
        order.setLotNo(lotNoOf(ncr.getLotId()));
        order.setOrderId(ncr.getOrderId());
        order.setOrderNo(orderNoOf(ncr.getOrderId()));
        order.setProductCode(ncr.getProductCode());
        order.setProductName(ncr.getProductName());
        order.setBatchNo(ncr.getBatchNo());
        order.setQuantity(action.getQuantity() == null ? BigDecimal.ZERO : action.getQuantity());
        // 原因口径：检验项目 + 分级（dev-20260924-004），文本说明作为补充
        order.setDefectItem(action.getMainCheckItem() == null ? ncr.getMainCheckItem() : action.getMainCheckItem());
        order.setDefectLevel(action.getMainDefectLevel() == null ? ncr.getMainDefectLevel() : action.getMainDefectLevel());
        order.setPieceRange(pieceRangeOf(action.getActionId()));
        order.setReason(ncr.getDefectReason());
        order.setStatus("DONE");
        order.setApplicant(action.getOperatorName());
        order.setApprover(action.getApprovedBy());
        order.setApproveTime(action.getApprovedTime());
        order.setRemark("报废处置单 #" + action.getActionId() + " 生成（dev-20260924-006）");
        // dev-20260924-010：损失金额（材料/工时分开 + 口径快照）；缺价/缺工价不静默，记入 loss_basis
        try {
            com.jjx.quality.service.QualityLossCostService.LossCost loss =
                    lossCostService.compute(ncr.getNcrId(), order.getQuantity());
            order.setLossMaterial(loss.materialLoss());
            order.setLossLabor(loss.laborLoss());
            order.setLossTotal(loss.total());
            order.setLossBasis(loss.basis());
        } catch (Exception e) {
            log.warn("报废单损失核算失败（单据照常出、金额留空）: ncrId={} err={}", ncr.getNcrId(), e.getMessage());
        }
        order.setDelFlag(0);
        try {
            scrapOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            QualityScrapOrder dup = scrapOrderMapper.selectOne(Wrappers.<QualityScrapOrder>lambdaQuery()
                    .eq(QualityScrapOrder::getActionId, action.getActionId()).last("LIMIT 1"));
            log.warn("报废单已存在（并发），复用: actionId={}", action.getActionId());
            return dup == null ? null : dup.getScrapId();
        }
        log.info("成品报废单已生成: scrapNo={} 数量={} 处置单={} 件号={}",
                order.getScrapNo(), order.getQuantity().toPlainString(), action.getActionId(), order.getPieceRange());
        // 台账报废金额累计（与单据同源）
        if (order.getLossTotal() != null) {
            jdbcTemplate.update(
                    "UPDATE quality_ncr SET scrapped_amount = IFNULL(scrapped_amount, 0) + ? WHERE ncr_id = ?",
                    order.getLossTotal(), ncr.getNcrId());
        }
        return order.getScrapId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int voidByAction(Long actionId) {
        if (actionId == null) {
            return 0;
        }
        QualityScrapOrder order = scrapOrderMapper.selectOne(Wrappers.<QualityScrapOrder>lambdaQuery()
                .eq(QualityScrapOrder::getActionId, actionId)
                .eq(QualityScrapOrder::getStatus, "DONE")
                .last("LIMIT 1"));
        if (order == null) {
            return 0;
        }
        order.setStatus("VOID");
        order.setRemark(appendRemark(order.getRemark(), "报废处置被撤销 → 单据作废（dev-20260924-006）"));
        scrapOrderMapper.updateById(order);
        // dev-20260924-010：撤销即回冲台账报废金额（单据不可改，金额随单据一起作废）
        if (order.getLossTotal() != null && order.getLossTotal().signum() > 0) {
            jdbcTemplate.update(
                    "UPDATE quality_ncr SET scrapped_amount = GREATEST(0, IFNULL(scrapped_amount, 0) - ?)"
                            + " WHERE ncr_id = ?",
                    order.getLossTotal(), order.getNcrId());
        }
        log.info("成品报废单已作废: scrapNo={} 处置单={}", order.getScrapNo(), actionId);
        return 1;
    }

    @Override
    public List<QualityScrapOrder> list(String keyword) {
        return scrapOrderMapper.selectList(Wrappers.<QualityScrapOrder>lambdaQuery()
                .and(org.apache.commons.lang3.StringUtils.isNotBlank(keyword),
                        w -> w.like(QualityScrapOrder::getScrapNo, keyword)
                                .or().like(QualityScrapOrder::getNcrNo, keyword)
                                .or().like(QualityScrapOrder::getOrderNo, keyword))
                .orderByDesc(QualityScrapOrder::getScrapId)
                .last("LIMIT 200"));
    }

    @Override
    public QualityScrapOrder detail(Long scrapId) {
        return scrapId == null ? null : scrapOrderMapper.selectById(scrapId);
    }

    // ==================== 内部 ====================

    /** 件号区间（件级追溯）：同一处置单挂的件，取最小~最大序号 */
    private String pieceRangeOf(Long actionId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT IF(CONCAT(MIN(seq_no)) = CONCAT(MAX(seq_no)), MIN(piece_no),"
                            + " CONCAT(MIN(piece_no), '~', SUBSTRING_INDEX(MAX(piece_no), '-D', -1)))"
                            + " FROM quality_ncr_piece WHERE action_id = ? AND del_flag = 0",
                    String.class, actionId);
        } catch (Exception e) {
            return null;
        }
    }

    private String lotNoOf(Long lotId) {
        if (lotId == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject("SELECT lot_no FROM quality_lot WHERE lot_id = ?", String.class, lotId);
        } catch (Exception e) {
            return null;
        }
    }

    private String orderNoOf(Long orderId) {
        if (orderId == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT order_no FROM production_order WHERE order_id = ?", String.class, orderId);
        } catch (Exception e) {
            return null;
        }
    }

    private String appendRemark(String origin, String add) {
        String base = origin == null ? "" : origin.trim();
        if (base.length() > 300) {
            base = base.substring(0, 300);
        }
        return base.isEmpty() ? add : base + " ｜ " + add;
    }
}
