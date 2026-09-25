package com.jjx.quality.dto;

import lombok.Data;

import java.util.List;

/**
 * 检验批查询入参 —— dev-20260917-001
 */
@Data
public class QualityLotQueryDTO {

    private Integer pageNum = 1;
    private Integer pageSize = 10;

    /** IQC/IPQC/FQC，空=全部 */
    private String lotType;
    /** PENDING/INSPECTING/JUDGED/CLOSED，空=全部 */
    private String status;
    private String lotNo;
    /** 报工单、生产工单、销售单、入库单或发货单号。 */
    private String businessNo;
    private Long orderId;
    private Long executionId;
    private Long materialId;
    private String materialCode;
    private Long productId;
    private String batchNo;

    /** 只看有不良待处置的批 */
    private Boolean hasPendingDefect;

    /** 附加检验项（详情用） */
    private List<Object> items;
}
