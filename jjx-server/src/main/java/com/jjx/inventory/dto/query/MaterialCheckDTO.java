package com.jjx.inventory.dto.query;

import lombok.Data;

/**
 * 物料校验请求DTO
 * 用于根据名称、规格等条件校验物料是否存在
 */
@Data
public class MaterialCheckDTO {
    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String specification;

    /** 供应商名称 */
    private String supplierName;
}
