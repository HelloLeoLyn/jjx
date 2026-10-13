package com.jjx.product.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 产品作业规范发布条目类型（dev-20261011-008；dev-20261013-010 增补完整快照与文件保留） */
@Getter
@AllArgsConstructor
public enum WorkSpecResourceType {
    SPEC_JSON("SPEC_JSON", "规范正文快照"),
    BOM("BOM", "BOM（引用已批准版本）"),
    ROUTING("ROUTING", "工艺路线（引用已批准版本）"),
    DRAWING("DRAWING", "工程图/结构图（引用文件版本）"),
    /** 完整文档快照（带 schemaVersion，自包含；dev-20261013-010）。 */
    DOCSET_JSON("DOCSET_JSON", "完整文档快照"),
    /** 快照文件保留索引：一条引用一个附件，作为发布版本对文件字节的不可变保留锚点（dev-20261013-010）。 */
    DOCSET_FILE("DOCSET_FILE", "快照文件保留");

    private final String code;
    private final String label;
}
