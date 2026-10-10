package com.jjx.product.domain.dto;

import lombok.Data;

/**
 * BOM 物料全量同步请求项（dev-20261010-002）
 * 前端把「未关联」的导入行（名称 + 规格）一次性提交，后端批量回查物料库。
 */
@Data
public class BomMaterialMatchItemDTO {

    /** 行定位（前端回填用，等于请求数组下标） */
    private Integer index;

    /** 物料名称（Excel 品名） */
    private String name;

    /** 规格（Excel 规格） */
    private String spec;
}
