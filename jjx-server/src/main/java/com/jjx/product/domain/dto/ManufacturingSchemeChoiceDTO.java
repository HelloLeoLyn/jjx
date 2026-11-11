package com.jjx.product.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
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

    /** 被选择的制造方案 engineering_bom.bom_id；取消主方案（cancelPrimary=true）时可为空。 */
    private Long bomId;

    /** 是否本产品该物料的主方案；true=设/替换该物料主方案，false/null=仅父BOM实际采用，不改主方案。 */
    private Boolean isPrimary;

    /** 显式取消该物料主方案；true 时只移除本物料映射，bomId 可为空。 */
    private Boolean cancelPrimary;

    /** 设主/普通采用必须选择制造方案；显式取消不需 bomId。BeanValidation 与 service 双重兜底。 */
    @AssertTrue(message = "制造方案不能为空")
    @JsonIgnore
    public boolean isBomIdPresentWhenRequired() {
        return Boolean.TRUE.equals(cancelPrimary) || bomId != null;
    }

    // ===== 只读回显字段（保存时忽略） =====
    private String bomCode;
    private String bomVersion;
    private Integer approveStatus;
    private String warning;
}
