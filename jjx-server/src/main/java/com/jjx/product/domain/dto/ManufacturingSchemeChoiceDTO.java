package com.jjx.product.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 产品×投入物料的主方案选择（方案 A：product.material_scheme_json，dev-20261111-004）。
 * 只回填父 BOM 草稿；父 BOM 显式保存时在同一事务持久化。
 */
@Data
public class ManufacturingSchemeChoiceDTO {

    /** 投入物料 inventory_material.material_id。 */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 被选择的制造方案 engineering_bom.bom_id。 */
    @NotNull(message = "制造方案不能为空")
    private Long bomId;

    /** 是否本产品该物料的主方案。 */
    private Boolean isPrimary;

    // ===== 只读回显字段（保存时忽略） =====
    private String bomCode;
    private String bomVersion;
    private Integer approveStatus;
    private String warning;
}
