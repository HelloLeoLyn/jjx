package com.jjx.inventory.dto.query;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 物料查询参数DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialQueryDTO extends PageQuery{

    private Long materialId;

    private String keyword;

    private String materialCode;

    private String materialName;

    private String materialType;

    /**
     * @deprecated 单值标签筛选，已由 tagIds + tagMatchMode 取代（dev-20260912-007）
     */
    @Deprecated
    private Long tagId;

    /** 标签ID集合（多选，与 tagMatchMode 配合，dev-20260912-007） */
    private java.util.List<Long> tagIds;

    /** 标签匹配模式：AND=同时含全部（默认）；OR=含任一 */
    private String tagMatchMode;

    private Long categoryId;

    private String specification;

    private String status;

    private Boolean batchControl;

    private Long supplierId;

    private Long defaultWarehouseId;

    private Boolean lowStock;

    private Boolean expiring;

    private String createTimeStart;

    private String createTimeEnd;

    private String orderBy;

    private String orderDirection;
}
