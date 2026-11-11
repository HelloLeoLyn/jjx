package com.jjx.production.service.impl;

import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.model.ExactQuantity;
import com.jjx.production.enums.ProductionOrderStatusEnum;
import com.jjx.production.mapper.ManufacturingSupplySnapshotMapper;
import com.jjx.production.mapper.ManufacturingSupplySnapshotMapper.MaterialProductionSupplyRow;
import com.jjx.production.mapper.ManufacturingSupplySnapshotMapper.PurchaseSupplyRow;
import com.jjx.production.service.ManufacturingSupplySnapshotProvider;
import com.jjx.product.enums.ProductEnums;
import com.jjx.purchase.domain.enums.ReceiptStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 服务端供给读取实现（dev-20261111-006）。
 *
 * <p>库存口径复用既有规则：available = inventory_stock.total_quantity − total_reserved；
 * 材料级再减 inventory_material.safe_stock，与 InventoryAlertServiceImpl 现行口径一致。
 * 本实现只读，不调用 applyDelta，不改余额/预留/流水。</p>
 *
 * <p>供给口径（第二步修复）：</p>
 * <ol>
 *   <li>采购“确定供应”只认 approval_status=已批准；草稿/待审批进 plannedPurchaseSupply，不抵扣；
 *       已驳回/已取消在 SQL 层排除，不沿用既有 (1,3,4) 的历史口径。</li>
 *   <li>部分收货按行扣减：剩余 = max(quantity − received_quantity, 0)，超收行给 warning 且不倒挂。</li>
 *   <li>采购行单位与库存单位不可比且无可核实换算依据时，排除该行并给 warning（列出物料/订单/数量）。</li>
 *   <li>生产供给只认已批准工单 + 已批准且在有效期内的绑定 BOM，产出量取 planned − finished（合格口径）。</li>
 *   <li>卷类物料禁用档案长度自动换算；逐卷实际长度/余量属 013，缺依据即排除并指向 013。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ManufacturingSupplySnapshotProviderImpl implements ManufacturingSupplySnapshotProvider {

    /** 卷类计量单位：档案宽/长是定尺，不是逐卷实际余长（013 负责逐卷）。 */
    private static final Set<String> COIL_UNITS = Set.of("卷", "ROLL", "COIL", "ROLLS");

    private final ManufacturingSupplySnapshotMapper mapper;

    @Override
    public SupplySnapshot loadMaterialSupply(Long materialId, Long warehouseId, LocalDate demandDate) {
        ManufacturingSupplySnapshotMapper.MaterialRow material = mapper.selectMaterial(materialId);
        if (material == null) {
            throw new BusinessException("需求物料[" + materialId + "]在物料档案中不存在，无法计算净需求");
        }
        if (!StatusEnum.NORMAL.getCode().equals(material.getStatus())) {
            throw new BusinessException("需求物料[" + material.getMaterialCode() + "]状态非正常，已阻断需求计算");
        }
        if (isBlank(material.getUnit())) {
            throw new BusinessException("需求物料[" + material.getMaterialCode() + "]库存档案单位为空，无法计算净需求");
        }

        ManufacturingSupplySnapshotMapper.StockRow stock = warehouseId == null
                ? mapper.selectMaterialStock(materialId)
                : mapper.selectWarehouseStock(materialId, warehouseId, StatusEnum.NORMAL.getCode());
        ExactQuantity onHand = ExactQuantity.fromDecimal(nullToZero(stock == null ? null : stock.getTotalQuantity()));
        ExactQuantity reserved = ExactQuantity.fromDecimal(nullToZero(stock == null ? null : stock.getTotalReserved()));
        ExactQuantity safeStock = ExactQuantity.fromDecimal(nullToZero(material.getSafeStock()));

        SupplySnapshot snapshot = new SupplySnapshot();
        snapshot.setMaterialId(material.getMaterialId());
        snapshot.setMaterialCode(material.getMaterialCode());
        snapshot.setMaterialName(material.getMaterialName());
        snapshot.setUnit(material.getUnit());
        snapshot.setOnHand(onHand);
        snapshot.setReserved(reserved);
        snapshot.setSafeStock(safeStock);

        if (onHand.isNegative() || reserved.isNegative()) {
            snapshot.getWarnings().add("物料[" + material.getMaterialCode() + "]库存汇总出现负数，可用量按0处理；需核查库存身份/流水");
        }
        ExactQuantity unreserved = onHand.subtract(reserved);
        if (unreserved.isNegative()) {
            unreserved = ExactQuantity.ZERO;
        }
        ExactQuantity availableForDemand;
        if (warehouseId == null) {
            availableForDemand = unreserved.subtract(safeStock);
            if (availableForDemand.isNegative()) {
                availableForDemand = ExactQuantity.ZERO;
            }
            snapshot.setSafeStockDeducted(safeStock.isPositive());
        } else {
            // 安全库存是材料级缓冲，现有库存模型没有仓库分摊依据；仓库维度不硬扣，避免每个仓库重复扣一次。
            availableForDemand = unreserved;
            snapshot.setSafeStockDeducted(false);
            snapshot.getWarnings().add("仓库维度试算未分摊材料级安全库存[" + safeStock.approximateText()
                    + "]，按该仓库可用量计算；材料级保护口径需后续统一分配规则");
        }
        snapshot.setAvailableForDemand(availableForDemand);
        snapshot.setMaterialProductionAdapterApplied(usesMaterialOutputAdapter(materialId));

        loadProductionSupply(material, demandDate, snapshot);
        loadPurchaseSupply(material, demandDate, snapshot);
        return snapshot;
    }

    // ==================== 生产供给 ====================

    private void loadProductionSupply(ManufacturingSupplySnapshotMapper.MaterialRow material,
                                      LocalDate demandDate, SupplySnapshot snapshot) {
        List<MaterialProductionSupplyRow> rows = mapper.selectMaterialProductionRows(material.getMaterialId(),
                demandDate,
                ProductionOrderStatusEnum.PLANNED.getValue(),
                ProductionOrderStatusEnum.PENDING_START.getValue(),
                ProductionOrderStatusEnum.IN_PROGRESS.getValue(),
                ApproveStatusEnum.APPROVED.getValue());
        ExactQuantity total = ExactQuantity.ZERO;
        for (MaterialProductionSupplyRow row : rows) {
            String label = productionLabel(row);
            if (!ProductEnums.BomStatus.APPROVED.getValue().equals(row.getBomApproveStatus())) {
                snapshot.getWarnings().add(label + " 绑定的制造BOM[" + text(row.getBomCode(), row.getBomId())
                        + "]未批准，产出未计入生产供给");
                continue;
            }
            if (row.getBomEffectiveDate() != null && row.getBomEffectiveDate().isAfter(demandDate)) {
                snapshot.getWarnings().add(label + " 绑定的制造BOM[" + text(row.getBomCode(), row.getBomId())
                        + "]生效日期[" + row.getBomEffectiveDate() + "]晚于需求日期[" + demandDate + "]，产出未计入");
                continue;
            }
            if (row.getBomExpiryDate() != null && row.getBomExpiryDate().isBefore(demandDate)) {
                snapshot.getWarnings().add(label + " 绑定的制造BOM[" + text(row.getBomCode(), row.getBomId())
                        + "]已过失效日期[" + row.getBomExpiryDate() + "]，产出未计入");
                continue;
            }
            if (row.getBomEffectiveDate() == null) {
                snapshot.getWarnings().add(label + " 绑定的制造BOM[" + text(row.getBomCode(), row.getBomId())
                        + "]未设置生效日期，按历史兼容计入（需核查版本）");
            }
            if (row.getBomExpiryDate() == null) {
                snapshot.getWarnings().add(label + " 绑定的制造BOM[" + text(row.getBomCode(), row.getBomId())
                        + "]未设置失效日期，按历史兼容计入（需核查版本）");
            }
            // 尚未实现产出 = planned − finished：finished_quantity 是完工检验合格累计，也是入库口径
            // （ProductionOrder#finishedQuantity、QualityFinishServiceImpl 回写、InventoryInboundServiceImpl
            // 2854 行“入库数量=完工检验合格数，非工序汇总 completedQuantity”）。已入库部分必须在供给中扣除，
            // 否则与现货重复；remaining_quantity 下达后不随完工递减，不能用。
            ExactQuantity plannedQty = ExactQuantity.fromDecimal(nullToZero(row.getPlannedQuantity()));
            ExactQuantity finishedQty = ExactQuantity.fromDecimal(nullToZero(row.getFinishedQuantity()));
            ExactQuantity supply = plannedQty.subtract(finishedQty);
            if (!supply.isPositive()) {
                continue;
            }
            ExactQuantity factor = lineUnitFactor(row.getProductUnit(), material, label, snapshot,
                    "计划产出[" + plannedQty.approximateText() + "]");
            if (factor == null) {
                continue;
            }
            total = total.add(supply.multiply(factor));
        }
        snapshot.setProductionSupply(total);
    }

    // ==================== 采购供给 ====================

    private void loadPurchaseSupply(ManufacturingSupplySnapshotMapper.MaterialRow material,
                                    LocalDate demandDate, SupplySnapshot snapshot) {
        List<PurchaseSupplyRow> rows = mapper.selectPurchaseRows(material.getMaterialId(), demandDate,
                ReceiptStatusEnum.PENDING.getValue(),
                ReceiptStatusEnum.PARTIALLY_RECEIVED.getValue(),
                ApproveStatusEnum.APPROVED.getValue(),
                ApproveStatusEnum.DRAFT.getValue(),
                ApproveStatusEnum.PENDING.getValue());
        ExactQuantity confirmed = ExactQuantity.ZERO;
        ExactQuantity planned = ExactQuantity.ZERO;
        for (PurchaseSupplyRow row : rows) {
            String label = purchaseLabel(row, material);
            ExactQuantity ordered = ExactQuantity.fromDecimal(nullToZero(row.getQuantity()));
            ExactQuantity received = ExactQuantity.fromDecimal(nullToZero(row.getReceivedQuantity()));
            ExactQuantity remaining = ordered.subtract(received);
            if (remaining.isNegative()) {
                snapshot.getWarnings().add(label + " 已收[" + received.approximateText() + "]超过订购["
                        + ordered.approximateText() + "]，该行按0计并需核查收货记录；负数不冲抵其他行");
                continue;
            }
            if (remaining.isZero()) {
                continue;
            }
            ExactQuantity factor = lineUnitFactor(row.getUnit(), material, label, snapshot,
                    "未收数量[" + remaining.approximateText() + "]");
            if (factor == null) {
                continue;
            }
            ExactQuantity converted = remaining.multiply(factor);
            if (ApproveStatusEnum.APPROVED.getValue().equals(row.getApprovalStatus())) {
                confirmed = confirmed.add(converted);
            } else {
                planned = planned.add(converted);
            }
        }
        snapshot.setPurchaseSupply(confirmed);
        snapshot.setPlannedPurchaseSupply(planned);
    }

    /**
     * 单行单位折算。返回 null 表示该行排除（已写 warning）。只有“同单位”或“物料档案 unit_alt/unit_conv
     * 明确对应”两种依据；卷类一律不走档案尺寸换算（013 逐卷）。
     */
    private ExactQuantity lineUnitFactor(String lineUnit, ManufacturingSupplySnapshotMapper.MaterialRow material,
                                         String label, SupplySnapshot snapshot, String qtyText) {
        String stockUnit = material.getUnit();
        if (isBlank(lineUnit)) {
            snapshot.getWarnings().add(label + " " + qtyText + " 单位为空，无法与库存单位[" + stockUnit
                    + "]比较，该行未计入供给（UNIT_CONVERSION_MISSING）");
            return null;
        }
        if (normalizeUnit(lineUnit).equals(normalizeUnit(stockUnit))) {
            return ExactQuantity.ONE;
        }
        if (isCoilUnit(lineUnit) || isCoilUnit(stockUnit)) {
            snapshot.getWarnings().add(label + " " + qtyText + " 单位[" + lineUnit + "]与库存单位[" + stockUnit
                    + "]属卷类计量，档案长宽是定尺而非逐卷实际余长，未作换算也未计入供给；"
                    + "逐卷实际长度/余量与预留由 013 负责（UNIT_CONVERSION_MISSING）");
            return null;
        }
        boolean altMatches = !isBlank(material.getUnitAlt())
                && normalizeUnit(material.getUnitAlt()).equals(normalizeUnit(lineUnit))
                && material.getUnitConv() != null
                && material.getUnitConv().signum() > 0;
        if (altMatches) {
            snapshot.getWarnings().add(label + " " + qtyText + " 按物料档案换算单位["
                    + material.getUnitAlt() + "→" + stockUnit + "]=unit_conv "
                    + material.getUnitConv().stripTrailingZeros().toPlainString() + " 折算为库存单位");
            return ExactQuantity.fromDecimal(material.getUnitConv());
        }
        snapshot.getWarnings().add(label + " " + qtyText + " 单位[" + lineUnit + "]与库存单位[" + stockUnit
                + "]不一致且无可核实换算依据（UNIT_CONVERSION_MISSING），该行未计入供给");
        return null;
    }

    private boolean usesMaterialOutputAdapter(Long materialId) {
        Integer count = mapper.selectMaterialOutputBomCount(materialId);
        return count != null && count > 0;
    }

    private String purchaseLabel(PurchaseSupplyRow row, ManufacturingSupplySnapshotMapper.MaterialRow material) {
        return "采购行[订单" + text(row.getOrderNo(), row.getOrderId()) + " 行" + row.getItemId()
                + " 物料" + material.getMaterialCode() + "]";
    }

    private String productionLabel(MaterialProductionSupplyRow row) {
        return "生产工单[" + text(row.getOrderNo(), row.getOrderId()) + "]";
    }

    private String text(String code, Long id) {
        return isBlank(code) ? (id == null ? "-" : "ID:" + id) : code;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isCoilUnit(String unit) {
        return unit != null && COIL_UNITS.contains(unit.trim().toUpperCase(Locale.ROOT));
    }

    private static String normalizeUnit(String unit) {
        if (unit == null) {
            return "";
        }
        String normalized = unit.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "㎡", "平方米", "平方" -> "M2";
            case "米" -> "M";
            case "公斤" -> "KG";
            default -> normalized;
        };
    }
}
