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
 *
 * <p>口径界定（dev-20261111-006 第二步）：{@code purchaseSupply} 只含<b>已批准</b>采购单的未收数量；
 * 草稿/待审批数量单独放 {@code plannedPurchaseSupply}，只展示、不抵扣缺口。</p>
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
        /** 已批准工单的未入库产出（planned − finished），尚未分配。 */
        private ExactQuantity productionSupply = ExactQuantity.ZERO;
        /** 已批准采购单的未收数量（部分收货按行扣减）。 */
        private ExactQuantity purchaseSupply = ExactQuantity.ZERO;
        /** 草稿/待审批采购数量：未批准，只展示不抵扣。 */
        private ExactQuantity plannedPurchaseSupply = ExactQuantity.ZERO;
        private boolean safeStockDeducted;
        /** 仅当该物料确实经 BOM 产出身份（output_type=MATERIAL）兼容读适配取数时为 true。 */
        private boolean materialProductionAdapterApplied;
        private List<String> warnings = new ArrayList<>();
    }
}
