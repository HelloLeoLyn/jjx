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

    /** IQC 归一（dev-20260918-026）：关联 quality_lot 主键 */
    private Long lotId;
    private Long materialId;
    private Long supplierId;
    private String supplierName;
    /** 统一处置单的业务子编号，兼容历史类型单据展示。 */
    private String actionNo;
    private Long childBatchId;
    private String childBatchNo;
    private String reason;
    private Long applicantId;
    private String applicantName;
    private Long approverId;
    private String approverName;
    /** 统一处置事实关联的质量域动作，审批/重试必须复用该动作，禁止重复记账。 */
    private Long qualityActionId;
    private String action;
    private BigDecimal quantity;
    private String materialCode;
    private String materialName;
    private String batchNo;
    private Long iqcBatchId;
    private String remark;
    private String status;
    private Long operatorId;
    private String operatorName;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
