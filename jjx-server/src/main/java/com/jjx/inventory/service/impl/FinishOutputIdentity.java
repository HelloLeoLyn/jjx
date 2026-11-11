package com.jjx.inventory.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.enums.InventoryItemTypeEnum;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.production.domain.entity.ProductionOrder;

/**
 * 完工入库产出身份解析（dev-20261111-008）。
 *
 * <p>PRODUCT 工单按产品库存身份入库；MATERIAL 工单（半成品）按材料库存身份入库。
 * 缺产出对象/材料不存在时阻断，绝不静默降级为 PRODUCT。</p>
 */
public final class FinishOutputIdentity {

    private FinishOutputIdentity() {
    }

    public record Resolved(InventoryItemTypeEnum itemType, Long sourceId, String code, String name, String unit) {
    }

    public static Resolved resolve(ProductionOrder order, InventoryMaterialMapper materialMapper) {
        if (order == null) {
            throw new BusinessException("工单不存在，无法完工入库");
        }
        String outputType = order.getOutputType() == null ? "PRODUCT" : order.getOutputType().trim().toUpperCase();
        if ("MATERIAL".equals(outputType)) {
            Long outputId = order.getOutputId();
            if (outputId == null) {
                throw new BusinessException("半成品工单[" + order.getOrderNo() + "]缺少产出对象，无法完工入库");
            }
            InventoryMaterial material = materialMapper.selectById(outputId);
            if (material == null) {
                throw new BusinessException("半成品工单[" + order.getOrderNo()
                        + "]的产出材料[" + outputId + "]不存在，无法完工入库");
            }
            String unit = material.getUnit() == null || material.getUnit().isBlank() ? "PCS" : material.getUnit();
            return new Resolved(InventoryItemTypeEnum.MATERIAL, outputId,
                    material.getMaterialCode(), material.getMaterialName(), unit);
        }
        if (!"PRODUCT".equals(outputType)) {
            throw new BusinessException("未知产出身份[" + outputType + "]，无法完工入库");
        }
        return new Resolved(InventoryItemTypeEnum.PRODUCT, order.getProductId(),
                order.getProductCode(), order.getProductName(), "PCS");
    }
}
