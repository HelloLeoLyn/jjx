package com.jjx.inventory.dto.vo;

import com.jjx.inventory.domain.InventoryIqcBatch;
import com.jjx.inventory.domain.InventoryIqcDispositionOrder;
import com.jjx.quality.domain.entity.QualityLot;
import lombok.Data;

import java.util.List;

/**
 * IQC 单页工作台只读聚合模型：来料批次 → 材料 → 检验批 → 处置。
 * 不承载写入逻辑，数量与状态仍来自现有事实表和服务口径。
 */
@Data
public class IqcWorkbenchVO {
    private InboundVO inbound;
    /** 隔离品（待处置/已处置）：带上下文行模型 —— 来料批次 / 供应商 / 采购单号 / 检验批号 / 不合格原因。 */
    private List<IqcQuarantineLedgerRowVO> quarantines;
    private List<InventoryIqcDispositionOrder> dispositions;
    private List<InventoryIqcBatch> batches;
    /**
     * 该批次材料行关联的检验批（含一行 previous_inspection_id 上溯的原批）。
     * 目的：材料行的检验结论/复核状态/数量口径由服务端一次带全，页面不再逐行请求（去 N+1）。
     */
    private List<QualityLot> lots;
}
