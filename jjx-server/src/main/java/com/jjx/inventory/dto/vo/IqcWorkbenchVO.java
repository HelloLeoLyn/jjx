package com.jjx.inventory.dto.vo;

import com.jjx.inventory.domain.InventoryIqcBatch;
import com.jjx.inventory.domain.InventoryIqcDispositionOrder;
import com.jjx.inventory.domain.InventoryIqcQuarantine;
import lombok.Data;

import java.util.List;

/**
 * IQC 单页工作台只读聚合模型：来料批次 → 材料 → 检验批 → 处置。
 * 不承载写入逻辑，数量与状态仍来自现有事实表和服务口径。
 */
@Data
public class IqcWorkbenchVO {
    private InboundVO inbound;
    private List<InventoryIqcQuarantine> quarantines;
    private List<InventoryIqcDispositionOrder> dispositions;
    private List<InventoryIqcBatch> batches;
}
