package com.jjx.production.service;

import com.jjx.production.domain.model.ExactQuantity;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端可信供给快照（dev-20261111-006）。
 *
 * <p>preview 请求只提交需求条件（产品/BOM/数量/日期/仓库），库存、采购在途、工单产出都由本接口
 * 从数据库读取；客户端不能提交或冒充余额。所有数量以 inventory_material.unit 为口径。</p>
 */
public interface ManufacturingSupplySnapshotProvider {

    SupplySnapshot loadMaterialSupply(Long materialId, Long warehouseId, LocalDate demandDate);

    @Data
    class SupplySnapshot {
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String unit;
        private ExactQuantity onHand = ExactQuantity.ZERO;
        private ExactQuantity reserved = ExactQuantity.ZERO;
        private ExactQuantity safeStock = ExactQuantity.ZERO;
        private ExactQuantity availableForDemand = ExactQuantity.ZERO;
        private ExactQuantity productionSupply = ExactQuantity.ZERO;
        private ExactQuantity purchaseSupply = ExactQuantity.ZERO;
        private boolean safeStockDeducted;
        private List<String> warnings = new ArrayList<>();
    }
}
