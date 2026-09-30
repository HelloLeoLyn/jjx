package com.jjx.inventory.dto.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** 入库单关联的成品检验批及其单据，只读投影，不持久化派生数量。 */
@Data
public class InboundLotSummaryVO {
    private Long inboundId;
    private Long lotId;
    private String lotNo;
    private BigDecimal lotQuantity;
    private BigDecimal qualifiedQuantity;
    private BigDecimal rejectedQuantity;
    private List<InboundDocument> inboundDocuments;
    private List<ScrapDocument> scrapDocuments;

    @Data
    public static class InboundDocument {
        private Long lotId;
        private Long inboundId;
        private String inboundNo;
        private String inboundType;
        private Integer status;
        private BigDecimal quantity;
        private BigDecimal postedQuantity;
    }

    @Data
    public static class ScrapDocument {
        private Long lotId;
        private String scrapNo;
        private BigDecimal quantity;
        private String status;
    }
}
