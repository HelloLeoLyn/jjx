package com.jjx.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 标签查询辅助项（facets）：标签 + 当前条件下的关联数量（dev-20260912-004）
 */
@Data
@Schema(description = "标签查询辅助项（含计数）")
public class TagFacetVO {

    @Schema(description = "标签ID")
    private Long tagId;

    @Schema(description = "标签编码")
    private String tagCode;

    @Schema(description = "标签名称")
    private String tagName;

    @Schema(description = "标签分组（如 supplier_goods / material_attribute）")
    private String tagGroup;

    @Schema(description = "当前筛选条件下的关联数量")
    private Long count;

    @Schema(description = "是否已选中")
    private Boolean selected;
}
