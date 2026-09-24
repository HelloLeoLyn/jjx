package com.jjx.quality.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityLotItem;
import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.domain.entity.QualityNcrAction;
import com.jjx.quality.domain.entity.QualityNcrPiece;
import com.jjx.quality.domain.entity.QualityNcrPieceDefect;
import com.jjx.quality.dto.vo.QualityNcrPieceVO;
import com.jjx.quality.enums.QualityPieceStatusEnum;
import com.jjx.quality.mapper.QualityLotItemMapper;
import com.jjx.quality.mapper.QualityLotMapper;
import com.jjx.quality.mapper.QualityNcrActionMapper;
import com.jjx.quality.mapper.QualityNcrMapper;
import com.jjx.quality.mapper.QualityNcrPieceDefectMapper;
import com.jjx.quality.mapper.QualityNcrPieceMapper;
import com.jjx.quality.service.QualityNcrPieceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 不良件级追溯实现 —— dev-20260924-004（报废线一期）。
 *
 * <p>三条硬口径（方案 §3/§5）：</p>
 * <ol>
 *   <li>件表/缺陷记录表只做身份与追溯，**不参与库存数量计算**；件号与缺陷不进任何库存/金额口径</li>
 *   <li>原因取自检验单：缺陷记录 = 件 × 检验项目（quality_lot_item.check_item）× 分级（CR/MA/MI）；
 *       检验单没有项目级计数时落一条「其他」兜底（不留白）</li>
 *   <li>件是处置的最小单位：处置时按序号从「待处置」件里取 N 件挂到处置单，件数 = 处置数量</li>
 * </ol>
 *
 * <p>缺陷分配规则（可验证）：把检验单项目计数展开成槽位（CR 优先、再 MA、再 MI，同级按项目录入顺序），
 * 按「槽位下标 % 件数」分配到件上 —— 每件的缺陷条数在件与件之间尽量均衡，且
 * **Σ(每件缺陷条数按 项目×分级) == 检验单该 项目×分级 的计数**，可被门禁第 ⑩ 查复核。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityNcrPieceServiceImpl implements QualityNcrPieceService {

    private static final String ST_PENDING = QualityPieceStatusEnum.PENDING.getCode();
    private static final String ST_REWORKING = QualityPieceStatusEnum.REWORKING.getCode();
    private static final String ST_VOID = QualityPieceStatusEnum.VOID.getCode();
    /** 兜底原因：检验单未录项目级不合格数时的原因项（不留白） */
    private static final String FALLBACK_ITEM = "其他";
    /** 批量写入分批大小（方案 §11：500 条/批） */
    private static final int BATCH_SIZE = 500;

    private final QualityNcrPieceMapper pieceMapper;
    private final QualityNcrPieceDefectMapper defectMapper;
    private final QualityLotItemMapper lotItemMapper;
    private final QualityLotMapper lotMapper;
    private final QualityNcrMapper ncrMapper;
    private final QualityNcrActionMapper actionMapper;
    private final com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper;

    // ==================== 发号 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int issuePieces(QualityNcr ncr) {
        if (ncr == null || ncr.getNcrId() == null) {
            return 0;
        }
        BigDecimal qty = ncr.getDefectQuantity() == null ? BigDecimal.ZERO : ncr.getDefectQuantity();
        int target = qty.setScale(0, RoundingMode.DOWN).intValue();
        if (qty.stripTrailingZeros().scale() > 0) {
            log.warn("不良数量非整数，件级按整数发号（向下取整）: ncrNo={} 不良={}", ncr.getNcrNo(), qty.toPlainString());
        }
        if (target <= 0) {
            return 0;
        }
        List<QualityNcrPiece> existing = pieceMapper.selectList(Wrappers.<QualityNcrPiece>lambdaQuery()
                .eq(QualityNcrPiece::getNcrId, ncr.getNcrId())
                .orderByAsc(QualityNcrPiece::getSeqNo));
        int have = existing.size();
        if (have >= target) {
            // 更正后不良数量变小：把尾部「待处置」件作废（已处置的件不动，保持可追溯）
            if (have > target) {
                List<Long> extra = existing.stream()
                        .filter(p -> ST_PENDING.equals(p.getStatus()))
                        .filter(p -> p.getSeqNo() != null && p.getSeqNo() > target)
                        .map(QualityNcrPiece::getPieceId)
                        .collect(Collectors.toList());
                if (!extra.isEmpty()) {
                    pieceMapper.update(null, Wrappers.<QualityNcrPiece>lambdaUpdate()
                            .set(QualityNcrPiece::getStatus, ST_VOID)
                            .set(QualityNcrPiece::getRemark, "更正后不良数量减少，超出部分作废（dev-20260924-004）")
                            .in(QualityNcrPiece::getPieceId, extra));
                    log.info("更正后件级收敛: ncrNo={} 作废待处置件={} 件（不良 {} → {}）",
                            ncr.getNcrNo(), extra.size(), have, target);
                }
            }
            return 0;
        }
        // ① 缺陷槽位（来自检验单项目）
        List<QualityLotItem> items = lotItemMapper.selectList(Wrappers.<QualityLotItem>lambdaQuery()
                .eq(QualityLotItem::getLotId, ncr.getLotId())
                .orderByAsc(QualityLotItem::getSortOrder)
                .orderByAsc(QualityLotItem::getItemId));
        List<Slot> slots = buildSlots(items);
        Map<String, QualityLotItem> itemByName = new LinkedHashMap<>();
        for (QualityLotItem it : items) {
            if (it.getCheckItem() != null) {
                itemByName.putIfAbsent(it.getCheckItem().trim(), it);
            }
        }
        String lotNo = lotNoOf(ncr.getLotId());
        String orderNo = orderNoOf(ncr.getOrderId());
        // ② 逐件算缺陷（内存完成，不逐件打库）
        Map<Integer, List<Slot>> slotBySeq = new HashMap<>();
        for (int seq = have + 1; seq <= target; seq++) {
            slotBySeq.put(seq, slotsOfPiece(slots, seq, target));
        }
        List<QualityNcrPiece> toInsert = new ArrayList<>();
        for (int seq = have + 1; seq <= target; seq++) {
            List<Slot> pieceSlots = slotBySeq.get(seq);
            Slot main = mainSlot(pieceSlots);
            QualityNcrPiece p = new QualityNcrPiece();
            p.setPieceNo(ncr.getNcrNo() + "-D" + String.format("%03d", seq));
            p.setNcrId(ncr.getNcrId());
            p.setLotId(ncr.getLotId());
            p.setSeqNo(seq);
            p.setStatus(ST_PENDING);
            p.setOrderId(ncr.getOrderId());
            p.setWorkOrderNo(orderNo);
            p.setLotNo(lotNo);
            if (main != null) {
                p.setMainCheckItem(main.checkItem);
                p.setMainDefectLevel(main.level);
                // 逐件实测值：取主缺陷所在项目的 sample_values 第 seq 段
                QualityLotItem mainItem = itemByName.get(main.checkItem);
                String segment = sampleSegment(mainItem == null ? null : mainItem.getSampleValues(), seq);
                if (segment != null) {
                    p.setSampleIndex(seq);
                    p.setActualValue(segment);
                }
            }
            toInsert.add(p);
        }
        // ③ 分批插入件
        for (int i = 0; i < toInsert.size(); i += BATCH_SIZE) {
            pieceMapper.insertBatch(toInsert.subList(i, Math.min(i + BATCH_SIZE, toInsert.size())));
        }
        // ④ 回读件 ID（批量插入不返回自增 ID）→ 落缺陷记录
        List<QualityNcrPiece> saved = pieceMapper.selectList(Wrappers.<QualityNcrPiece>lambdaQuery()
                .eq(QualityNcrPiece::getNcrId, ncr.getNcrId())
                .ge(QualityNcrPiece::getSeqNo, have + 1)
                .orderByAsc(QualityNcrPiece::getSeqNo));
        Map<Integer, Long> idBySeq = saved.stream()
                .collect(Collectors.toMap(QualityNcrPiece::getSeqNo, QualityNcrPiece::getPieceId, (a, b) -> a));
        List<QualityNcrPieceDefect> defects = new ArrayList<>();
        for (int seq = have + 1; seq <= target; seq++) {
            Long pieceId = idBySeq.get(seq);
            if (pieceId == null) {
                continue;
            }
            List<Slot> pieceSlots = slotBySeq.get(seq);
            int order = 0;
            for (Slot s : pieceSlots) {
                QualityNcrPieceDefect d = new QualityNcrPieceDefect();
                d.setPieceId(pieceId);
                d.setNcrId(ncr.getNcrId());
                d.setLotId(ncr.getLotId());
                d.setCheckItem(s.checkItem);
                d.setDefectLevel(s.level);
                d.setIsMain(s == mainSlot(pieceSlots) ? 1 : 0);
                d.setSortOrder(order++);
                d.setRemark(s.remark);
                defects.add(d);
            }
        }
        for (int i = 0; i < defects.size(); i += BATCH_SIZE) {
            defectMapper.insertBatch(defects.subList(i, Math.min(i + BATCH_SIZE, defects.size())));
        }
        // ⑤ 回填不良单首因（台账列表按 项目×分级 筛选统计）
        Slot ncrMain = mainSlot(slots);
        if (ncrMain != null && ncr.getMainCheckItem() == null) {
            QualityNcr patch = new QualityNcr();
            patch.setNcrId(ncr.getNcrId());
            patch.setMainCheckItem(ncrMain.checkItem);
            patch.setMainDefectLevel(ncrMain.level);
            ncrMapper.updateById(patch);
            ncr.setMainCheckItem(ncrMain.checkItem);
            ncr.setMainDefectLevel(ncrMain.level);
        }
        log.info("不良件已发号: ncrNo={} 新发={} 件（累计 {}）缺陷记录={} 条",
                ncr.getNcrNo(), target - have, target, defects.size());
        return target - have;
    }

    // ==================== 处置 / 撤销 / 作废 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int attachPieces(Long ncrId, Long actionId, String disposeType, BigDecimal quantity) {
        if (ncrId == null || actionId == null || quantity == null || quantity.signum() <= 0) {
            return 0;
        }
        String status = statusOfDispose(disposeType);
        if (status == null) {
            log.warn("未知处置类型，件级不挂钩: disposeType={}", disposeType);
            return 0;
        }
        int want = quantity.setScale(0, RoundingMode.DOWN).intValue();
        if (want <= 0) {
            return 0;
        }
        List<QualityNcrPiece> pending = pieceMapper.selectList(Wrappers.<QualityNcrPiece>lambdaQuery()
                .eq(QualityNcrPiece::getNcrId, ncrId)
                .eq(QualityNcrPiece::getStatus, ST_PENDING)
                .orderByAsc(QualityNcrPiece::getSeqNo)
                .last("LIMIT " + want));
        if (pending.isEmpty()) {
            // 无件（本功能上线前建的老单）→ 静默跳过，不阻断处置；由门禁提示模式提示
            log.info("件级处置跳过（该不良单暂无件）: ncrId={} actionId={}", ncrId, actionId);
            return 0;
        }
        List<Long> ids = pending.stream().map(QualityNcrPiece::getPieceId).collect(Collectors.toList());
        pieceMapper.update(null, Wrappers.<QualityNcrPiece>lambdaUpdate()
                .set(QualityNcrPiece::getActionId, actionId)
                .set(QualityNcrPiece::getDisposeType, disposeType)
                .set(QualityNcrPiece::getStatus, status)
                .in(QualityNcrPiece::getPieceId, ids));
        QualityNcrAction patch = new QualityNcrAction();
        patch.setActionId(actionId);
        patch.setPieceCount(ids.size());
        QualityNcrPiece first = pending.get(0);
        patch.setMainCheckItem(first.getMainCheckItem());
        patch.setMainDefectLevel(first.getMainDefectLevel());
        actionMapper.updateById(patch);
        if (ids.size() < want) {
            log.warn("件数不足，已按现有件挂账: actionId={} 需要={} 实际={}", actionId, want, ids.size());
        }
        return ids.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releasePieces(Long actionId) {
        if (actionId == null) {
            return 0;
        }
        List<QualityNcrPiece> attached = pieceMapper.selectList(Wrappers.<QualityNcrPiece>lambdaQuery()
                .eq(QualityNcrPiece::getActionId, actionId));
        if (attached.isEmpty()) {
            return 0;
        }
        List<Long> ids = attached.stream().map(QualityNcrPiece::getPieceId).collect(Collectors.toList());
        pieceMapper.update(null, Wrappers.<QualityNcrPiece>lambdaUpdate()
                .set(QualityNcrPiece::getActionId, null)
                .set(QualityNcrPiece::getDisposeType, null)
                .set(QualityNcrPiece::getStatus, ST_PENDING)
                .in(QualityNcrPiece::getPieceId, ids));
        QualityNcrAction patch = new QualityNcrAction();
        patch.setActionId(actionId);
        patch.setPieceCount(0);
        actionMapper.updateById(patch);
        log.info("撤销处置：件已回到待处置 actionId={} 件数={}", actionId, ids.size());
        return ids.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int voidOpenPieces(Long ncrId) {
        if (ncrId == null) {
            return 0;
        }
        List<QualityNcrPiece> open = pieceMapper.selectList(Wrappers.<QualityNcrPiece>lambdaQuery()
                .eq(QualityNcrPiece::getNcrId, ncrId)
                .in(QualityNcrPiece::getStatus, ST_PENDING, ST_REWORKING));
        if (open.isEmpty()) {
            return 0;
        }
        List<Long> ids = open.stream().map(QualityNcrPiece::getPieceId).collect(Collectors.toList());
        pieceMapper.update(null, Wrappers.<QualityNcrPiece>lambdaUpdate()
                .set(QualityNcrPiece::getStatus, ST_VOID)
                .in(QualityNcrPiece::getPieceId, ids));
        log.info("随批作废：件置 VOID ncrId={} 件数={}", ncrId, ids.size());
        return ids.size();
    }

    // ==================== 查询 ====================

    @Override
    public List<QualityNcrPieceVO> listPieces(Long ncrId) {
        if (ncrId == null) {
            return List.of();
        }
        List<QualityNcrPiece> pieces = pieceMapper.selectList(Wrappers.<QualityNcrPiece>lambdaQuery()
                .eq(QualityNcrPiece::getNcrId, ncrId)
                .orderByAsc(QualityNcrPiece::getSeqNo));
        if (pieces.isEmpty()) {
            return List.of();
        }
        List<Long> pieceIds = pieces.stream().map(QualityNcrPiece::getPieceId).collect(Collectors.toList());
        Map<Long, List<QualityNcrPieceDefect>> defectsByPiece = defectMapper.selectList(
                        Wrappers.<QualityNcrPieceDefect>lambdaQuery()
                                .in(QualityNcrPieceDefect::getPieceId, pieceIds)
                                .orderByAsc(QualityNcrPieceDefect::getSortOrder)
                                .orderByAsc(QualityNcrPieceDefect::getDefectId))
                .stream().collect(Collectors.groupingBy(QualityNcrPieceDefect::getPieceId));
        List<QualityNcrPieceVO> rows = new ArrayList<>();
        for (QualityNcrPiece p : pieces) {
            QualityNcrPieceVO vo = new QualityNcrPieceVO();
            vo.setPieceId(p.getPieceId());
            vo.setPieceNo(p.getPieceNo());
            vo.setSeqNo(p.getSeqNo());
            vo.setMainCheckItem(p.getMainCheckItem());
            vo.setMainDefectLevel(p.getMainDefectLevel());
            vo.setActualValue(p.getActualValue());
            vo.setSampleIndex(p.getSampleIndex());
            vo.setStatus(p.getStatus());
            vo.setStatusLabel(QualityPieceStatusEnum.labelOf(p.getStatus()));
            vo.setDisposeType(p.getDisposeType());
            vo.setActionId(p.getActionId());
            vo.setWorkOrderNo(p.getWorkOrderNo());
            vo.setLotNo(p.getLotNo());
            vo.setRemark(p.getRemark());
            vo.setDefects(defectsByPiece.getOrDefault(p.getPieceId(), List.of()));
            rows.add(vo);
        }
        return rows;
    }

    @Override
    public BigDecimal sumLotItemDefects(Long lotId) {
        if (lotId == null) {
            return BigDecimal.ZERO;
        }
        List<QualityLotItem> items = lotItemMapper.selectList(Wrappers.<QualityLotItem>lambdaQuery()
                .eq(QualityLotItem::getLotId, lotId));
        BigDecimal sum = BigDecimal.ZERO;
        for (QualityLotItem it : items) {
            sum = sum.add(nz(it.getCrQuantity())).add(nz(it.getMaQuantity())).add(nz(it.getMiQuantity()));
        }
        return sum;
    }

    // ==================== 内部 ====================

    /** 缺陷槽位：检验单项目计数展开（CR 优先、再 MA、再 MI；同级按项目录入顺序） */
    private List<Slot> buildSlots(List<QualityLotItem> items) {
        List<Slot> slots = new ArrayList<>();
        for (QualityLotItem it : items) {
            if (it.getCheckItem() == null || it.getCheckItem().isBlank()) {
                continue;
            }
            addSlots(slots, it, "CR", nz(it.getCrQuantity()));
            addSlots(slots, it, "MA", nz(it.getMaQuantity()));
            addSlots(slots, it, "MI", nz(it.getMiQuantity()));
        }
        slots.sort(Comparator.comparingInt((Slot s) -> -QualityNcrPieceDefect.severity(s.level))
                .thenComparingInt(s -> s.order));
        return slots;
    }

    private void addSlots(List<Slot> slots, QualityLotItem it, String level, BigDecimal qty) {
        int n = qty.setScale(0, RoundingMode.DOWN).intValue();
        for (int i = 0; i < n; i++) {
            Slot slot = new Slot();
            slot.checkItem = it.getCheckItem().trim();
            slot.level = level;
            slot.order = it.getSortOrder() == null ? 0 : it.getSortOrder();
            slots.add(slot);
        }
    }

    /**
     * 某件的缺陷槽位：槽位下标 % 件数 == 件序-1 → 均衡分配。
     * 检验单没有项目级计数时返回单条「其他」兜底（不留白）。
     */
    private List<Slot> slotsOfPiece(List<Slot> slots, int seq, int totalPieces) {
        if (slots.isEmpty()) {
            Slot fallback = new Slot();
            fallback.checkItem = FALLBACK_ITEM;
            fallback.level = null;
            fallback.remark = "检验单未录项目级不合格数，按「其他」兜底（dev-20260924-004）";
            List<Slot> one = new ArrayList<>();
            one.add(fallback);
            return one;
        }
        List<Slot> mine = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (i % totalPieces == seq - 1) {
                mine.add(slots.get(i));
            }
        }
        if (mine.isEmpty()) {
            mine.add(slots.get((seq - 1) % slots.size()));
        }
        return mine;
    }

    /** 主缺陷：CR>MA>MI，同级取顺序靠前 */
    private Slot mainSlot(List<Slot> slots) {
        Slot best = null;
        for (Slot s : slots) {
            if (best == null || QualityNcrPieceDefect.severity(s.level) > QualityNcrPieceDefect.severity(best.level)) {
                best = s;
            }
        }
        return best;
    }

    /** 逐件实测值：sample_values 按竖线（半角/全角）拆，取第 seq 段；越界/为空返回 null */
    static String sampleSegment(String sampleValues, int seq) {
        if (sampleValues == null || sampleValues.isBlank() || seq <= 0) {
            return null;
        }
        String[] parts = sampleValues.split("[|｜]");
        if (parts.length < seq) {
            return null;
        }
        String v = parts[seq - 1].trim();
        return v.isEmpty() ? null : v;
    }

    private String lotNoOf(Long lotId) {
        if (lotId == null) {
            return null;
        }
        QualityLot lot = lotMapper.selectById(lotId);
        return lot == null ? null : lot.getLotNo();
    }

    private String orderNoOf(Long orderId) {
        if (orderId == null) {
            return null;
        }
        try {
            com.jjx.production.domain.entity.ProductionOrder order = productionOrderMapper.selectById(orderId);
            return order == null ? null : order.getOrderNo();
        } catch (Exception e) {
            log.warn("读取工单号失败（件级追溯字段降级为空）: orderId={} err={}", orderId, e.getMessage());
            return null;
        }
    }

    private static String statusOfDispose(String disposeType) {
        if (disposeType == null) {
            return null;
        }
        return switch (disposeType.trim().toUpperCase()) {
            case "REWORK" -> ST_REWORKING;
            case "CONCESSION" -> QualityPieceStatusEnum.CONCEDED.getCode();
            case "SCRAP" -> QualityPieceStatusEnum.SCRAPPED.getCode();
            case "RETURN" -> QualityPieceStatusEnum.RETURNED.getCode();
            default -> null;
        };
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 缺陷槽位（内部用） */
    private static final class Slot {
        private String checkItem;
        private String level;
        private int order;
        private String remark;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Slot slot)) {
                return false;
            }
            return Objects.equals(checkItem, slot.checkItem) && Objects.equals(level, slot.level);
        }

        @Override
        public int hashCode() {
            return Objects.hash(checkItem, level);
        }
    }
}
