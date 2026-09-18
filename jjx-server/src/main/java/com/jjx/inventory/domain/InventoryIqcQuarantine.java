package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_iqc_quarantine")
public class InventoryIqcQuarantine {
    @TableId(type = IdType.AUTO) private Long quarantineId;
    private Long inboundId;
    private Long inboundItemId;
    private Long inspectionId;

    /** IQC 归一（dev-20260918-026）：关联 quality_lot 主键 */
    private Long lotId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String batchNo;
    private Long iqcBatchId;
    private BigDecimal quantity;
    private BigDecimal remainingQuantity;
    private String disposition;
    private String status;
    private Long operatorId;
    private String operatorName;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
