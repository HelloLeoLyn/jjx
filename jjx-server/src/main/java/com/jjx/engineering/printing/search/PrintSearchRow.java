package com.jjx.engineering.printing.search;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 印刷资源搜索候选行（dev-20261125-011）。未进入实物层的 ID 为 null。 */
@Data
public class PrintSearchRow {
    private Long artworkId;
    private String artworkNo;
    private String name;
    private Long versionId;
    private Long filmId;
    private String filmNo;
    private String filmStatus;
    private Long plateId;
    private String plateNo;
    private String sourcesJson;
    private String layout;
    private String plateStatus;
    private Long frameId;
    private String frameNo;
    private String frameStatus;
    private Boolean qualified;
    /** 命中类型：ARTWORK / VERSION / FILM / PLATE / FRAME / PRODUCT / PURPOSE */
    private String matchedType;
    private String matchedCode;
    private String displayName;
    private String versionNo;
    private String versionStatus;
    private String productCode;
    private String productName;
    private String purpose;
    private Long stretchId;
    private java.time.LocalDateTime stretchTime;
    private String stretchMaterial;
    private Integer mesh;
    private BigDecimal tensionNCm;
    private String position;
    private Boolean availability;
    private List<String> reasonCodes;
    private Integer rank;
}
