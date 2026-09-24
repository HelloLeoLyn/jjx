package com.jjx.quality.service.impl;

import com.jjx.quality.service.QualityLossCostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 损失成本核算实现 —— dev-20260924-010（报废线五期）。
 *
 * <p>只读聚合，不写任何业务表；金额与口径快照由调用方（报废单）落库。算法：
 * 逐条 BOM 行取「材料单价」（四级回退链）算出单位材料成本；逐道工序取「标准工时 × 标准工价」算出单位工时成本；
 * 缺价/缺工价**不静默**，计入口径快照。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityLossCostServiceImpl implements QualityLossCostService {

    private final JdbcTemplate jdbcTemplate;

    /** 材料单价来源（用于口径快照，业务上要能看出"这个数从哪来"） */
    enum PriceSource {
        BATCH_INBOUND("批次入库单价"),
        STOCK_COST("库存批次成本"),
        PURCHASE("最近采购价"),
        MATERIAL_MASTER("物料主数据价"),
        NONE("无价");

        private final String label;

        PriceSource(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /** 单价候选（纯数据，便于单测校验优先级） */
    record PriceCandidate(PriceSource source, BigDecimal price) {
    }

    @Override
    public LossCost compute(Long ncrId, BigDecimal quantity) {
        BigDecimal qty = quantity == null ? BigDecimal.ZERO : quantity;
        if (ncrId == null || qty.signum() <= 0) {
            return new LossCost(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "数量为 0，无损失");
        }
        // 不良单上下文
        Ctx ctx = loadContext(ncrId);
        if (ctx == null) {
            return new LossCost(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "不良单不存在");
        }
        List<String> notes = new ArrayList<>();
        BigDecimal unitMaterial;
        BigDecimal unitLabor = BigDecimal.ZERO;
        if ("IQC".equalsIgnoreCase(ctx.lotType)) {
            // 材料报废：按该材料自身批次价，直接、可精确（不摊 BOM、不摊工时）
            PriceCandidate p = priceOf(ctx.materialId, ctx.materialCode, ctx.batchNo);
            unitMaterial = p.price() == null ? BigDecimal.ZERO : p.price();
            if (p.price() == null) {
                notes.add("无价物料 1 项");
            } else {
                notes.add("材料单价 " + money(p.price()) + "（" + p.source().label() + "）");
            }
        } else {
            // 成品报废：单位标准成本 = Σ(BOM 单耗×(1+损耗率)×材料价) + Σ(工序标准工时×工价)
            List<BomLine> bom = loadBom(ctx.orderId);
            BigDecimal sum = BigDecimal.ZERO;
            int noPrice = 0;
            for (BomLine line : bom) {
                PriceCandidate p = priceOf(line.materialId, line.materialCode, null);
                if (p.price() == null) {
                    noPrice++;
                    continue;
                }
                BigDecimal per = line.quantity == null ? BigDecimal.ZERO : line.quantity;
                sum = sum.add(withLossRate(per, line.lossRate).multiply(p.price()));
            }
            unitMaterial = sum;
            if (bom.isEmpty()) {
                notes.add("该工单无 BOM，材料损失计 0");
            } else if (noPrice > 0) {
                notes.add("无价物料 " + noPrice + "/" + bom.size() + " 项（已按 0 计）");
            }
            LaborCost labor = loadLaborCost(ctx.orderId);
            unitLabor = labor.unitCost;
            if (labor.missingWage > 0) {
                notes.add("工时缺失 " + labor.missingWage + " 项（未配标准工价，工时损失计 0）");
            } else if (labor.unitHours.signum() > 0) {
                notes.add("标准工时 " + money(labor.unitHours) + "h/件 × 工价计 " + money(unitLabor) + " 元/件");
            }
        }
        BigDecimal materialLoss = qty.multiply(unitMaterial).setScale(2, RoundingMode.HALF_UP);
        BigDecimal laborLoss = qty.multiply(unitLabor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = materialLoss.add(laborLoss);
        String basis = "报废 " + money(qty) + " 件 × 单位成本(材料 " + money(unitMaterial)
                + " + 工时 " + money(unitLabor) + ") = " + money(total)
                + "；来源：" + (notes.isEmpty() ? "—" : String.join("；", notes))
                + "；口径：材料=Σ(BOM 单耗×(1+损耗率)×材料单价)，工时=Σ(工序标准工时×标准工价)";
        if (basis.length() > 500) {
            basis = basis.substring(0, 497) + "...";
        }
        return new LossCost(materialLoss, laborLoss, total, basis);
    }

    // ==================== 取价（四级回退链，纯数据可单测） ====================

    /** 按优先级挑价：批次入库价 → 库存批次成本 → 最近采购价 → 主数据价 → 无 */
    static PriceCandidate pickPrice(List<PriceCandidate> candidates) {
        for (PriceSource source : new PriceSource[]{
                PriceSource.BATCH_INBOUND, PriceSource.STOCK_COST,
                PriceSource.PURCHASE, PriceSource.MATERIAL_MASTER}) {
            for (PriceCandidate c : candidates) {
                if (c.source() == source && c.price() != null && c.price().signum() > 0) {
                    return c;
                }
            }
        }
        return new PriceCandidate(PriceSource.NONE, null);
    }

    private PriceCandidate priceOf(Long materialId, String materialCode, String batchNo) {
        List<PriceCandidate> candidates = new ArrayList<>();
        candidates.add(new PriceCandidate(PriceSource.BATCH_INBOUND,
                queryOne("SELECT ii.unit_price FROM inventory_inbound_item ii"
                        + " WHERE ii.unit_price > 0 AND (? IS NOT NULL AND ii.batch_no = ?)"
                        + "   AND (ii.material_id = ? OR ii.material_code = ?)"
                        + " ORDER BY ii.item_id DESC LIMIT 1", batchNo, batchNo, materialId, materialCode)));
        candidates.add(new PriceCandidate(PriceSource.STOCK_COST,
                queryOne("SELECT si.unit_cost FROM inventory_stock_item si"
                        + " WHERE si.unit_cost > 0 AND (si.material_id = ? OR si.material_code = ?)"
                        + " ORDER BY si.item_id DESC LIMIT 1", materialId, materialCode)));
        candidates.add(new PriceCandidate(PriceSource.PURCHASE,
                queryOne("SELECT pi.unit_price FROM purchase_order_item pi"
                        + " WHERE pi.unit_price > 0 AND (pi.material_id = ? OR pi.material_code = ?)"
                        + " ORDER BY pi.item_id DESC LIMIT 1", materialId, materialCode)));
        candidates.add(new PriceCandidate(PriceSource.MATERIAL_MASTER,
                queryOne("SELECT COALESCE(NULLIF(m.cost_price, 0), NULLIF(m.standard_price, 0))"
                        + " FROM inventory_material m WHERE (m.material_id = ? OR m.material_code = ?) LIMIT 1",
                        materialId, materialCode)));
        return pickPrice(candidates);
    }

    private BigDecimal queryOne(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 上下文 ====================

    private Ctx loadContext(Long ncrId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT n.ncr_id, n.order_id, n.lot_type, n.material_id, n.material_code, n.batch_no"
                            + " FROM quality_ncr n WHERE n.ncr_id = ?",
                    (rs, rowNum) -> {
                        Ctx c = new Ctx();
                        c.ncrId = rs.getLong("ncr_id");
                        long orderId = rs.getLong("order_id");
                        c.orderId = rs.wasNull() ? null : orderId;
                        c.lotType = rs.getString("lot_type");
                        long materialId = rs.getLong("material_id");
                        c.materialId = rs.wasNull() ? null : materialId;
                        c.materialCode = rs.getString("material_code");
                        c.batchNo = rs.getString("batch_no");
                        return c;
                    }, ncrId);
        } catch (Exception e) {
            log.warn("损失核算：读不良单上下文失败 ncrId={} err={}", ncrId, e.getMessage());
            return null;
        }
    }

    private List<BomLine> loadBom(Long orderId) {
        List<BomLine> lines = new ArrayList<>();
        if (orderId == null) {
            return lines;
        }
        try {
            jdbcTemplate.query(
                    "SELECT i.material_id, i.material_code, i.quantity, i.loss_rate"
                            + " FROM production_order o JOIN engineering_bom_item i ON i.bom_id = o.bom_id"
                            + " WHERE o.order_id = ?",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                        BomLine l = new BomLine();
                        long mid = rs.getLong("material_id");
                        l.materialId = rs.wasNull() ? null : mid;
                        l.materialCode = rs.getString("material_code");
                        l.quantity = rs.getBigDecimal("quantity");
                        l.lossRate = rs.getBigDecimal("loss_rate");
                        lines.add(l);
                    }, orderId);
        } catch (Exception e) {
            log.warn("损失核算：读 BOM 失败 orderId={} err={}", orderId, e.getMessage());
        }
        return lines;
    }

    /** 单位工时成本：逐工序 标准工时 × 标准工价（工价未配则计入缺失，不瞎估） */
    private LaborCost loadLaborCost(Long orderId) {
        LaborCost cost = new LaborCost();
        if (orderId == null) {
            return cost;
        }
        try {
            jdbcTemplate.query(
                    "SELECT r.custom_labor_hours, r.standard_wage, r.process_id,"
                            + " (SELECT p.standard_labor_hours FROM engineering_standard_process p"
                            + "   WHERE p.process_id = r.process_id LIMIT 1) AS std_hours"
                            + " FROM production_order o JOIN engineering_routing_item r ON r.routing_id = o.routing_id"
                            + " WHERE o.order_id = ?",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                        BigDecimal hours = rs.getBigDecimal("custom_labor_hours");
                        if (hours == null || hours.signum() <= 0) {
                            hours = rs.getBigDecimal("std_hours");
                        }
                        BigDecimal wage = rs.getBigDecimal("standard_wage");
                        if (hours == null || hours.signum() <= 0) {
                            return;
                        }
                        cost.unitHours = cost.unitHours.add(hours);
                        if (wage == null || wage.signum() <= 0) {
                            cost.missingWage++;
                            return;
                        }
                        cost.unitCost = cost.unitCost.add(hours.multiply(wage));
                    }, orderId);
        } catch (Exception e) {
            log.warn("损失核算：读工时/工价失败 orderId={} err={}", orderId, e.getMessage());
        }
        return cost;
    }

    private static String money(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    /**
     * 计算含损耗的用量 —— **口径与仓库既有实现一致**：`applied_qty = quantity × (1 + loss_rate/100)`
     * （见 EngineeringBomServiceImpl:680 注释、OrderMaterialReserveServiceImpl:266 / InventoryOutboundServiceImpl:1150 实现）。
     *
     * <p>注意：`engineering_bom_item.loss_rate` 存的是**百分数**（5 表示 5%），不是小数 —— 直接 ×(1+rate) 会把用量放大 100 倍。</p>
     */
    static BigDecimal withLossRate(BigDecimal quantity, BigDecimal lossRatePercent) {
        BigDecimal base = quantity == null ? BigDecimal.ZERO : quantity;
        BigDecimal rate = lossRatePercent == null ? BigDecimal.ZERO : lossRatePercent;
        return base.multiply(BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100))));
    }

    private static final class Ctx {
        private Long ncrId;
        private Long orderId;
        private String lotType;
        private Long materialId;
        private String materialCode;
        private String batchNo;
    }

    private static final class BomLine {
        private Long materialId;
        private String materialCode;
        private BigDecimal quantity;
        private BigDecimal lossRate;
    }

    private static final class LaborCost {
        private BigDecimal unitHours = BigDecimal.ZERO;
        private BigDecimal unitCost = BigDecimal.ZERO;
        private int missingWage = 0;
    }
}
