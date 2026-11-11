package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 多层需求展开节点（只读，dev-20261111-006）。每个节点对应一次 BOM 展开，保留全部来源路径。 */
@Data
@Schema(description = "多层需求展开节点")
public class RequirementNodeVO {

    private Long nodeId;
    private List<Long> parentNodeIds = new ArrayList<>();
    private Integer level;
    private Long bomId;
    private String bomCode;
    private String bomName;
    private String outputType;
    private Long outputId;
    private String outputCode;
    private String outputName;
    private String outputUnit;

    /** ROOT：顶层产品需求；MAKE_GAP：下层按制造缺口展开。 */
    private String expandReason;

    /** 权威精确展开数量，如 "50/1"。 */
    private String expandQtyExact;
    /** 12 位小数近似值，仅展示。 */
    private BigDecimal expandQtyApprox;

    private List<String> sourcePaths = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
