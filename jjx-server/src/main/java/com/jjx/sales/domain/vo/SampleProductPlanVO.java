package com.jjx.sales.domain.vo;

import com.jjx.sales.domain.entity.SalesSampleProcess;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 打样工作台「复制打样方案 → 成品方案」只读带出结果（dev-20261012-012）。
 *
 * <p>从样品单产品的「当前已批准且现行」BOM + 工艺路线带出工序行（含材料），
 * 直接产出「打样工序行形状」（{@link SalesSampleProcess}）的临时列表，只读、不落库、不自动继承。</p>
 *
 * <p>eligible=false 时 {@link #reason} 说明不可带出原因，rows 为空。</p>
 */
@Data
public class SampleProductPlanVO {

    /** 是否可带出（产品已有已批准且现行的 BOM + 工艺路线） */
    private Boolean eligible;

    /** 不可带出原因（eligible=false 时有值） */
    private String reason;

    private Long productId;
    private String productCode;
    private String productName;

    private Long bomId;
    private String bomVersion;
    private Integer bomApproveStatus;

    private Long routingId;
    private String routingVersion;
    private Integer routingApproveStatus;

    /** 带出工序行数 */
    private Integer processCount;

    /** 带出材料条数 */
    private Integer materialCount;

    /** 未挂工序的 BOM 材料（不静默丢弃，前端提示/处理，dev-20261012-012） */
    private List<Map<String, Object>> unlinkedMaterials;

    /** 打样工序行（形状复用 SalesSampleProcess，仅前端编辑用，不落库） */
    private List<SalesSampleProcess> rows;
}
