package com.jjx.inventory.dto.save;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 入库来料检验提交参数。检验员信息由当前登录用户写入。 */
@Data
public class InboundInspectionSubmitDTO {
    private String inspectionRemark;
    private List<Item> items;

    @Data
    public static class Item {
        private Long itemId;
        /** 当前表无抽检数量列，仅用于校验，不落库。 */
        private BigDecimal sampledQuantity;
        private String inspectionResult;
        private String disposition;
        private BigDecimal qualifiedQuantity;
        private BigDecimal rejectedQuantity;
        private BigDecimal acceptedQuantity;
        private String rejectReason;
        private List<com.jjx.production.domain.dto.InspectionItemDTO> inspectionItems;
    }
}
