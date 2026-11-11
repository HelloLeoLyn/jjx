package com.jjx.product.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * BOM 编辑结果 VO：承载变更明细（供 @Log detail 取值）
 */
@Data
public class EngineeringBomEditVO {
    private boolean success;
    private String detailMessage;
    /** 修改完成后 BOM 在库中的真实状态（供 @Log bizStatus 取值），对应 ProductEnums.BomStatus */
    private String bizStatus;
    /** 父 BOM 保存后产品方案 JSON 的新 revision（未变更方案时为空）。 */
    private Integer schemeRevision;
    /** 草稿引用未批准下层方案等非阻断提示。 */
    private List<String> warnings;
}
