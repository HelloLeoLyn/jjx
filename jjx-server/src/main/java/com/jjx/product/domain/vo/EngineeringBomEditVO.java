package com.jjx.product.domain.vo;

import lombok.Data;

/**
 * BOM 编辑结果 VO：承载变更明细（供 @Log detail 取值）
 */
@Data
public class EngineeringBomEditVO {
    private boolean success;
    private String detailMessage;
    /** 修改完成后 BOM 在库中的真实状态（供 @Log bizStatus 取值），对应 ProductEnums.BomStatus */
    private String bizStatus;
}
