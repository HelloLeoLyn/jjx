package com.jjx.sales.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 打样工作台「来源单据」查看摘要VO（任务1438 / dev-20260905-004）
 *
 * 背景：工程角色（engineering:sample:workbench）无 sales:quotation:view / sales:inquiry:view，
 * 复用销售详情弹窗会 403。此 VO 承载按样品单关联链收敛的只读摘要：
 * 仅返回打样参考所需字段，剔除价格/金额/联系人电话/销售负责人/审批信息等敏感数据。
 *
 * 链路：样品单.quotation_id → 报价单 → 询价单(converted_quotation_id)
 */
@Data
public class SampleSourceDocVO {

    /**
     * 来源报价单摘要（不含单价/金额等）
     */
    @Data
    public static class QuotationSummary {
        private Long quotationId;
        private String quotationNo;
        /** 报价类型：1=标准品，其他=样品（与销售页展示口径一致） */
        private Integer quotationType;
        private String customerName;
        private LocalDate quotationDate;
        private LocalDate validUntil;
        private Integer quotationStatus;
        /** 来源询价单号（经 converted_quotation_id 反查，可空） */
        private String sourceInquiryNo;
        private String remark;
        private List<QuotationItem> items;
    }

    /**
     * 来源报价单明细摘要（技术/规格字段全保留，单价/金额剔除）
     */
    @Data
    public static class QuotationItem {
        private String productCode;
        private String productName;
        private Integer keyCount;
        private BigDecimal width;
        private BigDecimal height;
        private BigDecimal thickness;
        private String materialType;
        private String color;
        private String circuitType;
        /** 编码流水号（DEV-1108 结构参数） */
        private String serialNo;
        /** 面板结构类型（DEV-1108） */
        private String panelType;
        /** 面板特征（DEV-1108） */
        private String panelFeature;
        /** 线路特征（DEV-1108） */
        private String circuitFeature;
        private String connectorType;
        private Integer quantity;
        private String unit;
        private Integer deliveryDays;
        private LocalDate estimatedDeliveryDate;
        /** 自定义要求 */
        private String customRequirements;
        /** Logo要求 */
        private String logoRequirement;
        /** 认证要求 */
        private String certificationRequirement;
    }

    /**
     * 来源询价单摘要（需求描述字段全保留，单价/联系人电话/销售负责人剔除）
     */
    @Data
    public static class InquirySummary {
        private Long inquiryId;
        private String inquiryNo;
        private String customerName;
        private String contactPerson;
        private LocalDate inquiryDate;
        private Integer expectedQuantity;
        private String productCode;
        private String productName;
        private Integer keyCount;
        private String sizeDescription;
        private String materialRequirements;
        private String circuitRequirements;
        private String connectorRequirements;
        private String specialRequirements;
        private String productDescription;
        /** 是否有图纸：0/1 */
        private Integer hasDrawing;
        /** 询价类型：2=样品，其他=标准（与销售页展示口径一致） */
        private Integer inquiryType;
        private Integer inquiryStatus;
        /** 需求时间窗口（起） */
        private LocalDate startDate;
        /** 需求时间窗口（止） */
        private LocalDate endDate;
        private String remark;
    }
}
