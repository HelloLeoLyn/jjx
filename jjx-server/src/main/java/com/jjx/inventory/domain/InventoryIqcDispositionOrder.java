package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_iqc_disposition_order")
public class InventoryIqcDispositionOrder {
    @TableId(type = IdType.AUTO) private Long dispositionId;
    private String dispositionNo;
    private Long quarantineId;
    private Long inboundId;
    private Long inboundItemId;
    private Long inspectionId;
    private String action;
    private BigDecimal quantity;
    private String materialCode;
    private String materialName;
    private String batchNo;
    private String remark;
    private String status;
    private Long operatorId;
    private String operatorName;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
