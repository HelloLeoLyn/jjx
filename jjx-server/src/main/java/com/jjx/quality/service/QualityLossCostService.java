package com.jjx.quality.service;

import java.math.BigDecimal;

/**
 * 损失（废品/返工）成本核算服务 —— dev-20260924-010（报废线五期）。
 *
 * <p>业务口径（与业内一致、分开列示、来源可追溯）：</p>
 * <ul>
 *   <li><b>材料损失</b> = 报废数量 × 单位材料标准成本；单位材料标准成本 = Σ(BOM 单耗 ×(1+损耗率) × 材料单价)</li>
 *   <li><b>工时损失</b> = 报废数量 × 单位工时标准成本；单位工时标准成本 = Σ(工序标准工时 × 该工序标准工价)</li>
 *   <li>材料单价**取值链**（逐级回退，来源写进口径快照）：①该批次入库单价 → ②库存批次成本 → ③最近采购价 → ④物料主数据(成本价/标准价) → ⑤无价（计 0 并列入「无价物料」）</li>
 *   <li>工价/工时缺失 → 工时损失计 0 并列入「工时缺失」（不静默、不瞎估）</li>
 *   <li>报废对象是**材料**（IQC 来料报废）时只算材料口径（用该材料自身批次价），不摊工时</li>
 * </ul>
 *
 * <p>金额是**单据快照**：单据不可改；报废被受控撤销时回冲。</p>
 */
public interface QualityLossCostService {

    /**
     * 计算某不良单本次报废的数量对应的损失。
     *
     * @param ncrId    不良单ID（提供 工单/产品/批次/批类型 上下文）
     * @param quantity 本次报废数量
     */
    LossCost compute(Long ncrId, BigDecimal quantity);

    /** 损失结果（金额 + 口径快照） */
    record LossCost(BigDecimal materialLoss, BigDecimal laborLoss, BigDecimal total, String basis) {
    }
}
