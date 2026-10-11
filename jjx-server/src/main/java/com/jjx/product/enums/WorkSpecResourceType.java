package com.jjx.product.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 产品作业规范发布条目类型（dev-20261011-008） */
@Getter
@AllArgsConstructor
public enum WorkSpecResourceType {
    SPEC_JSON("SPEC_JSON", "规范正文快照"),
    BOM("BOM", "BOM（引用已批准版本）"),
    ROUTING("ROUTING", "工艺路线（引用已批准版本）"),
    DRAWING("DRAWING", "工程图/结构图（引用文件版本）");

    private final String code;
    private final String label;
}
