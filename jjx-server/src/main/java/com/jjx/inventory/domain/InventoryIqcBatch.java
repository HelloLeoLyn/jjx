package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_iqc_batch")
public class InventoryIqcBatch {
    @TableId(type = IdType.AUTO)
    private Long batchId;
    private Long materialId;
    private String batchNo;
    private Long parentBatchId;
    private String parentBatchNo;
    private String rootBatchNo;
    private String batchType;
    private Long sourceReworkId;
    private Long sourceInboundItemId;
    private Long sourceInspectionId;
    private BigDecimal quantity;
    private BigDecimal processedQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal scrappedQuantity;
    private BigDecimal remainingQuantity;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
