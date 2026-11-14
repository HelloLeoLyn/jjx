package com.jjx.product.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 产品侧整套送审/审核请求（dev-20261114-011）。
 *
 * <p>前端必须回传预览时看到的具体方案范围（BOM id、版本、状态、内容指纹），
 * 后端在执行时重新发现并逐项比对；不接受仅传 productId 后由服务端另选版本。
 * 范围之外的方案不得顺带审核。
 */
@Data
public class BomReviewPackageRequest {

    /** SUBMIT=整套送审，APPROVE=整套通过，REJECT=整套驳回（只退回根产品BOM）。 */
    @NotBlank(message = "整套动作不能为空")
    @Pattern(regexp = "SUBMIT|APPROVE|REJECT", message = "整套动作只允许 SUBMIT/APPROVE/REJECT")
    private String action;

    @NotEmpty(message = "请选择至少一个产品BOM")
    @Valid
    private List<ProductScope> products;

    /** 整批审核意见；为空时按动作使用默认可读说明。 */
    private String remark;

    @Data
    public static class ProductScope {

        @NotNull(message = "产品BOM不能为空")
        private Long rootBomId;

        /** 预览时展示并确认的根方案版本。 */
        @NotBlank(message = "缺少产品BOM版本")
        private String expectedVersion;

        /** 预览时展示并确认的整套方案节点；执行时集合必须与之完全一致。 */
        @NotEmpty(message = "缺少整套方案范围")
        @Valid
        private List<NodeScope> expectedNodes;
    }

    @Data
    public static class NodeScope {

        @NotNull(message = "方案ID不能为空")
        private Long bomId;

        @NotBlank(message = "方案版本不能为空")
        private String version;

        @NotNull(message = "方案状态不能为空")
        private Integer status;

        /** 仅含业务内容的指纹（不含审核状态），用于识别预览后内容被改动。 */
        @NotBlank(message = "方案内容指纹不能为空")
        private String packageRevision;
    }
}
