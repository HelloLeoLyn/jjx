package com.jjx.inventory.dto.vo;

import com.jjx.inventory.domain.InventoryIqcDispositionOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** IQC 已处置历史行：补齐采购来源、入库单和供应商，保证单据链可见。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IqcDispositionLedgerRowVO extends InventoryIqcDispositionOrder {
    private String inboundNo;
    private String sourceNo;
    private Long supplierId;
    private String supplierName;
}
