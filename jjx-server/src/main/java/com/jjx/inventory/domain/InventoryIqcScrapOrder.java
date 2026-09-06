package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_iqc_scrap_order")
public class InventoryIqcScrapOrder {
    @TableId(type = IdType.AUTO) private Long scrapId;
    private String scrapNo;
    private Long dispositionId;
    private Long quarantineId;
    private Long inboundId;
    private Long inboundItemId;
    private Long inspectionId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String batchNo;
    private BigDecimal quantity;
    private String reason;
    private String status;
    private Long applicantId;
    private String applicantName;
    private Long approverId;
    private String approverName;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
