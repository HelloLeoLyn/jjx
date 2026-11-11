package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 多层净需求只读试算结果（dev-20261111-006）。 */
@Data
@Schema(description = "多层净需求只读试算结果")
public class RequirementSummaryVO {

    private Boolean readOnly = Boolean.TRUE;
    private Boolean stockWritten = Boolean.FALSE;
    private Boolean confirmSupported = Boolean.FALSE;
    private String note;

    private Long productId;
    private String productCode;
    private String productName;
    private String productUnit;
    private Long bomId;
    private String bomCode;
    private BigDecimal requestedQuantity;
    private LocalDate demandDate;
    private Long warehouseId;
    private LocalDateTime generatedAt;

    private List<RequirementNodeVO> nodes = new ArrayList<>();
    private List<RequirementLineVO> lines = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> limitations = new ArrayList<>();
}
