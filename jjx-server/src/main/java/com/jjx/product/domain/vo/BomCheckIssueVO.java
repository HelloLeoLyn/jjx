package com.jjx.product.domain.vo;

import lombok.Data;

/**
 * BOM 提交审核前的体检问题项（只读，供前端预检展示 / 后端提交前拦截）。
 * 2026-10-07 dev-20261007-006
 */
@Data
public class BomCheckIssueVO {

    /** 明细ID（问题所在行；主表级问题为 null） */
    private Long itemId;

    /** 物料编码（行定位用，便于用户照着改） */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 字段名（materialId / quantity / moduleQty …） */
    private String field;

    /** 问题描述（中文，可直接展示） */
    private String message;
}
