package com.jjx.inventory.dto.save;

import lombok.Data;

@Data
public class InboundInspectionReviewDTO {
    /** 当前检验批；为空时兼容历史请求，回退入库明细原始 lot_id。 */
    private Long lotId;
    private Long approverId;
    private String approverName;
    private String remark;
}
