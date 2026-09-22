package com.jjx.inventory.dto.vo;

import com.jjx.common.core.page.PageResult;
import com.jjx.inventory.domain.InventoryIqcBatch;
import com.jjx.inventory.domain.InventoryIqcDispositionOrder;
import com.jjx.inventory.domain.InventoryIqcReworkOrder;
import lombok.Data;

import java.util.List;

/** IQC 隔离处置页一次请求所需的数据。 */
@Data
public class IqcQuarantineLedgerPageVO {
    private PageResult<IqcQuarantineLedgerRowVO> page;
    private List<InventoryIqcDispositionOrder> dispositionOrders;
    private List<InventoryIqcReworkOrder> reworkOrders;
    private List<InventoryIqcBatch> batches;
}
