package com.jjx.production.service.impl;

import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.model.ExactQuantity;
import com.jjx.production.enums.ProductionOrderStatusEnum;
import com.jjx.production.mapper.ManufacturingSupplySnapshotMapper;
import com.jjx.production.service.ManufacturingSupplySnapshotProvider;
import com.jjx.purchase.domain.enums.ReceiptStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 服务端供给读取实现（dev-20261111-006）。
 *
 * <p>库存口径复用既有规则：available = inventory_stock.total_quantity − total_reserved；
 * 材料级再减 inventory_material.safe_stock，与 InventoryAlertServiceImpl 现行口径一致。
 * 本实现只读，不调用 applyDelta，不改余额/预留/流水。</p>
 */
@Service
@RequiredArgsConstructor
public class ManufacturingSupplySnapshotProviderImpl implements ManufacturingSupplySnapshotProvider {

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

        BigDecimal production = mapper.selectMaterialProductionSupply(materialId, demandDate,
                ProductionOrderStatusEnum.PLANNED.getValue(),
                ProductionOrderStatusEnum.PENDING_START.getValue(),
                ProductionOrderStatusEnum.IN_PROGRESS.getValue());
        snapshot.setProductionSupply(ExactQuantity.fromDecimal(nullToZero(production)));

        BigDecimal purchase = mapper.selectPurchaseSupply(materialId, demandDate,
                ReceiptStatusEnum.PENDING.getValue(),
                ApproveStatusEnum.DRAFT.getValue(),
                ApproveStatusEnum.PENDING.getValue(),
                ApproveStatusEnum.APPROVED.getValue());
        snapshot.setPurchaseSupply(ExactQuantity.fromDecimal(nullToZero(purchase)));
        return snapshot;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
