package com.jjx.inventory.dto.query;

import lombok.Data;

/** IQC 采购收货单分页查询参数。 */
@Data
public class IqcPendingQueryDTO {

    private long pageNum = 1;
    private long pageSize = 10;
    private String inboundNo;
    /** inventory_inbound_order.order_status；为空时查询全部 IQC 流程状态。 */
    private Integer orderStatus;
    /** true 仅已提交检验，false 仅未提交，为空时不限制。 */
    private Boolean fillInspection;
}
