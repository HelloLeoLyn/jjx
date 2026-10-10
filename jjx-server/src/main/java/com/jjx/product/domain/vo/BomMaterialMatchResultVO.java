package com.jjx.product.domain.vo;

import lombok.Data;

/**
 * BOM 物料全量同步匹配结果（dev-20261010-002）
 * 匹配键：物料名称 + 规格（两侧归一化后精确相等）。
 */
@Data
public class BomMaterialMatchResultVO {

    /** 行定位（对应请求项 index） */
    private Integer index;

    /**
     * 匹配状态：
     * MATCHED   唯一命中（可自动回填）
     * AMBIGUOUS 命中多条同名同规格（不自动填，前端标黄提示手动选择）
     * NOT_FOUND 未匹配到
     */
    private String status;

    /** 命中的物料ID（仅 MATCHED 时有效） */
    private Long materialId;

    /** 命中的物料编码（仅 MATCHED 时有效） */
    private String materialCode;

    /** 命中的计量单位（仅 MATCHED 时有效） */
    private String unit;

    /** 候选数量（NOT_FOUND=0，MATCHED=1，AMBIGUOUS>1） */
    private Integer matchCount;
}
