package com.jjx.inventory.dto.vo;

import com.jjx.inventory.domain.InventoryIqcQuarantine;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** IQC 隔离处置台账行，补充来源入库单与缺陷信息。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IqcQuarantineLedgerRowVO extends InventoryIqcQuarantine {
    private String inboundNo;
    private Long supplierId;
    private String supplierName;
    private String sourceNo;
    private String defectReason;
}
