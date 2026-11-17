package com.jjx.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 独立加工处置事实，只新增；正常交货事实仍只在原入库明细。 */
@Data
@TableName("purchase_processing_disposition")
public class PurchaseProcessingDisposition {
    @TableId(type=IdType.AUTO) private Long dispositionId;
    private Long orderId;
    private Long itemId;
    private String receiptRequestKey;
    private String requestMarker;
    private Long operatorId;
    private String operatorName;
    private Long inboundId;
    private String receiptJson;
    private LocalDateTime createTime;
}
