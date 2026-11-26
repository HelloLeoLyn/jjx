package com.jjx.engineering.printing.search;

import lombok.Data;

/** 印刷资源统一搜索入参（dev-20261125-011）。 */
@Data
public class PrintSearchOption {
    /** 搜索词：空白分词，多词 AND */
    private String q;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    /** 场景：NEW_VERSION / RELEASE / PRODUCTION（受控场景需带上下文） */
    private String scene;
    private Long productId;
    private Long sampleOrderId;
    private Long specVersionId;
    private Long routingItemId;
    private Long executionId;
}
