package com.jjx.inventory.dto.query;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** IQC 隔离处置台账分页查询条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IqcQuarantineLedgerQueryDTO extends PageQuery {
    /** 只看「待处置」＝剩余可处置量 &gt; 0（隔离单状态列已删除，唯一状态来源是剩余量，dev-20260929-004）。 */
    private Boolean pendingOnly;
    /** 只看「已处置」＝剩余可处置量 = 0。 */
    private Boolean settledOnly;
    private String materialKeyword;
    private String batchNo;
    private String inboundNo;
    private String sourceNo;
    private String supplierName;
}
