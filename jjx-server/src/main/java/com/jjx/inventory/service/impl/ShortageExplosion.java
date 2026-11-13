package com.jjx.inventory.service.impl;

import com.jjx.engineering.domain.entity.EngineeringBomItem;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongPredicate;

/**
 * 缺料预警的多层展开（dev-20261113-009）。
 *
 * <p>规则：BOM 行 source_type=make 且 sub_bom_id 指向"当前有效且已批准"的制造 BOM 时，
 * 继续向下展开其组件；深度上限 5；同一 sub_bom 在同一条展开链上只走一次（防环）。
 * 数量一律用精确单价累加，由调用方在整单汇总后统一向上取整一次。</p>
 */
public final class ShortageExplosion {

    /** 单条材料的展开结果。 */
    public record Line(Long materialId, String materialCode, String materialName,
                       BigDecimal exactQty, boolean selfMade) {
    }

    private ShortageExplosion() {
    }

    /**
     * 递归展开。
     *
     * @param items     当前层 BOM 明细
     * @param multiplier 当前层需要生产的数量
     * @param depth     当前深度（0 起）
     * @param visited   已访问 subBomId（防环）
     * @param subLoader subBomId → 其明细
     * @param effective  subBomId 是否有效且已批准（复用 BomVersionRules 的资格口径）
     * @param out       materialId → 精确数量
     */
    public static void explode(List<EngineeringBomItem> items, BigDecimal multiplier, int depth,
                               Set<Long> visited, Function<Long, List<EngineeringBomItem>> subLoader,
                               LongPredicate effective, Map<Long, BigDecimal> out) {
        if (items == null || items.isEmpty() || multiplier == null || multiplier.signum() <= 0) {
            return;
        }
        for (EngineeringBomItem item : items) {
            if (item == null || item.getMaterialId() == null) {
                continue;
            }
            BigDecimal unitQty = item.getActualIssueQty() != null
                    ? item.getActualIssueQty()
                    : item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
            Long subBomId = item.getSubBomId();
            boolean make = "make".equalsIgnoreCase(item.getSourceType());
            if (make && subBomId != null && depth < 5 && effective.test(subBomId) && visited.add(subBomId)) {
                List<EngineeringBomItem> children = subLoader.apply(subBomId);
                explode(children, multiplier.multiply(unitQty), depth + 1, visited, subLoader, effective, out);
            } else {
                out.merge(item.getMaterialId(), unitQty.multiply(multiplier), BigDecimal::add);
            }
        }
    }

    /** 整单汇总后的一次性取整（向上，0 位小数）。 */
    public static Map<Long, BigDecimal> roundOnce(Map<Long, BigDecimal> exact) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : exact.entrySet()) {
            result.put(entry.getKey(), entry.getValue().setScale(0, java.math.RoundingMode.UP));
        }
        return result;
    }

    /** 展开用访问集合的工厂（便于测试）。 */
    public static Set<Long> newVisited() {
        return new HashSet<>();
    }
}
