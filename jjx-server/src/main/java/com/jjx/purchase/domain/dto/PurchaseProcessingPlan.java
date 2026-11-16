package com.jjx.purchase.domain.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** 分切/切片/余卷的采购行快照，以及与计划分离的本次实收。 */
@Data
public class PurchaseProcessingPlan {
    private List<Group> groups;
    @Data public static class Group {
        private String groupId;
        private String name;
        private Integer motherRolls;
        private List<Slit> slits;
        private List<Output> outputs;
    }
    @Data public static class Slit {
        private BigDecimal widthMm;
        private Integer rolls;
    }
    @Data public static class Output {
        private String outputId;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String materialSpec;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal widthMm;
        private BigDecimal lengthMm;
        private BigDecimal rollLengthM;
        private String remark;
    }
    @Data public static class ReceiptGroup {
        private String groupId;
        private BigDecimal lossArea;
        private String lossReason;
        private List<ReceiptOutput> outputs;
    }
    @Data public static class ReceiptOutput {
        private String outputId;
        private BigDecimal quantity;
        private BigDecimal rollLengthM;
    }
}
