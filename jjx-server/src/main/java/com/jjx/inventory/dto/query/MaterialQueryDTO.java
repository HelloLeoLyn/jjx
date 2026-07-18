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

    private String materialCode;

    private String materialName;

    private String materialType;

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
