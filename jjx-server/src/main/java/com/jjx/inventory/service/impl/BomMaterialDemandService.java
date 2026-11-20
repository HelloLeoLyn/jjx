package com.jjx.inventory.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.service.BomVersionRules;
import com.jjx.production.domain.model.BomConsumptionCalculator;
import com.jjx.production.domain.model.ExactQuantity;
import com.jjx.production.service.BomConsumptionPreviewService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** 旧预警和材料预占的需求适配层；数学规则复用生产计算器，不写库存。 */
@RequiredArgsConstructor
public final class BomMaterialDemandService {
    private final InventoryMaterialMapper materials;
    private final EngineeringBomMapper boms;
    private final EngineeringBomItemMapper items;

    public Demand newDemand(boolean expandManufacturing) { return new Demand(expandManufacturing); }

    public final class Demand {
        private final boolean expand;
        private final Map<Long, ExactQuantity> totals = new LinkedHashMap<>();
        private final Map<Long, InventoryMaterial> archives = new LinkedHashMap<>();
        private final List<Map<String, Object>> issues = new ArrayList<>();
        private final Set<Long> invalid = new HashSet<>();

        private Demand(boolean expand) { this.expand = expand; }

        public void add(List<EngineeringBomItem> rows, BigDecimal quantity) {
            if (quantity == null || quantity.signum() < 0) throw new BusinessException("需求数量不能为负或为空");
            if (quantity.signum() == 0 || rows == null) return;
            addRows(rows, ExactQuantity.fromDecimal(quantity), new LinkedHashSet<>(), "BOM");
        }

        private void addRows(List<EngineeringBomItem> rows, ExactQuantity output, Set<Long> path, String prefix) {
            for (EngineeringBomItem row : rows) {
                if (row == null) continue;
                String location = prefix + "/明细[" + row.getItemId() + "]/物料[" + row.getMaterialCode() + "]";
                try {
                    InventoryMaterial archive = archive(row.getMaterialId(), location);
                    ExactQuantity amount = amount(row, archive, output, location);
                    if (expand && "make".equalsIgnoreCase(row.getSourceType()) && row.getSubBomId() != null) {
                        Long id = row.getSubBomId();
                        EngineeringBom sub = boms.selectById(id);
                        if (sub == null || !"MATERIAL".equals(sub.getOutputType())
                                || !Objects.equals(sub.getOutputId(), row.getMaterialId())
                                || !BomVersionRules.isEligible(sub.getApproveStatus(), sub.getEffectiveDate(),
                                    sub.getExpiryDate(), LocalDate.now(), ProductEnums.BomStatus.APPROVED.getValue()))
                            throw new BusinessException("下层生产BOM不存在、产出身份不符或未批准/已失效");
                        if (path.size() >= 5 || !path.add(id)) throw new BusinessException("下层生产BOM循环或超过五层展开边界");
                        try {
                            List<EngineeringBomItem> children = items.selectList(new LambdaQueryWrapper<EngineeringBomItem>()
                                    .eq(EngineeringBomItem::getBomId, id));
                            if (children == null || children.isEmpty()) throw new BusinessException("下层生产BOM没有投入明细");
                            addRows(children, amount, path, location + "/生产BOM[" + id + "]");
                        } finally { path.remove(id); }
                    } else if (!invalid.contains(row.getMaterialId())) {
                        totals.merge(row.getMaterialId(), amount, ExactQuantity::add);
                    }
                } catch (BusinessException ex) {
                    invalid.add(row.getMaterialId());
                    totals.remove(row.getMaterialId());
                    Map<String, Object> issue = new LinkedHashMap<>();
                    issue.put("materialId", row.getMaterialId()); issue.put("materialCode", row.getMaterialCode());
                    issue.put("materialName", row.getMaterialName()); issue.put("path", location);
                    issue.put("message", location + "：" + ex.getMessage()); issues.add(issue);
                }
            }
        }

        private InventoryMaterial archive(Long id, String location) {
            if (id == null) throw new BusinessException(location + "物料身份缺失");
            InventoryMaterial archive = archives.computeIfAbsent(id, materials::selectById);
            if (archive == null) throw new BusinessException("物料档案不存在，不能沿用旧ID推定单位");
            return archive;
        }

        private ExactQuantity amount(EngineeringBomItem row, InventoryMaterial archive,
                                     ExactQuantity output, String location) {
            ExactQuantity value;
            boolean raw = row.getBaseQty() != null || row.getModuleQty() != null || row.getPitchMm() != null
                    || (row.getPcsPerPitch() != null && row.getPcsPerPitch() != 1);
            if (raw) {
                value = BomConsumptionCalculator.lossApplied(output.multiply(BomConsumptionPreviewService.rawUnitQuantity(
                        row.getBaseQty(), row.getModuleQty(), row.getPitchMm(), row.getPcsPerPitch(),
                        row.getUnit(), archive.getWidthMm())), row.getLossRate());
            } else if (row.getQuantity() != null) {
                value = BomConsumptionCalculator.lossApplied(output.multiply(
                        BomConsumptionCalculator.theoretical(BigDecimal.ONE, null, null, row.getQuantity())), row.getLossRate());
            } else if (row.getActualIssueQty() != null && row.getActualIssueQty().signum() >= 0) {
                value = output.multiply(ExactQuantity.fromDecimal(row.getActualIssueQty()));
            } else throw new BusinessException("原始定额及历史用量均缺失，不能按0计算");
            ExactQuantity floor = ExactQuantity.fromDecimal(row.getMinIssueQty() == null ? BigDecimal.ZERO : row.getMinIssueQty());
            if (floor.signum() < 0) throw new BusinessException("最低投料不能为负");
            if (value.compareTo(floor) < 0) value = floor;
            // 跨单位只接受已保存且有依据的系数；不把卷料名义尺寸当每件耗用。
            var conversion = BomConsumptionCalculator.conversion(row.getUnit(), archive.getUnit(), row.getUnitFactor(),
                    row.getUnitFactorBasis(), row.getUnitFactorSource(), null, null, location);
            BomConsumptionCalculator.roundStock(ExactQuantity.ZERO, archive.getUnit()); // 校验取整策略
            return value.multiply(conversion.factor());
        }

        public void merge(Demand other) {
            archives.putAll(other.archives); issues.addAll(other.issues); invalid.addAll(other.invalid);
            other.totals.forEach((id, amount) -> totals.merge(id, amount, ExactQuantity::add));
            invalid.forEach(totals::remove);
        }

        public Map<Long, BigDecimal> rounded() {
            Map<Long, BigDecimal> result = new LinkedHashMap<>();
            totals.forEach((id, exact) -> result.put(id, BomConsumptionCalculator.roundStock(exact, archives.get(id).getUnit())));
            return result;
        }
        public List<Map<String, Object>> issues() { return List.copyOf(issues); }
        public String unit(Long id) { return archives.get(id).getUnit(); }
        public void requireComplete() {
            if (!issues.isEmpty()) throw new BusinessException("需求尚无法完整计算：" + issues.stream()
                    .map(i -> (String) i.get("message")).distinct().collect(java.util.stream.Collectors.joining("；")));
        }
    }
}
