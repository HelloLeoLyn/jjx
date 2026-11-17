package com.jjx.purchase.domain.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** 分切/切片/余卷的采购行快照，以及与计划分离的本次实收。 */
@Data
public class PurchaseProcessingPlan {
    /** 新方案按树保存；groups是后台生成的终端交货投影，旧方案仍直接使用groups。 */
    private Node tree;
    private List<Group> groups;
    @Data public static class Node {
        private String nodeId;
        private String role;
        private String unit;
        private BigDecimal widthMm;
        private BigDecimal lengthMm;
        private BigDecimal quantity;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String bindingMessage;
        private String remark;
        private com.jjx.purchase.domain.enums.PurchaseProcessingDelivery delivery;
        private String disposalReason;
        private Operation operation;
        private List<Node> children;
    }
    @Data public static class Operation {
        private com.jjx.purchase.domain.enums.PurchaseProcessingOperation type;
        private Integer useQuantity;
        private List<Slit> slits;
        private BigDecimal pieceWidthMm;
        private BigDecimal pieceLengthMm;
        private Boolean rotated;
        /** NULL按每个来源单位切满；填写时按每卷/每块的片数解释。 */
        private Integer piecesPerUnit;
    }
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
        private String nodePath;
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
        private com.jjx.purchase.domain.enums.PurchaseProcessingDelivery delivery;
        private String disposalReason;
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
