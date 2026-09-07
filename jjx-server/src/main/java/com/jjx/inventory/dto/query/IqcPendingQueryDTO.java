package com.jjx.inventory.dto.query;

import lombok.Data;

/** IQC 待检采购收货单分页查询参数。 */
@Data
public class IqcPendingQueryDTO {

    private long pageNum = 1;
    private long pageSize = 10;
    private String inboundNo;
}
