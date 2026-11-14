package com.jjx.product.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 整套送审/审核的逐产品结果（dev-20261114-011）。
 *
 * <p>失败按产品返回；成功的产品已完成整套操作，不因其他产品失败而回滚。
 */
@Data
public class BomReviewBatchResultVO {

    private String action;
    private List<ProductResult> successes = new ArrayList<>();
    private List<ProductFailure> failures = new ArrayList<>();
    /** 同一共享方案只处理一次；列出被复用的方案及涉及产品。 */
    private List<SharedNode> sharedNodes = new ArrayList<>();

    @Data
    public static class ProductResult {
        private Long rootBomId;
        private String rootBomVersion;
        private String productCode;
        private String productName;
        private String message;
        private List<NodeResult> nodes = new ArrayList<>();
    }

    @Data
    public static class NodeResult {
        private Long bomId;
        private String bomCode;
        private String bomVersion;
        /** SUBMIT / APPROVE / REUSE / REUSE_PENDING。 */
        private String action;
        private Integer statusBefore;
        private Integer statusAfter;
    }

    @Data
    public static class ProductFailure {
        private Long rootBomId;
        private String productCode;
        private String productName;
        private String reason;
    }

    @Data
    public static class SharedNode {
        private Long bomId;
        private String bomCode;
        private String bomVersion;
        private String action;
        private List<String> products = new ArrayList<>();
        private String message;
    }
}
