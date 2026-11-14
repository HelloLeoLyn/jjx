package com.jjx.product.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 产品侧整套审核范围与明细（dev-20261114-011）。
 *
 * <p>范围只沿工程BOM行实际 sub_bom_id 递归展开：共享节点去重、防环，
 * 不按主方案 JSON、候选清单或固定层数扩大/截断。
 */
@Data
public class BomReviewPackageVO {

    private Long rootBomId;
    private String rootBomCode;
    private String rootBomVersion;
    private Integer rootStatus;
    private String outputType;
    private Long outputId;
    private Long productId;
    private String productCode;
    private String productName;
    private String specification;

    /** 本次整套需要送审/审核的方案数量（不含已批准复用）。 */
    private int actionCount;
    /** 已批准且未变化、只读复用的方案数量。 */
    private int reuseCount;
    /** 已处于审核中、本次复用并关联的方案数量。 */
    private int pendingReuseCount;

    private boolean executable;
    private String reason;
    private List<Issue> blockingIssues = new ArrayList<>();
    private List<Node> nodes = new ArrayList<>();

    @Data
    public static class Node {
        private Long bomId;
        private String bomCode;
        private String bomName;
        private String bomVersion;
        private Integer status;
        private String outputType;
        private Long outputId;
        private String outputCode;
        private String outputName;
        /** 仅业务内容指纹；审核状态变化不影响该值。 */
        private String packageRevision;
        /** 完整内容指纹（含审核状态），供单方案旧入口继续使用。 */
        private String contentRevision;
        /** true = 已批准且未变化，本次不重复审核、不改审核人/时间。 */
        private boolean reuse;
        /** true = 已有待审状态，本次复用并关联，不重复提交。 */
        private boolean pendingReuse;
        /** SUBMIT / APPROVE / REUSE / REUSE_PENDING / BLOCKED。 */
        private String plannedAction;
        private int itemCount;
        private int pendingItemCount;
        private int approvedItemCount;
        private List<Item> items = new ArrayList<>();
        private List<BomCheckIssueVO> issues = new ArrayList<>();
    }

    @Data
    public static class Item {
        private Long itemId;
        private Long parentMaterialId;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String specification;
        private String unit;
        private BigDecimal quantity;
        private Integer reviewStatus;
        private Boolean removeConfirmed;
        private Long subBomId;
        private String subBomLabel;
    }

    @Data
    public static class Issue {
        private Long bomId;
        private Long itemId;
        private String field;
        private String message;
    }

    /** 材料BOM被驳回/审批前的影响范围：引用它的上层方案（含产品根方案）。 */
    @Data
    public static class ReferenceImpact {
        private Long bomId;
        private String bomCode;
        private String bomVersion;
        private Integer status;
        private String outputType;
        private Long outputId;
        private String outputCode;
        private String outputName;
        private boolean root;
    }
}
