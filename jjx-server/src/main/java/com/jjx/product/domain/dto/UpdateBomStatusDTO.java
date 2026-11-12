package com.jjx.product.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBomStatusDTO {
    @NotNull(message = "bomId 不能为空")
    private Long bomId;
    private Integer current;
    private Integer target;
    private String remark;

    /**
     * 审批/驳回时由详情带回的服务端内容指纹（dev-20261111-018）。
     * 内容在审核期间被保存改动时返回 409，要求重新加载，不批准旧内容。
     */
    private String expectedContentRevision;
}
