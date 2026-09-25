package com.jjx.inventory.dto.query;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** IQC 隔离处置台账分页查询条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IqcQuarantineLedgerQueryDTO extends PageQuery {
    private String status;
    private String materialKeyword;
    private String batchNo;
    private String inboundNo;
    private String sourceNo;
    private String supplierName;
}
